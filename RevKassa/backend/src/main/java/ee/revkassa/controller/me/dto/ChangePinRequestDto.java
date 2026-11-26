package ee.revkassa.controller.me.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ChangePinRequestDto implements Serializable {

    @NotNull
    @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
    @ToString.Exclude
    private String currentPin;

    @NotNull
    @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
    @ToString.Exclude
    private String newPin;
}
