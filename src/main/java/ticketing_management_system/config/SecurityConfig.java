package ticketing_management_system.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ticketing_management_system.repository.UserRepository;
import ticketing_management_system.security.UserRefreshFilter;

/**
 * Who may open which page.
 * Public: homepage, kiosk, ticket tracking, login, register, static files.
 * Everything else needs a login, and /admin, /agent, /client are limited to their own role.
 * CSRF protection is ON: every form uses th:action, which adds the hidden token automatically.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository users) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/kiosk", "/kiosk/**", "/track", "/login", "/register", "/error",
                                 "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasAuthority("ADMIN")
                .requestMatchers("/agent/**").hasAuthority("AGENT")
                .requestMatchers("/client/**").hasAuthority("CLIENT")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(this::routeByRole)
                .failureHandler(this::loginFailed)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")           // POST only (with CSRF token)
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll())
            .addFilterBefore(new UserRefreshFilter(users), AuthorizationFilter.class);
        return http.build();
    }

    /** After login, send each role to its own dashboard. */
    private void routeByRole(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws IOException {
        String target = "/client/dashboard";
        for (var a : auth.getAuthorities()) {
            if (a.getAuthority().equals("ADMIN")) { target = "/admin/dashboard"; break; }
            if (a.getAuthority().equals("AGENT")) { target = "/agent/dashboard"; break; }
        }
        res.sendRedirect(req.getContextPath() + target);
    }

    private void loginFailed(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        res.sendRedirect(req.getContextPath() + (ex instanceof DisabledException ? "/login?disabled" : "/login?error"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
