package ticketing_management_system.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ticketing_management_system.model.Comment;
import ticketing_management_system.model.Ticket;
import ticketing_management_system.model.User;
import ticketing_management_system.repository.CommentRepository;
import ticketing_management_system.repository.TicketRepository;
import ticketing_management_system.repository.UserRepository;

/**
 * All ticket rules and report calculations live here, so controllers stay thin and the dashboard,
 * reports and CSV always agree. Methods that can refuse an action return an error message, or null on success.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final UserRepository users;

    public TicketService(TicketRepository tickets, CommentRepository comments, UserRepository users) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
    }

    // =====================================================================
    // Creating tickets
    // =====================================================================

    /** A signed-in client submits from the web portal. */
    @Transactional
    public Ticket create(User owner, String subject, Ticket.Category category, Ticket.Priority priority, String description) {
        return persist(owner, null, null, Ticket.Source.PORTAL, subject, category, priority, description);
    }

    /** A walk-up user submits at the kiosk (no account needed, so we keep a name and optional contact). */
    @Transactional
    public Ticket createFromKiosk(String name, String contact, String subject, Ticket.Category category,
                                  Ticket.Priority priority, String description) {
        return persist(null, name, contact, Ticket.Source.KIOSK, subject, category, priority, description);
    }

    private Ticket persist(User owner, String name, String contact, Ticket.Source source, String subject,
                           Ticket.Category category, Ticket.Priority priority, String description) {
        Ticket t = new Ticket();
        t.setTicketCode("TMP-" + System.nanoTime()); // replaced below once the database id exists
        t.setSubject(clip(subject, 120));
        t.setCategory(category != null ? category : Ticket.Category.OTHERS);
        t.setPriority(priority != null ? priority : Ticket.Priority.MEDIUM);
        t.setDescription(clip(description, 2000));
        t.setUser(owner);
        t.setRequesterName(name == null ? null : clip(name, 80));
        t.setRequesterContact(contact == null ? null : clip(contact, 120));
        t.setSource(source);
        t = tickets.save(t);
        t.setTicketCode(String.format("TCK-%04d", t.getId())); // unique, sequential, human friendly
        t = tickets.save(t);
        log(t, owner, source == Ticket.Source.KIOSK ? "submitted this ticket at the kiosk" : "opened this ticket");
        return t;
    }

    public Ticket.Category parseCategory(String s) {
        try { return Ticket.Category.valueOf(s); } catch (RuntimeException e) { return Ticket.Category.OTHERS; }
    }

    public Ticket.Priority parsePriority(String s) {
        try { return Ticket.Priority.valueOf(s); } catch (RuntimeException e) { return Ticket.Priority.MEDIUM; }
    }

    // =====================================================================
    // Reading tickets
    // =====================================================================

    /** Clients only ever see their own tickets; agents and admins see everything. */
    public List<Ticket> visibleTo(User u) {
        return u.isClient() ? tickets.findByUserIdOrderByCreatedAtDesc(u.getId()) : tickets.findAllByOrderByCreatedAtDesc();
    }

    public boolean canView(User u, Ticket t) {
        return !u.isClient() || (t.getUser() != null && t.getUser().getId().equals(u.getId()));
    }

    public List<Ticket> filter(List<Ticket> list, Ticket.Status status, Ticket.Priority priority, Ticket.Category category,
                               String q, String scope, User me) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        String sc = scope == null ? "all" : scope;
        return list.stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> priority == null || t.getPriority() == priority)
                .filter(t -> category == null || t.getCategory() == category)
                .filter(t -> needle.isEmpty()
                        || (t.getTicketCode() + " " + t.getSubject() + " " + t.getRequesterLabel()).toLowerCase().contains(needle))
                .filter(t -> switch (sc) {
                    case "mine" -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(me.getId());
                    case "unassigned" -> t.getAssignedTo() == null && t.isWorking();
                    default -> true;
                })
                .toList();
    }

    /** Most urgent first, then oldest first. */
    public List<Ticket> sortQueue(List<Ticket> list) {
        return list.stream()
                .sorted(Comparator.comparingInt(Ticket::getPriorityRank).thenComparing(Ticket::getCreatedAt))
                .toList();
    }

    public List<Comment> recentActivity() { return comments.findTop8BySystemEventTrueOrderByCreatedAtDesc(); }

    // =====================================================================
    // Changing tickets
    // =====================================================================

    @Transactional
    public String changeStatus(Ticket t, Ticket.Status next, User actor, Integer rating) {
        if (next == null) return "Unknown status.";
        if (next == t.getStatus()) return "This ticket is already " + next.getLabel() + ".";

        if (actor.isClient()) {
            boolean own = t.getUser() != null && t.getUser().getId().equals(actor.getId());
            boolean allowed = own && t.getStatus() == Ticket.Status.RESOLVED
                    && (next == Ticket.Status.CLOSED || next == Ticket.Status.OPEN);
            if (!allowed) return "You can only close or reopen your own resolved tickets.";
        } else {
            if (t.getStatus() == Ticket.Status.CLOSED && !actor.isAdmin()) return "Only an admin can reopen a closed ticket.";
            if (actor.isAgent()) {
                if (t.getAssignedTo() == null) {
                    t.setAssignedTo(actor);
                    log(t, actor, "took this ticket");
                } else if (!t.getAssignedTo().getId().equals(actor.getId())) {
                    return "This ticket belongs to " + t.getAssignedTo().getFullName() + ". Ask an admin to reassign it.";
                }
            }
        }

        Ticket.Status old = t.getStatus();
        LocalDateTime now = LocalDateTime.now();
        boolean done = next == Ticket.Status.RESOLVED || next == Ticket.Status.CLOSED;

        t.setStatus(next);
        t.setUpdatedAt(now);
        if (done && t.getResolvedAt() == null) t.setResolvedAt(now);
        if (!done) t.setResolvedAt(null);
        if (!actor.isClient() && next == Ticket.Status.IN_PROGRESS && t.getFirstRespondedAt() == null) t.setFirstRespondedAt(now);
        if (actor.isClient() && next == Ticket.Status.CLOSED && rating != null && rating >= 1 && rating <= 5) {
            t.setSatisfactionRating(rating);
        }
        tickets.save(t);
        log(t, actor, "changed the status from " + old.getLabel() + " to " + next.getLabel());
        return null;
    }

    @Transactional
    public String assign(Ticket t, Long agentId, User actor) {
        if (actor.isClient()) return "You can't assign tickets.";
        User target = null;
        if (agentId != null) {
            target = users.findById(agentId).filter(u -> !u.isClient() && u.isActive()).orElse(null);
            if (target == null) return "Choose a valid agent.";
        }
        if (actor.isAgent()) {
            if (target == null || !target.getId().equals(actor.getId())) return "Agents can only take tickets for themselves.";
            if (t.getAssignedTo() != null && !t.getAssignedTo().getId().equals(actor.getId()))
                return "This ticket already belongs to " + t.getAssignedTo().getFullName() + ".";
        }
        t.setAssignedTo(target);
        t.setUpdatedAt(LocalDateTime.now());
        tickets.save(t);

        if (target == null) log(t, actor, "moved this ticket back to the queue");
        else if (target.getId().equals(actor.getId())) log(t, actor, "took this ticket");
        else log(t, actor, "assigned this ticket to " + target.getFullName());
        return null;
    }

    @Transactional
    public String addComment(Ticket t, User author, String message, boolean internal) {
        if (message == null || message.isBlank()) return "Write a message first.";
        if (t.getStatus() == Ticket.Status.CLOSED) return "This ticket is closed.";

        boolean note = internal && !author.isClient();
        comments.save(new Comment(t, author, clip(message, 2000), note, false));

        LocalDateTime now = LocalDateTime.now();
        if (!author.isClient() && !note && t.getFirstRespondedAt() == null) t.setFirstRespondedAt(now); // first response time
        t.setUpdatedAt(now);
        tickets.save(t);
        return null;
    }

    private void log(Ticket t, User actor, String what) {
        comments.save(new Comment(t, actor, what, false, true));
    }

    private static String clip(String s, int max) {
        String v = s == null ? "" : s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    // =====================================================================
    // Numbers for dashboards and reports (keys are display labels, in a fixed order)
    // =====================================================================

    public Map<String, Long> count(List<Ticket> list, Function<Ticket, String> key, List<String> order) {
        Map<String, Long> m = new LinkedHashMap<>();
        order.forEach(k -> m.put(k, 0L));
        list.forEach(t -> m.merge(key.apply(t), 1L, Long::sum));
        return m;
    }

    public Map<String, Long> statusCounts(List<Ticket> list) {
        return count(list, Ticket::getStatusLabel, Arrays.stream(Ticket.Status.values()).map(Ticket.Status::getLabel).toList());
    }

    public Map<String, Long> categoryCounts(List<Ticket> list) {
        return count(list, Ticket::getCategoryLabel, Arrays.stream(Ticket.Category.values()).map(Ticket.Category::getLabel).toList());
    }

    public Map<String, Long> priorityCounts(List<Ticket> list) {
        return count(list, Ticket::getPriorityLabel, Arrays.stream(Ticket.Priority.values()).map(Ticket.Priority::getLabel).toList());
    }

    public Map<String, Long> sourceCounts(List<Ticket> list) {
        return count(list, Ticket::getSourceLabel, Arrays.stream(Ticket.Source.values()).map(Ticket.Source::getLabel).toList());
    }

    /** Tickets created per day for the last 7 days, oldest first. */
    public Map<String, Long> lastSevenDays(List<Ticket> list) {
        Map<String, Long> m = new LinkedHashMap<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            m.put(f.format(d), list.stream().filter(t -> t.getCreatedAt().toLocalDate().equals(d)).count());
        }
        return m;
    }

    /** Average hours from creation to first resolution. */
    public double avgHours(List<Ticket> list) {
        double avg = list.stream().map(Ticket::getHoursToResolve).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.round(avg * 10) / 10.0;
    }

    /** Average minutes until someone on the team first responded. */
    public long avgFirstResponseMinutes(List<Ticket> list) {
        return Math.round(list.stream().map(Ticket::getMinutesToFirstResponse).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0));
    }

    /** Customer satisfaction: average, number of ratings and how many of each star. */
    public Map<String, Object> ratingSummary(List<Ticket> all) {
        List<Integer> rated = all.stream().map(Ticket::getSatisfactionRating).filter(Objects::nonNull).toList();
        Map<String, Long> dist = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            final int s = star;
            dist.put(star + " star" + (star == 1 ? "" : "s"), rated.stream().filter(r -> r == s).count());
        }
        double avg = rated.stream().mapToInt(Integer::intValue).average().orElse(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avg", Math.round(avg * 10) / 10.0);
        m.put("count", rated.size());
        m.put("dist", dist);
        return m;
    }

    public List<Map<String, Object>> agentPerformance(List<Ticket> all) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User a : users.findByRoleOrderByFullNameAsc(User.Role.AGENT)) {
            List<Ticket> mine = all.stream()
                    .filter(t -> t.getAssignedTo() != null && t.getAssignedTo().getId().equals(a.getId())).toList();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("agent", a);
            r.put("assigned", mine.size());
            r.put("active", mine.stream().filter(Ticket::isWorking).count());
            r.put("resolved", mine.stream().filter(t -> !t.isWorking()).count());
            r.put("overdue", mine.stream().filter(Ticket::isOverdue).count());
            r.put("avgHours", avgHours(mine));
            r.put("firstResponse", avgFirstResponseMinutes(mine));
            r.put("rating", ratingSummary(mine).get("avg"));
            rows.add(r);
        }
        return rows;
    }

    /** Which categories generate the most tickets and the most delays: the "recurring bottlenecks" view. */
    public List<Map<String, Object>> categoryStats(List<Ticket> all) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Ticket.Category c : Ticket.Category.values()) {
            List<Ticket> in = all.stream().filter(t -> t.getCategory() == c).toList();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("category", c.getLabel());
            r.put("icon", c.getIcon());
            r.put("total", in.size());
            r.put("active", in.stream().filter(Ticket::isWorking).count());
            r.put("overdue", in.stream().filter(Ticket::isOverdue).count());
            r.put("avgHours", avgHours(in));
            r.put("share", all.isEmpty() ? 0L : Math.round(in.size() * 100.0 / all.size()));
            rows.add(r);
        }
        rows.sort((a, b) -> Integer.compare((Integer) b.get("total"), (Integer) a.get("total")));
        return rows;
    }
}
