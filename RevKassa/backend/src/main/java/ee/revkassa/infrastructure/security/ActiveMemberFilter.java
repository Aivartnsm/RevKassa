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
 * An admin can deactivate a member while they are logged in. Every request with a session checks that the member
 * is still active; if not, the session ends and the request gets 403 ACCOUNT_NOT_ACTIVE.
 */
@Slf4j
@RequiredArgsConstructor
public class ActiveMemberFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = "/api/auth/";

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
        MemberPrincipal principal = currentPrincipal();
        if (principal != null && !memberRepository.isActiveMember(principal.memberId())) {
            log.info("Session ended for inactive member: memberId={}", principal.memberId());
            loginSessionService.end(request, response);
            securityErrorHandler.write(response, HttpStatus.FORBIDDEN, Error.ACCOUNT_NOT_ACTIVE);
            return;
        }
        chain.doFilter(request, response);
    }

    private static MemberPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MemberPrincipal principal) {
            return principal;
        }
        return null;
    }
}
