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
 * The balance is not here: it changes only through the ledger (adjustments, payments, purchases).
 */
@Data
public class MemberUpdateRequestDto implements Serializable {

    @NotBlank
    @Size(max = MemberCreateRequestDto.MAX_NAME_LENGTH)
    private String displayName;

    @NotBlank
    @Email
    @Size(max = MemberCreateRequestDto.MAX_EMAIL_LENGTH)
    private String email;

    @NotNull
    private MemberRoleEnum role;

    @NotNull
    private Boolean active;

    @NotNull
    private Boolean feeExempt;

    // An inactive level only if the member already has it
    @NotNull
    @Positive
    private Long levelId;

    // The version the admin loaded; an older one means someone else saved in between (409)
    @NotNull
    private Long version;
}
