package ticketing_management_system.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import ticketing_management_system.model.Ticket;
import ticketing_management_system.model.User;
import ticketing_management_system.repository.TicketRepository;
import ticketing_management_system.repository.UserRepository;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.service.TicketService;
import ticketing_management_system.util.Web;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketService svc;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final CurrentUser current;

    public AdminController(TicketService svc, TicketRepository tickets, UserRepository users,
                           PasswordEncoder encoder, CurrentUser current) {
        this.svc = svc;
        this.tickets = tickets;
        this.users = users;
        this.encoder = encoder;
        this.current = current;
    }

    // ---------- dashboard ----------

    @GetMapping("/dashboard")
    public String dashboard(Model m) {
        List<Ticket> all = tickets.findAllByOrderByCreatedAtDesc();
        m.addAttribute("stats", svc.statusCounts(all));
        m.addAttribute("total", all.size());
        m.addAttribute("tickets", all);
        return "admin/dashboard";
    }

    // ---------- users ----------

    @GetMapping("/users")
    public String usersPage(Model m) {
        m.addAttribute("userList", users.findAllByOrderByCreatedAtDesc());
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String fullName, @RequestParam String email, @RequestParam String password,
                             @RequestParam User.Role role, RedirectAttributes ra) {
        String mail = email.trim().toLowerCase();
        if (fullName.isBlank() || !mail.matches(EMAIL) || password.length() < 6) {
            Web.err(ra, "Fill in every field with a valid email and a password of at least 6 characters.");
        } else if (users.existsByEmail(mail)) {
            Web.err(ra, "That email is already registered.");
        } else {
            users.save(new User(fullName.trim(), mail, encoder.encode(password), role));
            Web.ok(ra, "Account created for " + fullName.trim() + ".");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam User.Role role, RedirectAttributes ra) {
        User target = users.findById(id).orElse(null);
        if (target == null) Web.err(ra, "User not found.");
        else if (target.getId().equals(current.require().getId())) Web.err(ra, "You can't change your own role.");
        else {
            target.setRole(role);
            users.save(target);
            Web.ok(ra, target.getFullName() + " is now " + target.getRoleLabel() + ".");
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        User target = users.findById(id).orElse(null);
        if (target == null) Web.err(ra, "User not found.");
        else if (target.getId().equals(current.require().getId())) Web.err(ra, "You can't deactivate your own account.");
        else {
            target.setActive(!target.isActive());
            users.save(target);
            Web.ok(ra, target.getFullName() + (target.isActive() ? " can sign in again." : " has been deactivated."));
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset")
    public String reset(@PathVariable Long id, RedirectAttributes ra) {
        User target = users.findById(id).orElse(null);
        if (target == null) {
            Web.err(ra, "User not found.");
        } else {
            String temp = "Tms-" + (100000 + RANDOM.nextInt(900000));
            target.setPassword(encoder.encode(temp));
            users.save(target);
            ra.addFlashAttribute("tempPassword", temp);
            ra.addFlashAttribute("tempFor", target.getFullName());
        }
        return "redirect:/admin/users";
    }

    // ---------- reports ----------

    @GetMapping("/reports")
    public String reports(Model m) {
        List<Ticket> all = tickets.findAllByOrderByCreatedAtDesc();
        long done = all.stream().filter(t -> !t.isWorking()).count();
        m.addAttribute("total", all.size());
        m.addAttribute("rate", all.isEmpty() ? 0 : Math.round(done * 100.0 / all.size()));
        m.addAttribute("avgHours", svc.avgHours(all));
        m.addAttribute("firstResponse", svc.avgFirstResponseMinutes(all));
        m.addAttribute("overdue", all.stream().filter(Ticket::isOverdue).count());
        m.addAttribute("rating", svc.ratingSummary(all));
        m.addAttribute("bySource", svc.sourceCounts(all));
        m.addAttribute("perf", svc.agentPerformance(all));
        m.addAttribute("cats", svc.categoryStats(all));
        m.addAttribute("tickets", all);
        m.addAttribute("unassigned", all.stream().filter(t -> t.getAssignedTo() == null && t.isWorking()).count());
        return "admin/reports";
    }

    @GetMapping("/reports/export.csv")
    public void export(HttpServletResponse res) throws IOException {
        res.setContentType("text/csv; charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"tickets-" + LocalDate.now() + ".csv\"");
        PrintWriter w = res.getWriter();
        w.print('\uFEFF'); // so Excel reads UTF-8 correctly
        w.println("Code,Subject,Category,Priority,Status,Source,Requester,Assignee,Created,Resolved,Rating");
        for (Ticket t : tickets.findAllByOrderByCreatedAtDesc()) {
            w.println(String.join(",",
                    csv(t.getTicketCode()), csv(t.getSubject()), csv(t.getCategoryLabel()), csv(t.getPriorityLabel()),
                    csv(t.getStatusLabel()), csv(t.getSourceLabel()), csv(t.getRequesterLabel()),
                    csv(t.getAssignedTo() == null ? "" : t.getAssignedTo().getFullName()),
                    csv(t.getCreatedLabel()), csv(t.getResolvedAt() == null ? "" : t.getResolvedLabel()),
                    csv(t.getSatisfactionRating() == null ? "" : String.valueOf(t.getSatisfactionRating()))));
        }
        w.flush();
    }

    /** Quotes a value and neutralises spreadsheet formulas (=, +, -, @). */
    private static String csv(String s) {
        if (s == null) return "\"\"";
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
