package ticketing_management_system.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ticketing_management_system.model.Comment;
import ticketing_management_system.model.Ticket;
import ticketing_management_system.model.User;
import ticketing_management_system.repository.CommentRepository;
import ticketing_management_system.repository.TicketRepository;
import ticketing_management_system.repository.UserRepository;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.service.TicketService;
import ticketing_management_system.util.Web;

/** Ticket screens shared by all signed-in roles. What each role can see or do is decided in TicketService. */
@Controller
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService svc;
    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final UserRepository users;
    private final CurrentUser current;

    public TicketController(TicketService svc, TicketRepository tickets, CommentRepository comments,
                            UserRepository users, CurrentUser current) {
        this.svc = svc;
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.current = current;
    }

    /**
     * The ticket data table. Clients get only their own tickets (see TicketService.visibleTo), staff get everyone's.
     * ?status=OPEN | IN_PROGRESS | RESOLVED | CLOSED | ALL picks the starting filter (default Open);
     * searching, filtering, sorting and paging then happen in the browser.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String status, Model model) {
        User me = current.require();
        String s = status == null ? "OPEN" : status.trim().toUpperCase();
        if ("ALL".equals(s)) s = "";
        else {
            try { Ticket.Status.valueOf(s); } catch (IllegalArgumentException e) { s = "OPEN"; }
        }
        model.addAttribute("tickets", svc.visibleTo(me)); // newest first
        model.addAttribute("fStatus", s);
        return "tickets/list";
    }

    /** The "new ticket" form is a modal on the client dashboard, so this old address just opens it. */
    @GetMapping("/new")
    public String newForm() {
        return current.require().isClient() ? "redirect:/client/dashboard?open=submit" : "redirect:/tickets";
    }

    @PostMapping
    public String create(@RequestParam String subject, @RequestParam Ticket.Category category,
                         @RequestParam Ticket.Priority priority, @RequestParam String description,
                         RedirectAttributes ra) {
        User me = current.require();
        if (!me.isClient()) return "redirect:/tickets";
        if (subject.isBlank() || description.isBlank()) {
            Web.err(ra, "Add a subject and describe the problem.");
            return "redirect:/client/dashboard?open=submit";
        }
        Ticket t = svc.create(me, subject, category, priority, description);
        Web.ok(ra, "Ticket " + t.getTicketCode() + " submitted.");
        return "redirect:/client/dashboard"; // back to the dashboard, where the counts are now up to date
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User me = current.require();
        Ticket t = tickets.findById(id).orElse(null);
        if (t == null || !svc.canView(me, t)) {
            Web.err(ra, "That ticket doesn't exist or you don't have access to it.");
            return "redirect:/tickets";
        }
        List<Comment> thread = comments.findByTicketIdOrderByCreatedAtAsc(id);
        if (me.isClient()) thread = thread.stream().filter(c -> !c.isInternalNote()).toList();

        boolean canManage = !me.isClient()
                && (me.isAdmin() || t.getAssignedTo() == null || t.getAssignedTo().getId().equals(me.getId()));
        model.addAttribute("ticket", t);
        model.addAttribute("comments", thread);
        model.addAttribute("canManage", canManage);
        model.addAttribute("canClientAct", me.isClient() && t.getStatus() == Ticket.Status.RESOLVED);
        model.addAttribute("agents", users.findByRoleOrderByFullNameAsc(User.Role.AGENT).stream().filter(User::isActive).toList());
        return "tickets/detail";
    }

    @PostMapping("/{id}/comment")
    public String comment(@PathVariable Long id, @RequestParam String message,
                          @RequestParam(defaultValue = "false") boolean internal, RedirectAttributes ra) {
        User me = current.require();
        Ticket t = tickets.findById(id).orElse(null);
        if (t == null || !svc.canView(me, t)) return "redirect:/tickets";
        Web.result(ra, svc.addComment(t, me, message, internal), internal ? "Internal note added." : "Reply sent.");
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam Ticket.Status status,
                         @RequestParam(required = false) Integer rating, RedirectAttributes ra) {
        User me = current.require();
        Ticket t = tickets.findById(id).orElse(null);
        if (t == null || !svc.canView(me, t)) return "redirect:/tickets";
        Web.result(ra, svc.changeStatus(t, status, me, rating), "Status changed to " + status.getLabel() + ".");
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam(required = false) Long assigneeId, RedirectAttributes ra) {
        User me = current.require();
        Ticket t = tickets.findById(id).orElse(null);
        if (t == null || !svc.canView(me, t)) return "redirect:/tickets";
        Web.result(ra, svc.assign(t, assigneeId, me), "Assignment updated.");
        return "redirect:/tickets/" + id;
    }
}
