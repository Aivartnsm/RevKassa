package ee.revkassa.infrastructure.error;

import ee.revkassa.infrastructure.ai.AssistantUnavailableException;
import ee.revkassa.infrastructure.exception.BadRequestException;
import ee.revkassa.infrastructure.exception.ConflictException;
import ee.revkassa.infrastructure.exception.DataNotFoundException;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.infrastructure.exception.TooManyAttemptsException;
import ee.revkassa.infrastructure.mail.MailSendFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PrimaryKeyNotFoundException.class)
    public ApiError handlePrimaryKeyNotFoundException(PrimaryKeyNotFoundException exception) {
        return createApiError(exception.getError());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(DataNotFoundException.class)
    public ApiError handleDataNotFoundException(DataNotFoundException exception) {
        return createApiError(exception.getError());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiError handleUnavailablePathException() {
        return createApiError(Error.DATA_NOT_FOUND);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(ForbiddenException.class)
    public ApiError handleForbiddenException(ForbiddenException exception) {
        ApiError apiError = createApiError(exception.getError());
        apiError.setRemainingAttempts(exception.getRemainingAttempts());
        return apiError;
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttemptsException(TooManyAttemptsException exception) {
        ApiError apiError = createApiError(exception.getError());
        if (exception.getNextLockoutSeconds() > 0) {
            apiError.setNextLockoutSeconds(Math.toIntExact(exception.getNextLockoutSeconds()));
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(apiError);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public ApiError handleBadRequestException(BadRequestException exception) {
        return createApiError(exception.getError());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiError handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        Map<String, String> errorFieldsWithMessages = new HashMap<>();
        for (FieldError fieldError : fieldErrors) {
            errorFieldsWithMessages.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ApiError apiError = createApiError(Error.INVALID_DATA_IN_FIELDS);
        apiError.setFieldErrors(errorFieldsWithMessages);
        return apiError;
    }

    // A constraint on a @RequestParam, e.g. ?page=-1
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ApiError handleHandlerMethodValidationException() {
        return createApiError(Error.INVALID_DATA_IN_FIELDS);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiError handleHttpMessageNotReadableException() {
        return createApiError(Error.INVALID_REQUEST);
    }

    // The mail server refused or did not answer; nothing was saved, the admin can try again
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(MailSendFailedException.class)
    public ApiError handleMailSendFailedException() {
        return createApiError(Error.EMAIL_NOT_SENT);
    }

    // The assistant is off or Gemini failed (quota, funds, timeout); nothing was saved, the admin can try again
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(AssistantUnavailableException.class)
    public ApiError handleAssistantUnavailableException(AssistantUnavailableException exception) {
        return createApiError(exception.getError());
    }

    // e.g. /api/admin/devices/abc/revoke: a malformed id is the client's mistake, not a server error
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiError handleMethodArgumentTypeMismatchException() {
        return createApiError(Error.INVALID_REQUEST);
    }

    // e.g. /api/admin/invoices/preview without memberId
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiError handleMissingServletRequestParameterException() {
        return createApiError(Error.INVALID_REQUEST);
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ConflictException.class)
    public ApiError handleConflictException(ConflictException exception) {
        return createApiError(exception.getError());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ApiError handleObjectOptimisticLockingFailureException() {
        return createApiError(Error.DATA_HAS_CHANGED_RELOAD_REQUIRED);
    }

    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiError handleMethodNotAllowedException() {
        return createApiError(Error.INVALID_REQUEST);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public ApiError handleUnexpectedException(Exception exception) {
        log.error("INTERNAL_SERVER_ERROR log: ", exception);
        return createApiError(Error.UNEXPECTED_ERROR);
    }

    private ApiError createApiError(Error error) {
        ApiError apiError = new ApiError();
        apiError.setMessage(error.getMessage());
        apiError.setErrorCode(error.getErrorCode());
        return apiError;
    }
}
