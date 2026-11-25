package ee.revkassa.infrastructure.security;

import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.persistence.member.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * An admin can deactivate a member or take away admin rights while they are logged in. Every request with a session
 * checks that the member is still active (if not: session ends, 403 ACCOUNT_NOT_ACTIVE) and that an admin session
 * still belongs to an active admin (if not: session ends, 403 ACCESS_DENIED; the member logs in again as a member).
 */
@Slf4j
@RequiredArgsConstructor
public class ActiveMemberFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = "/api/auth/";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final MemberRepository memberRepository;
    private final LoginSessionService loginSessionService;
    private final SecurityErrorHandler securityErrorHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Login and logout replace or end the session themselves
        return request.getRequestURI().startsWith(AUTH_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = principalOf(authentication);
        if (principal != null && !memberRepository.isActiveMember(principal.memberId())) {
            log.info("Session ended for inactive member: memberId={}", principal.memberId());
            endSession(request, response, Error.ACCOUNT_NOT_ACTIVE);
            return;
        }
        if (principal != null && hasAdminRole(authentication) && !memberRepository.isActiveAdmin(principal.memberId())) {
            log.info("Admin session ended, member is no longer an admin: memberId={}", principal.memberId());
            endSession(request, response, Error.ACCESS_DENIED);
            return;
        }
        chain.doFilter(request, response);
    }

    private static MemberPrincipal principalOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof MemberPrincipal principal) {
            return principal;
        }
        return null;
    }

    private static boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }

    private void endSession(HttpServletRequest request, HttpServletResponse response, Error error) throws IOException {
        loginSessionService.end(request, response);
        securityErrorHandler.write(response, HttpStatus.FORBIDDEN, error);
    }
}
