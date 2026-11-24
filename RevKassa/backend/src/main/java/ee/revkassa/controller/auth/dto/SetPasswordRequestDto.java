package ee.revkassa.controller.auth.dto;

import ee.revkassa.infrastructure.validation.MaxUtf8Bytes;
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
    @Size(min = 10, message = "must be at least 10 characters")
    @MaxUtf8Bytes(72)
    @ToString.Exclude
    private String password;
}
