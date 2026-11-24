package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.error.Error;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Spring Security rejects requests before they reach a controller, so RestExceptionHandler never sees them.
 * This gives those responses the same ApiError body as every other error.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, Error.SESSION_MISSING);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, Error.ACCESS_DENIED);
    }

    public void write(HttpServletResponse response, HttpStatus status, Error error) throws IOException {
        ApiError apiError = new ApiError();
        apiError.setMessage(error.getMessage());
        apiError.setErrorCode(error.getErrorCode());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), apiError);
    }
}
