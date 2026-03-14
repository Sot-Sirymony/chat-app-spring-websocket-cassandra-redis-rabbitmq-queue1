package br.com.jorgeacetozi.ebookChat.integrationTests.filestorage.api;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.jorgeacetozi.ebookChat.fileapproval.domain.model.FileTransferRequest;
import br.com.jorgeacetozi.ebookChat.fileapproval.domain.service.FileTransferRequestService;
import br.com.jorgeacetozi.ebookChat.filestorage.domain.model.FileMetadata;
import br.com.jorgeacetozi.ebookChat.filestorage.domain.service.MinioFileService;
import br.com.jorgeacetozi.ebookChat.integrationTests.test.EbookChatTest;

/**
 * Integration tests for file download with full Spring Security (Principal injected).
 * Mocks MinIO/file services so no MinIO server is required. Covers the 5 scenarios from docs/File-And-Message-Test-Cases.md (D1–D5).
 */
@RunWith(SpringRunner.class)
@EbookChatTest
@WebAppConfiguration
public class FileControllerIntegrationTest {

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private FilterChainProxy springSecurityFilter;

	@MockBean
	private MinioFileService minioFileService;

	@MockBean
	private FileTransferRequestService fileTransferRequestService;

	private MockMvc mockMvc;

	@Before
	public void setup() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).addFilter(springSecurityFilter).build();
	}

	@Test
	public void download_noAuth_returns401() throws Exception {
		mockMvc.perform(get("/api/files/some-id/download"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	public void download_fileNotFound_returns404() throws Exception {
		when(minioFileService.getMetadata("missing-id")).thenReturn(null);

		mockMvc.perform(get("/api/files/missing-id/download").with(user("alice").roles("USER")))
				.andExpect(status().isNotFound());
	}

	@Test
	public void download_fileWithNoRequestId_warnOrAllow_returns200() throws Exception {
		FileMetadata meta = new FileMetadata();
		meta.setId("file-1");
		meta.setRequestId(null);
		meta.setFilename("warn.txt");
		meta.setContentType("text/plain");
		meta.setSizeBytes(10L);
		when(minioFileService.getMetadata("file-1")).thenReturn(meta);
		when(minioFileService.getContent("file-1")).thenReturn(new ByteArrayInputStream("content".getBytes()));

		mockMvc.perform(get("/api/files/file-1/download").with(user("bob").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("warn.txt")));
	}

	@Test
	public void download_filePendingAdmin_returns200() throws Exception {
		FileMetadata meta = new FileMetadata();
		meta.setId("file-2");
		meta.setRequestId("req-1");
		meta.setFilename("pending.txt");
		meta.setContentType("text/plain");
		meta.setSizeBytes(5L);
		FileTransferRequest req = new FileTransferRequest();
		req.setId("req-1");
		req.setStatus(FileTransferRequest.Status.PENDING.name());
		when(minioFileService.getMetadata("file-2")).thenReturn(meta);
		when(fileTransferRequestService.findById(anyString())).thenReturn(req);
		when(minioFileService.getContent("file-2")).thenReturn(new ByteArrayInputStream("data".getBytes()));

		mockMvc.perform(get("/api/files/file-2/download").with(user("admin").roles("ADMIN", "USER")))
				.andExpect(status().isOk());
	}

	@Test
	public void download_filePendingNonAdmin_returns403WithDenyReasonHeader() throws Exception {
		FileMetadata meta = new FileMetadata();
		meta.setId("file-3");
		meta.setRequestId("req-2");
		meta.setFilename("pending.txt");
		FileTransferRequest req = new FileTransferRequest();
		req.setId("req-2");
		req.setStatus(FileTransferRequest.Status.PENDING.name());
		when(minioFileService.getMetadata("file-3")).thenReturn(meta);
		when(fileTransferRequestService.findById(anyString())).thenReturn(req);

		mockMvc.perform(get("/api/files/file-3/download").with(user("bob").roles("USER")))
				.andExpect(status().isForbidden())
				.andExpect(header().string("X-File-Deny-Reason", "FILE_PENDING_APPROVAL"));
	}

	@Test
	public void download_fileApproved_returns200ForAnyUser() throws Exception {
		FileMetadata meta = new FileMetadata();
		meta.setId("file-4");
		meta.setRequestId("req-3");
		meta.setFilename("approved.txt");
		meta.setContentType("text/plain");
		meta.setSizeBytes(3L);
		FileTransferRequest req = new FileTransferRequest();
		req.setId("req-3");
		req.setStatus(FileTransferRequest.Status.APPROVED.name());
		when(minioFileService.getMetadata("file-4")).thenReturn(meta);
		when(fileTransferRequestService.findById(anyString())).thenReturn(req);
		when(minioFileService.getContent("file-4")).thenReturn(new ByteArrayInputStream("ok".getBytes()));

		// After approval, any authenticated user (e.g. dalen.phea) can download.
		mockMvc.perform(get("/api/files/file-4/download").with(user("dalen.phea").roles("USER")))
				.andExpect(status().isOk());
	}
}
