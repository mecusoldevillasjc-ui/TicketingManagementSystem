package ticketing_management_system.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import ticketing_management_system.model.User;
import ticketing_management_system.repository.UserRepository;

/** Gives controllers the signed-in User entity (fresh from the database). */
@Component
public class CurrentUser {
    public static final String ATTR = "tms.currentUser";

    private final UserRepository users;

    public CurrentUser(UserRepository users) { this.users = users; }

    /** The signed-in user, or null for visitors. */
    public User get() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null && ra.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST) instanceof User u) return u;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AppUserDetails p) return users.findById(p.getId()).orElse(null);
        return null;
    }

    public User require() {
        User u = get();
        if (u == null) throw new AccessDeniedException("Please sign in.");
        return u;
    }
}
