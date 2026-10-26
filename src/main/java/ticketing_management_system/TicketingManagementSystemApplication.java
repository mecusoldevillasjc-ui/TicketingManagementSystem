package ticketing_management_system;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import ticketing_management_system.model.Ticket;
import ticketing_management_system.model.User;
import ticketing_management_system.repository.TicketRepository;
import ticketing_management_system.repository.UserRepository;

@SpringBootApplication
public class TicketingManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingManagementSystemApplication.class, args);
    }

    /** Creates the default accounts (BCrypt hashed) and, on an empty database, some demo tickets. */
    @Bean
    public CommandLineRunner seed(UserRepository users, TicketRepository tickets, PasswordEncoder encoder,
                                  @Value("${app.seed-demo:true}") boolean seedDemo) {
        return args -> {
            ensure(users, encoder, "System Admin", "admin@system.com", "admin123", User.Role.ADMIN);
            User agent = ensure(users, encoder, "Support Agent", "agent@system.com", "agent123", User.Role.AGENT);
            User agent2 = ensure(users, encoder, "Maria Santos", "agent2@system.com", "agent123", User.Role.AGENT);
            User client = ensure(users, encoder, "John Client", "client@system.com", "client123", User.Role.CLIENT);

            if (seedDemo && tickets.count() == 0) seedDemoTickets(tickets, client, agent, agent2);
        };
    }

    private User ensure(UserRepository users, PasswordEncoder encoder, String name, String email, String password, User.Role role) {
        return users.findByEmail(email).orElseGet(() -> users.save(new User(name, email, encoder.encode(password), role)));
    }

    private void seedDemoTickets(TicketRepository tickets, User client, User a1, User a2) {
        Object[][] rows = {
            // subject, category, priority, status, from kiosk?
            {"Printer offline, Room 204", Ticket.Category.HARDWARE, Ticket.Priority.HIGH, Ticket.Status.OPEN, false},
            {"Wi-Fi reset request", Ticket.Category.IT_SUPPORT, Ticket.Priority.LOW, Ticket.Status.RESOLVED, true},
            {"Cannot log in to the student portal", Ticket.Category.ACCOUNT, Ticket.Priority.HIGH, Ticket.Status.IN_PROGRESS, false},
            {"Install statistics software", Ticket.Category.SOFTWARE, Ticket.Priority.MEDIUM, Ticket.Status.CLOSED, false},
            {"Projector flickers during class", Ticket.Category.HARDWARE, Ticket.Priority.MEDIUM, Ticket.Status.IN_PROGRESS, true},
            {"Email storage is full", Ticket.Category.ACCOUNT, Ticket.Priority.LOW, Ticket.Status.OPEN, false},
            {"Keyboard keys not responding", Ticket.Category.HARDWARE, Ticket.Priority.LOW, Ticket.Status.RESOLVED, true},
            {"Need access to the shared drive", Ticket.Category.IT_SUPPORT, Ticket.Priority.MEDIUM, Ticket.Status.OPEN, false},
            {"Software license expired", Ticket.Category.SOFTWARE, Ticket.Priority.HIGH, Ticket.Status.OPEN, true},
            {"Library computer is very slow", Ticket.Category.OTHERS, Ticket.Priority.MEDIUM, Ticket.Status.CLOSED, false},
            {"Second monitor not detected", Ticket.Category.HARDWARE, Ticket.Priority.MEDIUM, Ticket.Status.RESOLVED, false},
            {"Password reset email not arriving", Ticket.Category.ACCOUNT, Ticket.Priority.HIGH, Ticket.Status.IN_PROGRESS, false}
        };
        for (int i = 0; i < rows.length; i++) {
            Ticket.Status status = (Ticket.Status) rows[i][3];
            boolean kiosk = (Boolean) rows[i][4];
            LocalDateTime created = LocalDateTime.now().minusDays(i % 7).minusHours(i * 2L + 1);

            Ticket t = new Ticket();
            t.setTicketCode("TMP-" + i);
            t.setSubject((String) rows[i][0]);
            t.setCategory((Ticket.Category) rows[i][1]);
            t.setPriority((Ticket.Priority) rows[i][2]);
            t.setStatus(status);
            t.setDescription("Sample ticket created for demonstration.");
            if (kiosk) {
                t.setSource(Ticket.Source.KIOSK);
                t.setRequesterName("Walk-up visitor");
                t.setRequesterContact("Room " + (200 + i));
            } else {
                t.setUser(client);
            }
            t.setCreatedAt(created);
            t.setUpdatedAt(created);

            if (status != Ticket.Status.OPEN) {
                t.setAssignedTo(i % 2 == 0 ? a1 : a2);
                t.setFirstRespondedAt(created.plusMinutes(15 + i * 10L));
            }
            if (status == Ticket.Status.RESOLVED || status == Ticket.Status.CLOSED) t.setResolvedAt(created.plusHours(2 + i * 2L));
            if (status == Ticket.Status.CLOSED && !kiosk) t.setSatisfactionRating(4 + (i % 2));

            t = tickets.save(t);
            t.setTicketCode(String.format("TCK-%04d", t.getId()));
            tickets.save(t);
        }
    }
}
