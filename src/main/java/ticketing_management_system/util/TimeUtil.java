package ticketing_management_system.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TimeUtil {
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private TimeUtil() {}

    public static String shortDate(LocalDateTime t) { return t == null ? "-" : SHORT.format(t); }

    public static String full(LocalDateTime t) { return t == null ? "-" : FULL.format(t); }

    public static String ago(LocalDateTime t) {
        if (t == null) return "";
        Duration d = Duration.between(t, LocalDateTime.now());
        long m = d.toMinutes();
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (d.toHours() < 24) return d.toHours() + "h ago";
        if (d.toDays() < 7) return d.toDays() + "d ago";
        return DAY.format(t);
    }
}
