package ticketing_management_system.model;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ticketing_management_system.util.TimeUtil;

@Entity
@Table(name = "tickets")
public class Ticket {

    // ---------- fixed vocabularies (enums give compile-time safety and one place for labels) ----------

    public enum Status {
        OPEN("Open"), IN_PROGRESS("In Progress"), RESOLVED("Resolved"), CLOSED("Closed");

        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
        public String getKey() { return name().toLowerCase().replace('_', '-'); }
        public int getStep() { return ordinal() + 1; }
        public boolean isWorking() { return this == OPEN || this == IN_PROGRESS; }
    }

    public enum Priority {
        LOW("Low", 120, 2), MEDIUM("Medium", 72, 1), HIGH("High", 24, 0);

        private final String label;
        private final int slaHours; // service target: hours before an unresolved ticket counts as overdue
        private final int rank;     // 0 = most urgent
        Priority(String label, int slaHours, int rank) { this.label = label; this.slaHours = slaHours; this.rank = rank; }
        public String getLabel() { return label; }
        public int getSlaHours() { return slaHours; }
        public int getRank() { return rank; }
        public String getKey() { return name().toLowerCase(); }
    }

    public enum Category {
        IT_SUPPORT("IT Support", "fa-headset"), ACCOUNT("Account", "fa-user-lock"), SOFTWARE("Software", "fa-window-restore"),
        HARDWARE("Hardware", "fa-computer"), OTHERS("Others", "fa-ellipsis");

        private final String label;
        private final String icon;
        Category(String label, String icon) { this.label = label; this.icon = icon; }
        public String getLabel() { return label; }
        public String getIcon() { return icon; }
    }

    /** Where the ticket came from. */
    public enum Source {
        PORTAL("Web portal"), KIOSK("Kiosk");

        private final String label;
        Source(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ---------- columns ----------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticketCode; // TCK-0001

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** The signed-in requester. Null for tickets submitted at the kiosk (walk-up users have no account). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 80)
    private String requesterName;    // kiosk tickets only

    @Column(length = 120)
    private String requesterContact; // kiosk tickets only (phone, email or room)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'PORTAL'")
    private Source source = Source.PORTAL;

    /** The agent working on the ticket. Null means it is still waiting in the queue. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime firstRespondedAt; // first staff reply or first move to In Progress
    private LocalDateTime resolvedAt;

    @Column(name = "satisfaction_rating")
    private Integer satisfactionRating; // 1-5, given by the client when closing

    public Ticket() {}

    // ---------- logic helpers ----------

    public boolean isWorking() { return status != null && status.isWorking(); }

    public boolean isKiosk() { return source == Source.KIOSK; }

    /** Past its service target and still not resolved. */
    public boolean isOverdue() {
        if (!isWorking() || createdAt == null || priority == null) return false;
        return Duration.between(createdAt, LocalDateTime.now()).toHours() >= priority.getSlaHours();
    }

    public Double getHoursToResolve() {
        if (resolvedAt == null || createdAt == null) return null;
        return Duration.between(createdAt, resolvedAt).toMinutes() / 60.0;
    }

    public Double getMinutesToFirstResponse() {
        if (firstRespondedAt == null || createdAt == null) return null;
        return (double) Duration.between(createdAt, firstRespondedAt).toMinutes();
    }

    // ---------- helpers used by the pages ----------

    public String getRequesterLabel() {
        if (user != null) return user.getFullName();
        return requesterName == null || requesterName.isBlank() ? "Kiosk guest" : requesterName;
    }

    public String getRequesterContactLabel() {
        if (user != null) return user.getEmail();
        return requesterContact == null || requesterContact.isBlank() ? "No contact left" : requesterContact;
    }

    public String getRequesterInitials() {
        String[] p = getRequesterLabel().trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "")).toUpperCase();
    }

    public String getStatusLabel() { return status == null ? "" : status.getLabel(); }
    public String getStatusKey() { return status == null ? "" : status.getKey(); }
    public int getStep() { return status == null ? 1 : status.getStep(); }
    public String getPriorityLabel() { return priority == null ? "" : priority.getLabel(); }
    public String getPriorityKey() { return priority == null ? "" : priority.getKey(); }
    public int getPriorityRank() { return priority == null ? 2 : priority.getRank(); }
    public String getCategoryLabel() { return category == null ? "" : category.getLabel(); }
    public String getCategoryIcon() { return category == null ? "fa-ticket" : category.getIcon(); }
    public String getSourceLabel() { return source == null ? "" : source.getLabel(); }
    public String getAgo() { return TimeUtil.ago(createdAt); }
    public String getUpdatedAgo() { return TimeUtil.ago(updatedAt); }
    public String getCreatedLabel() { return TimeUtil.full(createdAt); }
    public String getCreatedShort() { return TimeUtil.shortDate(createdAt); }
    /** Seconds since 1970, used by the table to sort by date. */
    public long getCreatedEpoch() { return createdAt == null ? 0 : createdAt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond(); }
    public String getAssigneeLabel() { return assignedTo == null ? "Unassigned" : assignedTo.getFullName(); }
    public String getResolvedLabel() { return TimeUtil.full(resolvedAt); }

    // ---------- getters and setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getRequesterContact() { return requesterContact; }
    public void setRequesterContact(String requesterContact) { this.requesterContact = requesterContact; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getFirstRespondedAt() { return firstRespondedAt; }
    public void setFirstRespondedAt(LocalDateTime firstRespondedAt) { this.firstRespondedAt = firstRespondedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public Integer getSatisfactionRating() { return satisfactionRating; }
    public void setSatisfactionRating(Integer satisfactionRating) { this.satisfactionRating = satisfactionRating; }
}
