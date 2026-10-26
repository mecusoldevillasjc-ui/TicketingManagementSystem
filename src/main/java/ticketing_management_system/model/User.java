package ticketing_management_system.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ticketing_management_system.util.TimeUtil;

@Entity
@Table(name = "users")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Role { CLIENT, AGENT, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt hash, never plain text

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    @Column(length = 30)
    private String phone; // optional

    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}

    public User(String fullName, String email, String password, Role role) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    // ---- helpers used by the pages ----
    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isAgent() { return role == Role.AGENT; }
    public boolean isClient() { return role == Role.CLIENT; }

    public String getHomePath() {
        if (isAdmin()) return "/admin/dashboard";
        if (isAgent()) return "/agent/dashboard";
        return "/client/dashboard";
    }

    public String getRoleLabel() {
        if (role == null) return "";
        return role.name().charAt(0) + role.name().substring(1).toLowerCase();
    }

    /** Friendly role name for the profile page. */
    public String getRoleDisplay() {
        if (isAdmin()) return "Administrator";
        if (isAgent()) return "Support agent";
        return "Client";
    }

    public String getFirstName() {
        return fullName == null || fullName.isBlank() ? "" : fullName.trim().split("\\s+")[0];
    }

    public String getInitials() {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] p = fullName.trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "")).toUpperCase();
    }

    public String getCreatedLabel() { return TimeUtil.full(createdAt); }

    // ---- getters and setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
