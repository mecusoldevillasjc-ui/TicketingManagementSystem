package ticketing_management_system.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import ticketing_management_system.model.User;
import ticketing_management_system.repository.UserRepository;

/**
 * Runs on every request of a signed-in user and re-reads the account from the database.
 * - account deactivated or deleted: the session is ended immediately
 * - role changed by an admin: the new role applies on the very next click
 * It is created with "new" inside SecurityConfig (not a @Component) so it runs once, inside the security chain.
 */
public class UserRefreshFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public UserRefreshFilter(UserRepository users) { this.users = users; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/img/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails p) {
            User fresh = users.findById(p.getId()).orElse(null);
            if (fresh == null || !fresh.isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                res.sendRedirect(req.getContextPath() + "/login?disabled");
                return;
            }
            if (fresh.getRole() != p.getRole()) {
                AppUserDetails updated = new AppUserDetails(fresh);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(updated, null, updated.getAuthorities()));
            }
            req.setAttribute(CurrentUser.ATTR, fresh);
        }
        chain.doFilter(req, res);
    }
}
