package me.jumpwatch.webserver.automation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimal, bounded recurrence specification. No system cron, shell or dependency.
 * Supports one 5-field standard cron expression OR one interval (e.g. 1m, 6h).
 */
public final class TaskSchedule {
    public final String expression;
    public final ZoneId timeZone;
    public final boolean cron;
    public final Duration interval;
    private final boolean[] minutes;
    private final boolean[] hours;
    private final boolean[] days;
    private final boolean[] months;
    private final boolean[] weekdays;
    private final boolean anyDay;
    private final boolean anyWeekday;

    private TaskSchedule(String expression, ZoneId timeZone, Duration interval, boolean[][] fields,
                         boolean anyDay, boolean anyWeekday) {
        this.expression = expression;
        this.timeZone = timeZone;
        this.cron = interval == null;
        this.interval = interval;
        this.minutes = fields == null ? null : fields[0];
        this.hours = fields == null ? null : fields[1];
        this.days = fields == null ? null : fields[2];
        this.months = fields == null ? null : fields[3];
        this.weekdays = fields == null ? null : fields[4];
        this.anyDay = anyDay;
        this.anyWeekday = anyWeekday;
    }

    public static TaskSchedule fromMap(Map<?, ?> cfg) {
        String cronValue = value(cfg.get("Schedule"));
        String every = value(cfg.get("Every"));
        if (cronValue.isEmpty() == every.isEmpty())
            throw new IllegalArgumentException("Scheduled tasks must specify exactly one of Schedule or Every");
        if (!value(cfg.get("OverlapPolicy")).isEmpty() &&
                !value(cfg.get("OverlapPolicy")).equalsIgnoreCase("skip"))
            throw new IllegalArgumentException("Only OverlapPolicy: skip is supported");
        ZoneId zone;
        try {
            zone = ZoneId.of(value(cfg.get("TimeZone")).isEmpty() ? "UTC" : value(cfg.get("TimeZone")));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid TimeZone", ex);
        }
        if (!every.isEmpty()) {
            if (!Pattern.matches("[1-9][0-9]{0,6}[smhd]", every))
                throw new IllegalArgumentException("Every must be a positive number followed by s, m, h, d");
            long count = Long.parseLong(every.substring(0, every.length() - 1));
            char unit = every.charAt(every.length() - 1);
            Duration period = unit == 's' ? Duration.ofSeconds(count) :
                    unit == 'm' ? Duration.ofMinutes(count) :
                    unit == 'h' ? Duration.ofHours(count) : Duration.ofDays(count);
            if (period.compareTo(Duration.ofSeconds(10)) < 0 ||
                    period.compareTo(Duration.ofDays(365)) > 0)
                throw new IllegalArgumentException("Every must be between 10s and 365d");
            return new TaskSchedule(every, zone, period, null, false, false);
        }
        String[] parts = cronValue.trim().split("\\s+");
        if (parts.length != 5) throw new IllegalArgumentException("Schedule must contain five cron fields");
        boolean[][] fields = new boolean[5][];
        fields[0] = parseField(parts[0], 0, 59, false);
        fields[1] = parseField(parts[1], 0, 23, false);
        fields[2] = parseField(parts[2], 1, 31, false);
        fields[3] = parseField(parts[3], 1, 12, false);
        fields[4] = parseField(parts[4], 0, 7, true);
        return new TaskSchedule(cronValue, zone, null, fields,
                parts[2].equals("*"), parts[4].equals("*"));
    }

    /** Cron-compatible day-of-month/day-of-week OR behavior if both fields are restricted. */
    public boolean matches(Instant instant) {
        if (!cron) throw new IllegalStateException("Interval schedules are not calendar expressions");
        ZonedDateTime date = instant.atZone(timeZone);
        if (!minutes[date.getMinute()] || !hours[date.getHour()] || !months[date.getMonthValue()])
            return false;
        boolean monthDay = days[date.getDayOfMonth()];
        boolean weekDay = weekdays[date.getDayOfWeek().getValue() % 7];
        // Standard Vixie cron semantics: if both DOM and DOW are restricted,
        // EITHER may match (otherwise use the one restricted field).
        if (anyDay && anyWeekday) return true;
        if (anyDay) return weekDay;
        if (anyWeekday) return monthDay;
        return monthDay || weekDay;
    }

    private static boolean[] parseField(String source, int min, int max, boolean weekday) {
        boolean[] result = new boolean[max + 1];
        for (String token : source.split(",", -1)) {
            if (token.isEmpty()) throw new IllegalArgumentException("Invalid empty cron field token");
            String[] step = token.split("/", -1);
            if (step.length > 2) throw new IllegalArgumentException("Invalid cron step: " + token);
            int stride = step.length == 2 ? number(step[1], min, max) : 1;
            if (stride < 1) throw new IllegalArgumentException("Cron step must be greater than zero");
            String base = step[0];
            int first;
            int last;
            if (base.equals("*")) {
                first = min;
                last = max;
            } else if (base.contains("-")) {
                String[] limits = base.split("-", -1);
                if (limits.length != 2) throw new IllegalArgumentException("Invalid cron range: " + base);
                first = number(limits[0], min, max);
                last = number(limits[1], min, max);
            } else {
                first = number(base, min, max);
                last = step.length == 2 ? max : first;
            }
            if (first > last) throw new IllegalArgumentException("Descending cron range: " + base);
            for (int i = first; i <= last; i += stride)
                result[weekday && i == 7 ? 0 : i] = true;
        }
        return result;
    }

    private static int number(String text, int min, int max) {
        if (!text.matches("[0-9]{1,3}")) throw new IllegalArgumentException("Invalid cron number");
        int n = Integer.parseInt(text);
        if (n < min || n > max) throw new IllegalArgumentException("Cron value out of range: " + n);
        return n;
    }

    private static String value(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
