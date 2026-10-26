package ticketing_management_system.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ticketing_management_system.model.User;
import ticketing_management_system.repository.TicketRepository;
import ticketing_management_system.repository.UserRepository;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.service.TicketService;

/**
 * Login page and self-registration. The actual sign-in (POST /login) and sign-out (POST /logout)
 * are handled by Spring Security, configured in SecurityConfig.
 */
@Controller
public class AuthController {

    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final UserRepository users;
    private final TicketRepository tickets;
    private final TicketService svc;
    private final PasswordEncoder encoder;
    private final CurrentUser current;

    public AuthController(UserRepository users, TicketRepository tickets, TicketService svc,
                          PasswordEncoder encoder, CurrentUser current) {
        this.users = users;
        this.tickets = tickets;
        this.svc = svc;
        this.encoder = encoder;
        this.current = current;
    }

    @GetMapping("/login")
    public String login(Model model) {
        User u = current.get();
        if (u != null) return "redirect:" + u.getHomePath();
        model.addAttribute("counts", svc.statusCounts(tickets.findAll()));
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        User u = current.get();
        return u != null ? "redirect:" + u.getHomePath() : "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName, @RequestParam String email,
                           @RequestParam String password, @RequestParam String confirm,
                           RedirectAttributes ra) {
        String mail = email.trim().toLowerCase();
        String error = null;
        if (fullName.isBlank() || fullName.length() > 80) error = "Enter your full name.";
        else if (!mail.matches(EMAIL)) error = "Enter a valid email address.";
        else if (password.length() < 6) error = "Use a password with at least 6 characters.";
        else if (!password.equals(confirm)) error = "The two passwords don't match.";
        else if (users.existsByEmail(mail)) error = "That email is already registered.";

        if (error != null) {
            ra.addFlashAttribute("error", error);
            ra.addFlashAttribute("fullName", fullName);
            ra.addFlashAttribute("email", email);
            return "redirect:/register";
        }
        // public sign-up can only ever create CLIENT accounts
        users.save(new User(fullName.trim(), mail, encoder.encode(password), User.Role.CLIENT));
        ra.addFlashAttribute("success", "Account created. Sign in to submit your first ticket.");
        return "redirect:/login";
    }
}
