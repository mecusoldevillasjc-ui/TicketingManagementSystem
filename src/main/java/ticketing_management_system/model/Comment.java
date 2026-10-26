package ticketing_management_system.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ticketing_management_system.util.TimeUtil;

/** A reply, an internal staff note, or an automatic event (status change, assignment) on a ticket. */
@Entity
@Table(name = "ticket_comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    /** Null when the event comes from a kiosk guest (they have no account). */
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "internal_note", nullable = false)
    private boolean internalNote;

    @Column(name = "system_event", nullable = false)
    private boolean systemEvent;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Comment() {}

    public Comment(Ticket ticket, User author, String message, boolean internalNote, boolean systemEvent) {
        this.ticket = ticket;
        this.author = author;
        this.message = message;
        this.internalNote = internalNote;
        this.systemEvent = systemEvent;
    }

    public String getAuthorName() {
        if (author != null) return author.getFullName();
        return ticket != null ? ticket.getRequesterLabel() : "Guest";
    }

    public String getAuthorInitials() {
        if (author != null) return author.getInitials();
        return ticket != null ? ticket.getRequesterInitials() : "G";
    }

    public String getAgo() { return TimeUtil.ago(createdAt); }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getAuthor() { return author; }
    public String getMessage() { return message; }
    public boolean isInternalNote() { return internalNote; }
    public boolean isSystemEvent() { return systemEvent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
