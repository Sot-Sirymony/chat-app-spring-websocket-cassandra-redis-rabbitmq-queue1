package br.com.jorgeacetozi.ebookChat.chatroom.api;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import br.com.jorgeacetozi.ebookChat.authentication.domain.model.User;
import br.com.jorgeacetozi.ebookChat.authentication.domain.repository.UserRepository;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.model.ChatRoom;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.model.ChatRoomUser;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.model.InstantMessage;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.model.RoomClassification;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.policy.AbacContext;
import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpAction;
import br.com.jorgeacetozi.ebookChat.dlp.domain.model.DlpScanResult;
import br.com.jorgeacetozi.ebookChat.dlp.domain.service.DlpEngine;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.policy.AbacPolicyService;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.policy.PolicyDecision;
import br.com.jorgeacetozi.ebookChat.audit.domain.service.AuditService;
import br.com.jorgeacetozi.ebookChat.fileapproval.domain.service.FileTransferRequestService;
import br.com.jorgeacetozi.ebookChat.filestorage.domain.service.MinioFileService;
import br.com.jorgeacetozi.ebookChat.metrics.ChatMetricsService;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.policy.UserRiskScoreService;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.service.ChatRoomService;
import br.com.jorgeacetozi.ebookChat.chatroom.domain.service.InstantMessageService;

@Controller
public class ChatRoomController {

	@Autowired
	private ChatRoomService chatRoomService;

	@Autowired
	private InstantMessageService instantMessageService;

	@Autowired
	private AbacPolicyService abacPolicyService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private AuditService auditService;

	@Autowired
	private DlpEngine dlpEngine;

	@Autowired
	private FileTransferRequestService fileTransferRequestService;

	@Autowired
	private MinioFileService minioFileService;

	@Autowired
	private ChatMetricsService chatMetricsService;

	@Autowired
	private UserRiskScoreService userRiskScoreService;

	@Secured("ROLE_ADMIN")
	@RequestMapping(path = "/chatroom", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(code = HttpStatus.CREATED)
	public ChatRoom createChatRoom(@RequestBody ChatRoom chatRoom) {
		return chatRoomService.save(chatRoom);
	}

	@RequestMapping("/approvals")
	public String approvals() {
		return "approvals";
	}

	@RequestMapping("/analytics")
	public String analytics() {
		return "analytics";
	}

	@RequestMapping("/chatroom/{chatRoomId}")
	public ModelAndView join(@PathVariable String chatRoomId, Principal principal) {
		ModelAndView modelAndView = new ModelAndView("chatroom");
		modelAndView.addObject("chatRoom", chatRoomService.findById(chatRoomId));
		return modelAndView;
	}

	@SubscribeMapping("/connected.users")
	public List<ChatRoomUser> listChatRoomConnectedUsersOnSubscribe(SimpMessageHeaderAccessor headerAccessor) {
		String chatRoomId = headerAccessor.getSessionAttributes().get("chatRoomId").toString();
		return chatRoomService.findById(chatRoomId).getConnectedUsers();
	}

	@SubscribeMapping("/old.messages")
	public List<InstantMessage> listOldMessagesFromUserOnSubscribe(Principal principal,
			SimpMessageHeaderAccessor headerAccessor) {
		String chatRoomId = headerAccessor.getSessionAttributes().get("chatRoomId").toString();
		return instantMessageService.findAllInstantMessagesFor(principal.getName(), chatRoomId);
	}

	@MessageMapping("/send.message")
	public void sendMessage(@Payload InstantMessage instantMessage, Principal principal,
			SimpMessageHeaderAccessor headerAccessor) {
		String chatRoomId = headerAccessor.getSessionAttributes().get("chatRoomId").toString();
		ChatRoom room = chatRoomService.findById(chatRoomId);
		AbacContext ctx = buildAbacContext(principal, room, instantMessage.isPublic() ? "public" : "private", headerAccessor);
		PolicyDecision decision = abacPolicyService.evaluateSendMessage(ctx);
		if (!decision.isAllowed()) {
			chatMetricsService.recordPolicyDenied();
			auditService.logEvent(principal.getName(), "SEND_MESSAGE", chatRoomId, "deny", decision.getRuleId(), decision.getReason());
			messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/policy-denial",
					java.util.Collections.singletonMap("reason", decision.getReason()));
			return;
		}
		auditService.logEvent(principal.getName(), "SEND_MESSAGE", chatRoomId, "allow", decision.getRuleId(), null);
		instantMessage.setFromUser(principal.getName());
		instantMessage.setChatRoomId(chatRoomId);

		DlpScanResult dlpResult = dlpEngine.scan(instantMessage.getText());
		if (dlpResult.getAction() == DlpAction.BLOCK) {
			chatMetricsService.recordDlpBlocked();
			for (String ruleId : dlpResult.getMatchedRuleIds()) {
				chatMetricsService.recordDlpRuleHit(ruleId);
			}
			auditService.logEvent(principal.getName(), "SEND_MESSAGE", chatRoomId, "deny", "dlp-BLOCK", dlpResult.getMessage());
			messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/policy-denial",
					java.util.Collections.singletonMap("reason", "Message blocked by policy: " + dlpResult.getMessage()));
			return;
		}
		if (dlpResult.getAction() == DlpAction.REQUIRE_APPROVAL) {
			instantMessage.setFromUser(principal.getName());
			instantMessage.setChatRoomId(chatRoomId);
			String fileRef = instantMessage.getFileRef();
			br.com.jorgeacetozi.ebookChat.fileapproval.domain.model.FileTransferRequest created = fileTransferRequestService.createPending(
					principal.getName(), instantMessage.getToUser(), chatRoomId, instantMessage.getText(), fileRef);
			// Only gate the file if the attachment required approval at upload (WARN-only attachments stay downloadable)
			Boolean attachmentRequiresApproval = instantMessage.getAttachmentRequiresApproval();
			if (fileRef != null && !fileRef.isEmpty() && Boolean.TRUE.equals(attachmentRequiresApproval)) {
				minioFileService.linkToRequest(fileRef, created.getId());
			}
			auditService.logEvent(principal.getName(), "SEND_MESSAGE", chatRoomId, "pending_approval", "dlp-REQUIRE_APPROVAL", "Request " + created.getId());
			java.util.Map<String, String> payload = new java.util.HashMap<>();
			payload.put("reason", "Message requires approval. Request ID: " + created.getId());
			payload.put("requestId", created.getId());
			messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/policy-denial", payload);
			return;
		}
		if (dlpResult.getAction() == DlpAction.WARN) {
			instantMessage.setDlpWarning(dlpResult.getMessage());
			auditService.logEvent(principal.getName(), "SEND_MESSAGE", chatRoomId, "allow", "dlp-WARN", dlpResult.getMessage());
		}
		String fileRef = instantMessage.getFileRef();
		if (fileRef != null && !fileRef.isEmpty()) {
			String text = instantMessage.getText() != null ? instantMessage.getText() : "";
			instantMessage.setText(text + " [Download attachment](/api/files/" + fileRef + "/download)");
		}
		// T3.2.2: Wrong recipient check — warn if recipient is not in this room
		String toUser = instantMessage.getToUser();
		if (toUser != null && !toUser.isEmpty()) {
			boolean recipientInRoom = room.getConnectedUsers().stream()
					.anyMatch(u -> toUser.equals(u.getUsername()));
			if (!recipientInRoom) {
				java.util.Map<String, String> warn = new java.util.HashMap<>();
				warn.put("reason", "Recipient \"" + toUser + "\" is not in this room. They may not see the message.");
				messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/recipient-warning", warn);
			}
		}
		if (instantMessage.isPublic()) {
			chatRoomService.sendPublicMessage(instantMessage);
		} else {
			chatRoomService.sendPrivateMessage(instantMessage);
		}
	}

	private AbacContext buildAbacContext(Principal principal, ChatRoom room, String messageType,
			SimpMessageHeaderAccessor headerAccessor) {
		AbacContext ctx = new AbacContext();
		ctx.setUsername(principal.getName());
		ctx.setRoomId(room.getId());
		ctx.setRoomLevel(room.getClassification() != null ? room.getClassification() : RoomClassification.PUBLIC);
		ctx.setMessageType(messageType);
		ctx.setTimestamp(System.currentTimeMillis());
		if (headerAccessor != null && headerAccessor.getSessionAttributes() != null) {
			Object dt = headerAccessor.getSessionAttributes().get("deviceType");
			if (dt != null) {
				ctx.setDeviceType(dt.toString());
			}
		}
		ctx.setRiskScore(userRiskScoreService.getScore(principal.getName()));
		User user = userRepository.findOne(principal.getName());
		if (user != null) {
			ctx.setDepartment(user.getDepartment());
			Set<String> roles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet());
			ctx.setRoles(roles);
		}
		return ctx;
	}
}
