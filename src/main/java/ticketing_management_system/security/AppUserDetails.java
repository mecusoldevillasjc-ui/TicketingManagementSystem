package ticketing_management_system.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import ticketing_management_system.model.User;

/** What Spring Security keeps in the session about the signed-in person (a small snapshot, not the entity). */
public class AppUserDetails implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String password;
    private final User.Role role;
    private final boolean active;

    public AppUserDetails(User u) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.password = u.getPassword();
        this.role = u.getRole();
        this.active = u.isActive();
    }

    public Long getId() { return id; }
    public User.Role getRole() { return role; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority(role.name())); }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; } // a deactivated account fails login with DisabledException
}
