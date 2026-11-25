package ee.revkassa.controller.admin.member.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * A member's new PIN, shown to the admin this one time only.
 */
@Data
@AllArgsConstructor
public class MemberPinDto implements Serializable {
    private String pin;
}
