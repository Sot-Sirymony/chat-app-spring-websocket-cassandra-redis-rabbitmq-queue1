package br.com.jorgeacetozi.ebookChat.unitTests.filestorage.api;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;

import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpAction;
import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpRiskLevel;
import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpScanResult;
import br.com.jorgeacetozi.ebookChat.dlp.domain.service.DlpEngine;
import br.com.jorgeacetozi.ebookChat.fileapproval.domain.model.FileTransferRequest;
import br.com.jorgeacetozi.ebookChat.fileapproval.domain.service.FileTransferRequestService;
import br.com.jorgeacetozi.ebookChat.filestorage.api.FileController;
import br.com.jorgeacetozi.ebookChat.filestorage.domain.service.MinioFileService;

/**
 * Unit tests for file upload (DLP) and download no-auth. BR-3.1, BR-3.3, BR-4.1.
 * Authenticated download scenarios (file not found, WARN/PENDING/APPROVED) are in FileControllerIntegrationTest.
 */
@RunWith(MockitoJUnitRunner.class)
public class FileControllerTest {

	@Mock
	private MinioFileService minioFileService;
	@Mock
	private FileTransferRequestService fileTransferRequestService;
	@Mock
	private DlpEngine dlpEngine;

	private MockMvc mockMvc;

	/** Ensures request principal is set from Spring Security test user() so controller sees authenticated user. */
	private static final OncePerRequestFilter SECURITY_CONTEXT_FILTER = new OncePerRequestFilter() {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, javax.servlet.FilterChain chain) throws java.io.IOException, javax.servlet.ServletException {
			HttpServletRequest root = request;
			while (root instanceof HttpServletRequestWrapper) {
				root = (HttpServletRequest) ((HttpServletRequestWrapper) root).getRequest();
			}
			if (root instanceof MockHttpServletRequest && SecurityContextHolder.getContext().getAuthentication() != null) {
				((MockHttpServletRequest) root).setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
			}
			chain.doFilter(request, response);
		}
	};

	/** Apply after user() so request has principal for controller (standalone MockMvc does not inject it). */
	private static RequestPostProcessor setPrincipalFromSecurityContext() {
		return request -> {
			HttpServletRequest req = (HttpServletRequest) request;
			while (req instanceof HttpServletRequestWrapper) {
				req = (HttpServletRequest) ((HttpServletRequestWrapper) req).getRequest();
			}
			if (SecurityContextHolder.getContext().getAuthentication() != null && req instanceof MockHttpServletRequest) {
				((MockHttpServletRequest) req).setUserPrincipal(SecurityContextHolder.getContext().getAuthentication());
			}
			return request;
		};
	}

	@Before
	public void setUp() {
		FileController controller = new FileController(minioFileService, fileTransferRequestService, dlpEngine);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilter(SECURITY_CONTEXT_FILTER).build();
	}

	@After
	public void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	public void upload_emptyFile_returns200WithNullFileId() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

		mockMvc.perform(fileUpload("/api/files/upload").file(file).with(user("alice").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filename", org.hamcrest.Matchers.is("No file")))
				.andExpect(jsonPath("$.dlpWarning", org.hamcrest.Matchers.is(false)))
				.andExpect(jsonPath("$.dlpRequireApproval", org.hamcrest.Matchers.is(false)));
		verifyZeroInteractions(dlpEngine);
		verifyZeroInteractions(minioFileService);
	}

	@Test
	public void upload_dlpWarn_returns200WithDlpWarningTrue_noRequestCreated() throws Exception {
		byte[] content = "Confidential - internal only.".getBytes();
		MockMultipartFile file = new MockMultipartFile("file", "warn.txt", "text/plain", content);
		when(dlpEngine.scanFile(anyString(), any(byte[].class)))
				.thenReturn(new DlpScanResult(DlpRiskLevel.MEDIUM, Collections.<String>emptyList(), DlpAction.WARN, "confidential"));
		when(minioFileService.upload(anyString(), anyString(), anyString(), any(byte[].class))).thenReturn("file-id-warn");

		mockMvc.perform(fileUpload("/api/files/upload").file(file).with(user("alice").roles("USER")).with(setPrincipalFromSecurityContext()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileId", org.hamcrest.Matchers.is("file-id-warn")))
				.andExpect(jsonPath("$.dlpWarning", org.hamcrest.Matchers.is(true)))
				.andExpect(jsonPath("$.dlpRequireApproval", org.hamcrest.Matchers.is(false)));
		verify(minioFileService).upload(anyString(), eq("warn.txt"), eq("text/plain"), any(byte[].class));
		verifyZeroInteractions(fileTransferRequestService);
	}

	@Test
	public void upload_dlpRequireApproval_returns200AndLinksFileToRequest() throws Exception {
		byte[] content = "My SSN is 123-45-6789".getBytes();
		MockMultipartFile file = new MockMultipartFile("file", "ssn.txt", "text/plain", content);
		when(dlpEngine.scanFile(anyString(), any(byte[].class)))
				.thenReturn(new DlpScanResult(DlpRiskLevel.HIGH, Collections.<String>emptyList(), DlpAction.REQUIRE_APPROVAL, "SSN"));
		when(minioFileService.upload(anyString(), anyString(), anyString(), any(byte[].class))).thenReturn("file-id-ssn");
		FileTransferRequest req = new FileTransferRequest();
		req.setId("request-1");
		req.setStatus(FileTransferRequest.Status.PENDING.name());
		when(fileTransferRequestService.createPending(anyString(), eq(null), eq(null), anyString(), eq("file-id-ssn"))).thenReturn(req);

		mockMvc.perform(fileUpload("/api/files/upload").file(file).with(user("alice").roles("USER")).with(setPrincipalFromSecurityContext()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileId", org.hamcrest.Matchers.is("file-id-ssn")))
				.andExpect(jsonPath("$.dlpRequireApproval", org.hamcrest.Matchers.is(true)));
		verify(fileTransferRequestService).createPending(anyString(), eq(null), eq(null), anyString(), eq("file-id-ssn"));
		verify(minioFileService).linkToRequest(eq("file-id-ssn"), eq("request-1"));
	}

	@Test
	public void upload_dlpBlock_returns403() throws Exception {
		byte[] content = "forbidden content".getBytes();
		MockMultipartFile file = new MockMultipartFile("file", "block.txt", "text/plain", content);
		when(dlpEngine.scanFile(anyString(), any(byte[].class)))
				.thenReturn(new DlpScanResult(DlpRiskLevel.CRITICAL, Collections.<String>emptyList(), DlpAction.BLOCK, "blocked"));

		mockMvc.perform(fileUpload("/api/files/upload").file(file).with(user("alice").roles("USER")))
				.andExpect(status().isForbidden());
		verifyZeroInteractions(minioFileService);
	}

	@Test
	public void download_noAuth_returns401() throws Exception {
		mockMvc.perform(get("/api/files/some-id/download"))
				.andExpect(status().isUnauthorized());
	}
}
