package ticketing_management_system.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ticketing_management_system.model.Ticket;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Ticket> findAllByOrderByCreatedAtDesc();
    Optional<Ticket> findByTicketCode(String ticketCode);
}
