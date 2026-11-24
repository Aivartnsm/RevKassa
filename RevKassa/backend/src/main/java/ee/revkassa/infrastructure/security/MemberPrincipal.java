package ee.revkassa.infrastructure.security;

import java.io.Serializable;

/**
 * What the session remembers about the logged-in member. Only the id and how they logged in: everything else
 * (name, balance, active, role) is read fresh from the database, so changes made by an admin apply at once.
 */
public record MemberPrincipal(Long memberId, LoginMethodEnum loginMethod) implements Serializable {
}
