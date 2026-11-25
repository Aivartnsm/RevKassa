package ee.revkassa.controller.admin.member.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * The new member and their PIN. The PIN is shown to the admin this one time only: it is not stored anywhere readable.
 */
@Data
@AllArgsConstructor
public class MemberCreatedDto implements Serializable {
    private AdminMemberDto member;
    private String pin;
}
