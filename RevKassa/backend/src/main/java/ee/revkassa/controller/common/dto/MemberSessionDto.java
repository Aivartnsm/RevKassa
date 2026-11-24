package ee.revkassa.controller.common.dto;

import ee.revkassa.infrastructure.security.LoginMethodEnum;
import ee.revkassa.persistence.member.MemberRoleEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * Response of POST /api/auth/pin, POST /api/auth/login and GET /api/me.
 */
@Data
public class MemberSessionDto implements Serializable {
    private Long memberId;
    private String displayName;
    private MemberRoleEnum role;
    private Long balanceCents;
    private LoginMethodEnum loginMethod;
}
