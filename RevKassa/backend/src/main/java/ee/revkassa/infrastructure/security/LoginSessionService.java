package ee.revkassa.infrastructure.security;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRoleEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts and ends the server-side login session (JSESSIONID cookie). The PIN session on the shared tablet expires
 * after 1 minute of inactivity, the password session after 30 minutes. Admin rights come only with a password session.
 * Login gets a new session id and a new CSRF token; logout removes both cookies, so nothing usable is left in the
 * browser for the next person on a shared tablet. The trusted device cookie is never touched here.
 */
@Service
@RequiredArgsConstructor
public class LoginSessionService {

    public static final String SESSION_COOKIE_NAME = "JSESSIONID";
    public static final Duration PIN_SESSION_TIMEOUT = Duration.ofMinutes(1);
    public static final Duration PASSWORD_SESSION_TIMEOUT = Duration.ofMinutes(30);

    private static final String ROLE_MEMBER = "ROLE_MEMBER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public void start(Member member, LoginMethodEnum loginMethod, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = createNewSession(request);
        session.setMaxInactiveInterval((int) sessionTimeout(loginMethod).toSeconds());
        saveAuthentication(createAuthentication(member, loginMethod), request, response);
        renewCsrfToken(request, response);
    }

    public void end(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CookieClearingLogoutHandler(SESSION_COOKIE_NAME).logout(request, response, authentication);
        new CsrfLogoutHandler(csrfTokenRepository).logout(request, response, authentication);
    }

    // A fresh session id on every login, so a session id planted before login (session fixation) is worthless,
    // and a PIN session in the same browser is replaced, not upgraded.
    private static HttpSession createNewSession(HttpServletRequest request) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        return request.getSession(true);
    }

    private static Duration sessionTimeout(LoginMethodEnum loginMethod) {
        return loginMethod == LoginMethodEnum.PIN ? PIN_SESSION_TIMEOUT : PASSWORD_SESSION_TIMEOUT;
    }

    private static Authentication createAuthentication(Member member, LoginMethodEnum loginMethod) {
        MemberPrincipal principal = new MemberPrincipal(member.getId(), loginMethod);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities(member, loginMethod));
    }

    private static List<SimpleGrantedAuthority> authorities(Member member, LoginMethodEnum loginMethod) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_MEMBER));
        if (loginMethod == LoginMethodEnum.PASSWORD && member.getRole() == MemberRoleEnum.ADMIN) {
            authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
        }
        return authorities;
    }

    private void saveAuthentication(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    // A CSRF token known before login (e.g. planted by an attacker) must not keep working after it
    private void renewCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }
}
