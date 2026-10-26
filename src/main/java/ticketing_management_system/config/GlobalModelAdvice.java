package ticketing_management_system.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import ticketing_management_system.model.Ticket;
import ticketing_management_system.model.User;
import ticketing_management_system.security.CurrentUser;

/** Values every page needs: the signed-in user, the current path (for the active menu item) and option lists. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final CurrentUser current;

    public GlobalModelAdvice(CurrentUser current) { this.current = current; }

    @ModelAttribute("me")
    public User me() { return current.get(); }

    @ModelAttribute("path")
    public String path(HttpServletRequest req) { return req.getRequestURI(); }

    @ModelAttribute("statuses")
    public Ticket.Status[] statuses() { return Ticket.Status.values(); }

    @ModelAttribute("priorities")
    public Ticket.Priority[] priorities() { return Ticket.Priority.values(); }

    @ModelAttribute("categories")
    public Ticket.Category[] categories() { return Ticket.Category.values(); }

    @ModelAttribute("roles")
    public User.Role[] roles() { return User.Role.values(); }
}
