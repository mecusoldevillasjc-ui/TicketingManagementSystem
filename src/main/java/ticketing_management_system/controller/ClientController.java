package ticketing_management_system.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import ticketing_management_system.model.Ticket;
import ticketing_management_system.security.CurrentUser;
import ticketing_management_system.service.TicketService;

/**
 * Client dashboard: four live status boxes. Clicking a box shows that status's tickets in a table right below it
 * (the browser filters the rows below; nothing is fetched again).
 */
@Controller
@RequestMapping("/client")
public class ClientController {

    private final TicketService svc;
    private final CurrentUser current;

    public ClientController(TicketService svc, CurrentUser current) {
        this.svc = svc;
        this.current = current;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Ticket> mine = svc.visibleTo(current.require()); // only this client's tickets, newest first
        model.addAttribute("stats", svc.statusCounts(mine));
        model.addAttribute("tickets", mine);
        return "client/dashboard";
    }
}
