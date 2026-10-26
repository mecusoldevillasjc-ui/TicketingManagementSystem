package ticketing_management_system.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import ticketing_management_system.model.Ticket;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.service.TicketService;

/** Agent dashboard: the same four boxes as the client, counted over every ticket in the queue. */
@Controller
@RequestMapping("/agent")
public class AgentController {

    private final TicketService svc;
    private final CurrentUser current;

    public AgentController(TicketService svc, CurrentUser current) {
        this.svc = svc;
        this.current = current;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Ticket> all = svc.visibleTo(current.require());
        model.addAttribute("stats", svc.statusCounts(all));
        model.addAttribute("tickets", all);
        return "agent/dashboard";
    }
}
