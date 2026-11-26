package ee.revkassa.controller.admin.member.dto;

import ee.revkassa.persistence.member.MemberRoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * A new member is always active (the invite goes out at once). No password: the member sets it with the invite link.
 * levelId may be left out: then the member gets the first active level.
 */
@Data
public class MemberCreateRequestDto implements Serializable {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 254;

    @NotBlank
    @Size(max = MAX_NAME_LENGTH)
    private String displayName;

    @NotBlank
    @Email
    @Size(max = MAX_EMAIL_LENGTH)
    private String email;

    @NotNull
    private MemberRoleEnum role;

    @NotNull
    private Boolean feeExempt;

    @Positive
    private Long levelId;
}
