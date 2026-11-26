package ee.revkassa.controller.admin.member.dto;

import ee.revkassa.persistence.member.MemberRoleEnum;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * A member as the admin sees and edits it. No PIN or password hash: passwordSet only says whether the member has set
 * a password with their link. version must be sent back unchanged with an update.
 */
@Data
public class AdminMemberDto implements Serializable {
    private Long memberId;
    private String displayName;
    private String email;
    private MemberRoleEnum role;
    private Boolean active;
    private Boolean feeExempt;
    private Long levelId;
    private String levelName;
    private Long balanceCents;
    private Boolean passwordSet;
    private OffsetDateTime createdAt;
    private Long version;
}
