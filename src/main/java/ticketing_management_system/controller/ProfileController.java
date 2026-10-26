package ticketing_management_system.controller;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ticketing_management_system.model.User;
import ticketing_management_system.repository.UserRepository;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.util.Web;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser current;

    public ProfileController(UserRepository users, PasswordEncoder encoder, CurrentUser current) {
        this.users = users;
        this.encoder = encoder;
        this.current = current;
    }

    @GetMapping
    public String profile() { return "profile"; }

    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    /** Update name, email and phone. The role can't be changed here. */
    @PostMapping
    public String update(@RequestParam String fullName, @RequestParam String email,
                         @RequestParam(defaultValue = "") String phone, RedirectAttributes ra) {
        User me = this.current.require();
        String mail = email.trim().toLowerCase();
        String error = null;
        if (fullName.isBlank() || fullName.length() > 80) error = "Enter your full name.";
        else if (!mail.matches(EMAIL)) error = "Enter a valid email address.";
        else if (!mail.equals(me.getEmail()) && users.existsByEmail(mail)) error = "That email is already used by another account.";
        else if (!phone.isBlank() && !phone.matches("[0-9+()\\-\\s]{5,30}")) error = "Enter a valid phone number.";

        if (error == null) {
            me.setFullName(fullName.trim());
            me.setEmail(mail);
            me.setPhone(phone.isBlank() ? null : phone.trim());
            users.save(me);
        }
        Web.result(ra, error, "Profile updated.");
        return "redirect:/profile";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String current, @RequestParam String password,
                                 @RequestParam String confirm, RedirectAttributes ra) {
        User me = this.current.require();
        String error = null;
        if (!encoder.matches(current, me.getPassword())) error = "Your current password is not correct.";
        else if (password.length() < 6) error = "Use a new password with at least 6 characters.";
        else if (!password.equals(confirm)) error = "The two new passwords don't match.";

        if (error == null) {
            me.setPassword(encoder.encode(password));
            users.save(me);
        }
        Web.result(ra, error, "Password updated.");
        return "redirect:/profile";
    }
}
