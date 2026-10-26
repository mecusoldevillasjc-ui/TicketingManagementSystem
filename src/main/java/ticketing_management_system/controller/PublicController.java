package ticketing_management_system.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ticketing_management_system.model.Ticket;
import ticketing_management_system.model.User;
import ticketing_management_system.repository.TicketRepository;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.service.TicketService;
import ticketing_management_system.util.Web;

/** Pages anyone can open without signing in: homepage, ticket tracking and the walk-up kiosk. */
@Controller
public class PublicController {

    private final TicketService svc;
    private final TicketRepository tickets;
    private final CurrentUser current;

    public PublicController(TicketService svc, TicketRepository tickets, CurrentUser current) {
        this.svc = svc;
        this.tickets = tickets;
        this.current = current;
    }

    @GetMapping("/")
    public String home(Model m) {
        // someone who is already signed in goes straight to their own dashboard, never the landing page
        User me = current.get();
        if (me != null) return "redirect:" + me.getHomePath();
        List<Ticket> all = tickets.findAll();
        m.addAttribute("total", all.size());
        m.addAttribute("solved", all.stream().filter(t -> !t.isWorking()).count());
        m.addAttribute("avgHours", svc.avgHours(all));
        return "public/home";
    }

    /** Public status lookup. It shows progress only, never the subject, description or conversation. */
    @GetMapping("/track")
    public String track(@RequestParam(required = false) String code, Model m) {
        if (code != null && !code.isBlank()) {
            String c = code.trim().toUpperCase();
            if (c.matches("\\d{1,6}")) c = String.format("TCK-%04d", Long.parseLong(c)); // "42" -> TCK-0042
            m.addAttribute("code", c);
            var found = tickets.findByTicketCode(c);
            if (found.isPresent()) m.addAttribute("ticket", found.get());
            else m.addAttribute("notFound", true);
        }
        return "public/track";
    }

    // ---------- kiosk ----------

    @GetMapping("/kiosk")
    public String kiosk() { return "public/kiosk"; }

    @PostMapping("/kiosk")
    public String kioskSubmit(@RequestParam String category, @RequestParam String priority,
                              @RequestParam String subject, @RequestParam String description,
                              @RequestParam("visitorName") String name, @RequestParam(defaultValue = "") String contact,
                              @RequestParam(name = "website", defaultValue = "") String honeypot,
                              RedirectAttributes ra) {
        if (!honeypot.isBlank()) return "redirect:/kiosk"; // a hidden field only bots fill in
        if (subject.isBlank() || description.isBlank() || name.isBlank()) {
            Web.err(ra, "Please fill in your name, a short title and what happened.");
            return "redirect:/kiosk";
        }
        Ticket t = svc.createFromKiosk(name, contact, subject, svc.parseCategory(category),
                svc.parsePriority(priority), description);
        ra.addFlashAttribute("code", t.getTicketCode());
        return "redirect:/kiosk/done";
    }

    @GetMapping("/kiosk/done")
    public String kioskDone(Model m) {
        // the code arrives as a flash attribute; if someone opens this page directly, send them back
        return m.containsAttribute("code") ? "public/kiosk-done" : "redirect:/kiosk";
    }
}
