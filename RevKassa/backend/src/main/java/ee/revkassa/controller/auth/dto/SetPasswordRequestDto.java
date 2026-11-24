package ee.revkassa.controller.auth.dto;

import ee.revkassa.infrastructure.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class SetPasswordRequestDto implements Serializable {

    // Our tokens are 43 characters; the limit only stops huge input before hashing
    @NotBlank
    @Size(max = 100)
    @ToString.Exclude
    private String token;

    @NotNull
    @StrongPassword
    @ToString.Exclude
    private String password;
}
