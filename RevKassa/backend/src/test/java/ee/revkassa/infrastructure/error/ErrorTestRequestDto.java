package ee.revkassa.infrastructure.error;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ErrorTestRequestDto {

    @NotBlank(message = "Can not be left empty")
    private String username;
}
