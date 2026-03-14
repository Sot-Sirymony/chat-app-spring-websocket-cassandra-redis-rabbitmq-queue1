package br.com.jorgeacetozi.ebookChat.filestorage.api;

import java.io.InputStream;
import java.security.Principal;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpAction;
import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpScanResult;
import br.com.jorgeacetozi.ebookChat.dlp.domain.service.DlpEngine;
import br.com.jorgeacetozi.ebookChat.fileapproval.domain.model.FileTransferRequest;
import br.com.jorgeacetozi.ebookChat.fileapproval.domain.service.FileTransferRequestService;
import br.com.jorgeacetozi.ebookChat.filestorage.domain.model.FileMetadata;
import br.com.jorgeacetozi.ebookChat.filestorage.domain.service.MinioFileService;

/**
 * File upload (MinIO) and secure download (BR-3.3). TM.3, TM.5, TM.6.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

	private final MinioFileService minioFileService;
	private final FileTransferRequestService fileTransferRequestService;
	private final DlpEngine dlpEngine;

	@Autowired
	public FileController(MinioFileService minioFileService,
			FileTransferRequestService fileTransferRequestService,
			DlpEngine dlpEngine) {
		this.minioFileService = minioFileService;
		this.fileTransferRequestService = fileTransferRequestService;
		this.dlpEngine = dlpEngine;
	}

	/**
	 * Upload a file. DLP scan: BLOCK -> 403; WARN/REQUIRE_APPROVAL -> 200 with flags. Returns file id for use in messages/approval.
	 */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Secured({ "ROLE_USER", "ROLE_ADMIN" })
	public UploadResponse upload(@RequestParam("file") MultipartFile file, Principal principal) throws Exception {
		if (file == null || file.isEmpty()) {
			return new UploadResponse(null, "No file", 0L, false, false);
		}
		String username = principal != null ? principal.getName() : "anonymous";
		String contentType = file.getContentType();
		byte[] content = file.getBytes();

		DlpScanResult dlp = dlpEngine.scanFile(contentType, content);
		if (dlp.getAction() == DlpAction.BLOCK) {
			throw new DlpBlockedException("Upload blocked by policy: " + dlp.getMessage());
		}

		String fileId = minioFileService.upload(username, file.getOriginalFilename(), contentType, content);
		boolean dlpWarning = dlp.getAction() == DlpAction.WARN;
		boolean dlpRequireApproval = dlp.getAction() == DlpAction.REQUIRE_APPROVAL;
		long sizeBytes = file.getSize();

		// Only REQUIRE_APPROVAL (e.g. SSN) creates an approval request. WARN (e.g. confidential/secret) does not.
		if (dlpRequireApproval) {
			String contentLabel = "File upload: " + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment");
			FileTransferRequest created = fileTransferRequestService.createPending(username, null, null, contentLabel, fileId);
			minioFileService.linkToRequest(fileId, created.getId());
		}

		return new UploadResponse(fileId, file.getOriginalFilename(), sizeBytes, dlpWarning, dlpRequireApproval);
	}

	/**
	 * Download file by id. Allowed if: (1) file has no request (WARN/ALLOW); (2) file is APPROVED (any authenticated user); (3) PENDING and user is admin; (4) REJECTED and user is admin/requester/recipient.
	 */
	@GetMapping("/{id}/download")
	@Secured({ "ROLE_USER", "ROLE_ADMIN" })
	public void download(@PathVariable String id, Principal principal, HttpServletResponse response) throws Exception {
		// CORS is handled by FileDownloadCorsFilter (reflects Origin when credentials are used)

		String username = principal != null ? principal.getName() : null;
		if (username == null) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
			return;
		}

		FileMetadata meta = minioFileService.getMetadata(id);
		if (meta == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
			return;
		}

		boolean allowed = false;
		String denyReason = null;
		String denyMessage = "Not allowed to download this file";
		if (meta.getRequestId() != null) {
			FileTransferRequest req = fileTransferRequestService.findById(meta.getRequestId());
			if (req == null) {
				// Orphaned link (request deleted or missing): allow download.
				allowed = true;
			} else {
				String status = normalizeStatus(req.getStatus());
				String requester = req.getRequester();
				String recipient = req.getRecipient();
				boolean isRequesterOrRecipient = (requester != null && username.equals(requester))
						|| (recipient != null && !recipient.isEmpty() && username.equals(recipient));
				// PENDING: only admin can download.
				if (FileTransferRequest.Status.PENDING.name().equals(status)) {
					if (hasRoleAdmin()) {
						allowed = true;
					} else {
						denyReason = "FILE_PENDING_APPROVAL";
						denyMessage = "File not yet approved";
					}
				} else if (FileTransferRequest.Status.REJECTED.name().equals(status)) {
					allowed = hasRoleAdmin() || isRequesterOrRecipient;
					if (!allowed) {
						denyReason = "FILE_NOT_AUTHORIZED";
						denyMessage = "Not allowed to download this file";
					}
				} else {
					// APPROVED or any other decided/legacy status: allow all authenticated users (all 3 can download).
					allowed = true;
				}
			}
		} else {
			// File not linked to any request (WARN or ALLOW upload): allow any authenticated user.
			allowed = true;
		}
		if (!allowed) {
			if (denyReason != null) {
				response.setHeader("X-File-Deny-Reason", denyReason);
			}
			response.sendError(HttpServletResponse.SC_FORBIDDEN, denyMessage);
			return;
		}

		try (InputStream in = minioFileService.getContent(id)) {
			response.setContentType(meta.getContentType() != null ? meta.getContentType() : "application/octet-stream");
			String filename = meta.getFilename() != null ? meta.getFilename() : "download";
			response.setHeader("Content-Disposition", "attachment; filename=\"" + sanitizeFilename(filename) + "\"");
			if (meta.getSizeBytes() != null) {
				response.setContentLengthLong(meta.getSizeBytes());
			}
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) != -1) {
				response.getOutputStream().write(buf, 0, n);
			}
			response.getOutputStream().flush();
		}
	}

	private static boolean hasRoleAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth.getAuthorities() == null) return false;
		return auth.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch("ROLE_ADMIN"::equals);
	}

	/** Normalize status for comparison (handles DB casing, e.g. "pending" -> "PENDING"). */
	private static String normalizeStatus(String status) {
		return status != null ? status.trim().toUpperCase() : null;
	}

	private static String sanitizeFilename(String name) {
		if (name == null) return "download";
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	@SuppressWarnings("unused")
	public static class UploadResponse {
		private String fileId;
		private String filename;
		private long sizeBytes;
		private boolean dlpWarning;
		private boolean dlpRequireApproval;

		public UploadResponse(String fileId, String filename, long sizeBytes, boolean dlpWarning, boolean dlpRequireApproval) {
			this.fileId = fileId;
			this.filename = filename;
			this.sizeBytes = sizeBytes;
			this.dlpWarning = dlpWarning;
			this.dlpRequireApproval = dlpRequireApproval;
		}

		public String getFileId() { return fileId; }
		public String getFilename() { return filename; }
		public long getSizeBytes() { return sizeBytes; }
		public boolean isDlpWarning() { return dlpWarning; }
		public boolean isDlpRequireApproval() { return dlpRequireApproval; }
	}

	@ResponseStatus(HttpStatus.FORBIDDEN)
	public static class DlpBlockedException extends RuntimeException {
		public DlpBlockedException(String message) {
			super(message);
		}
	}
}
