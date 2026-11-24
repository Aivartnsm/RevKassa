package ee.revkassa.infrastructure.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false) // error mapping only; security is tested in LoginFlowTest
public class RestExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void primaryKeyNotFoundTest() throws Exception {
        mockMvc.perform(get("/test/primary-key-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(Error.ENTRY_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.ENTRY_NOT_FOUND.getErrorCode()));
    }

    @Test
    void dataNotFoundTest() throws Exception {
        mockMvc.perform(get("/test/data-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(Error.DATA_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.DATA_NOT_FOUND.getErrorCode()));
    }

    @Test
    void forbiddenExceptionTest() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Error.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_REQUEST.getErrorCode()))
                .andExpect(jsonPath("$.remainingAttempts").doesNotExist())
                .andExpect(jsonPath("$.nextLockoutSeconds").doesNotExist());
    }

    @Test
    void forbiddenWithRemainingAttemptsTest() throws Exception {
        mockMvc.perform(get("/test/forbidden-with-remaining-attempts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(Error.WRONG_PIN.getErrorCode()))
                .andExpect(jsonPath("$.remainingAttempts").value(3));
    }

    @Test
    void tooManyAttemptsTest() throws Exception {
        mockMvc.perform(get("/test/too-many-attempts"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.message").value(Error.TOO_MANY_WRONG_PINS.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(113))
                .andExpect(jsonPath("$.nextLockoutSeconds").value(600))
                .andExpect(jsonPath("$.remainingAttempts").doesNotExist());
    }

    @Test
    void runtimeExceptionTest() throws Exception {
        mockMvc.perform(get("/test/runtime-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(Error.UNEXPECTED_ERROR.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.UNEXPECTED_ERROR.getErrorCode()));
    }

    @Test
    void optimisticLockingTest() throws Exception {
        mockMvc.perform(get("/test/optimistic-locking"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Error.DATA_HAS_CHANGED_RELOAD_REQUIRED.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.DATA_HAS_CHANGED_RELOAD_REQUIRED.getErrorCode()));
    }

    @Test
    void requestDtoErrorTest() throws Exception {
        mockMvc.perform(post("/test/dto-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": " " }
                                """
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Error.INVALID_DATA_IN_FIELDS.getMessage()))
                .andExpect(jsonPath("$.fieldErrors.username").value("Can not be left empty"))
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_DATA_IN_FIELDS.getErrorCode()));
    }

    @Test
    void unreadableJsonTest() throws Exception {
        mockMvc.perform(post("/test/dto-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": " 32412 SQL /&"
                                """
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Error.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_REQUEST.getErrorCode()));
    }

    @Test
    void malformedPathIdTest() throws Exception {
        mockMvc.perform(get("/test/path-id/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Error.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_REQUEST.getErrorCode()));
    }

    @Test
    void clockAndMoneyFormatTest() throws Exception {
        mockMvc.perform(get("/test/clock-and-money-format-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateTime").value("2026-10-28T18:42:00+02:00"))
                .andExpect(jsonPath("$.money").value(-1250));
    }

    @Test
    void unavailablePathTest() throws Exception {
        mockMvc.perform(get("/test/unavailable-path"))
                .andExpect(status().isNotFound());
    }

    @Test
    void methodNotAllowedExceptionTest() throws Exception {
        mockMvc.perform(post("/test/forbidden"))
                .andExpect(status().isMethodNotAllowed());
    }
}
