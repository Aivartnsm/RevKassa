package ee.revkassa.controller.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class PasswordResetRequestDto implements Serializable {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;
}
