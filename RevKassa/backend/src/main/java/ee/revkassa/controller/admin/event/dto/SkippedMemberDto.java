package ee.revkassa.controller.admin.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A member who was not added, and why.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkippedMemberDto implements Serializable {
    private Long memberId;
    private SkipReasonEnum reason;

    public enum SkipReasonEnum {
        MEMBER_NOT_ACTIVE,
        ALREADY_PARTICIPANT
    }
}
