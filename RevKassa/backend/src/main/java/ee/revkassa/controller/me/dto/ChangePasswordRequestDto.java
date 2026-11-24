package ee.revkassa.controller.me.dto;

import ee.revkassa.infrastructure.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class ChangePasswordRequestDto implements Serializable {

    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String currentPassword;

    @NotNull
    @StrongPassword
    @ToString.Exclude
    private String newPassword;
}
