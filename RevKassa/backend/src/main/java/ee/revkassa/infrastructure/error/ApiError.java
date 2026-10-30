package ee.revkassa.infrastructure.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError implements Serializable {
    private String message;
    private Integer errorCode;
    private Map<String, String> fieldErrors;
    private Integer remainingAttempts;
    private Integer nextLockoutSeconds;

}
