package ee.revkassa.infrastructure.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ErrorTestController.class)
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
                .andExpect(jsonPath("$.errorCode").value(Error.INVALID_REQUEST.getErrorCode()));
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
