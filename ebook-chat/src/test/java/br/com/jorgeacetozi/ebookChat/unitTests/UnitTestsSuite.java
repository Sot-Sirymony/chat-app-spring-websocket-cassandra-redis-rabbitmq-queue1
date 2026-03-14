package br.com.jorgeacetozi.ebookChat.unitTests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import br.com.jorgeacetozi.ebookChat.unitTests.chatroom.domain.service.RedisChatRoomServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.chatroom.domain.model.InstantMessageBuilderTest;
import br.com.jorgeacetozi.ebookChat.unitTests.utils.DestinationsTest;
import br.com.jorgeacetozi.ebookChat.unitTests.utils.SystemMessagesTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase1.JwtTokenServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase1.AbacPolicyServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase1.AuditServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase1.RoomClassificationAndChatRoomTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase2.DlpEngineAndRuleBasedProviderTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase2.DlpScanResultAndRiskLevelTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase3.FileTransferRequestServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase4.ChatMetricsServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase4.UserRiskScoreServiceTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase5.PresidioPropertiesTest;
import br.com.jorgeacetozi.ebookChat.unitTests.phase5.DlpEnginePresidioMergeAndFallbackTest;
import br.com.jorgeacetozi.ebookChat.unitTests.filestorage.api.FileControllerTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  FileControllerTest.class,
  InstantMessageBuilderTest.class,
  DestinationsTest.class,
  SystemMessagesTest.class,
  RedisChatRoomServiceTest.class,
  JwtTokenServiceTest.class,
  AbacPolicyServiceTest.class,
  AuditServiceTest.class,
  RoomClassificationAndChatRoomTest.class,
  DlpEngineAndRuleBasedProviderTest.class,
  DlpScanResultAndRiskLevelTest.class,
  FileTransferRequestServiceTest.class,
  ChatMetricsServiceTest.class,
  UserRiskScoreServiceTest.class,
  PresidioPropertiesTest.class,
  DlpEnginePresidioMergeAndFallbackTest.class
})
public class UnitTestsSuite {

}
