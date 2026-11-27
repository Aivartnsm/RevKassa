package ee.revkassa.controller.admin.assistant;

import ee.revkassa.persistence.member.MemberRoleEnum;
import ee.revkassa.testdata.TestData;
import ee.revkassa.testdata.TestSessions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B31: the assistant status through the real security chain. Tests run without a key (test application.properties),
 * so the assistant is off; which assistant a key selects is in AiConfigTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestData.class, TestSessions.class})
class AdminAssistantFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestSessions testSessions;

    @Test
    void status_withoutKey_isDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/assistant/status").session(testSessions.adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void status_byMember_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/assistant/status").session(testSessions.memberSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_byAdminWithPin_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/assistant/status").session(testSessions.pinSession(MemberRoleEnum.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/assistant/status")).andExpect(status().isUnauthorized());
    }
}
