package ee.revkassa.controller.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
public class PinLoginRequestDto implements Serializable {

    @NotNull
    @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
    private String pin;
}
