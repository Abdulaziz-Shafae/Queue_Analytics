package com.example.queueanalytics.Service;

import com.example.queueanalytics.Api.ApiException;
import com.example.queueanalytics.Config.AnalyticsProperties;
import com.example.queueanalytics.Repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final AnalyticsRepository repository;
    private final AnalyticsProperties properties;

    public record Point(Instant collectionSlot, Instant collectedAt, Instant sourceUpdatedAt, Integer waitMinutes, Boolean isOpen, boolean isStale, boolean isValid) {
    }

    public record Hour(int hour, Double averageWait, int sampleCount, int observedCount, int expectedSamples, double coverage, boolean lowCoverage) {
    }

    public record HistoricalHour(int hour, Double averageWait, Double p75DailyMean, int daysCount, int sampleCount, String confidence) {
    }

    public ZoneId displayZone() {
        return ZoneId.of(properties.getZone());
    }

    public List<Map<String, Object>> listRides() {
        LocalDate today = LocalDate.now(displayZone());
        Map<Long, Map<String, Object>> latest = repository.findLatestSnapshots().stream().collect(Collectors.toMap(row -> number(row, "sourceRideId"), row -> row));
        Map<Long, List<Point>> day = repository.findSnapshots(dayStart(today), dayStart(today.plusDays(1)), null).stream().collect(Collectors.groupingBy(row -> number(row, "sourceRideId"), Collectors.mapping(this::convertPoint, Collectors.toList())));
        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> ride : repository.findAllRides()) {
            long id = number(ride, "sourceRideId");
            Map<String, Object> item = new LinkedHashMap<>();
            for (String key : List.of("sourceRideId", "parkId", "name", "landId", "landName")) {
                item.put(key, ride.get(key));
            }
            item.put("latest", latest.containsKey(id) ? latestWithFreshness(convertPoint(latest.get(id))) : null);
            List<Point> valid = day.getOrDefault(id, List.of()).stream().filter(this::isAccepted).toList();
            item.put("todayAverage", calculateAverage(valid));
            item.put("todaySamples", valid.size());
            item.put("coverage", coverage(valid.size(), expectedSamples(dayStart(today), dayStart(today.plusDays(1)), Instant.now())));
            output.add(item);
        }
        return output;
    }

    public Map<String, Object> getLatest(long id) {
        ensureRideExists(id);
        return repository.findLatestSnapshots().stream().filter(row -> number(row, "sourceRideId") == id).findFirst().map(row -> latestWithFreshness(convertPoint(row))).orElseGet(LinkedHashMap::new);
    }

    private Map<String, Object> latestWithFreshness(Point point) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.getStaleMinutes()));
        boolean stale = point.isStale() || point.sourceUpdatedAt() == null || point.sourceUpdatedAt().isBefore(cutoff) || point.collectedAt().isBefore(cutoff);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collectionSlot", point.collectionSlot());
        result.put("collectedAt", point.collectedAt());
        result.put("sourceUpdatedAt", point.sourceUpdatedAt());
        result.put("waitMinutes", point.waitMinutes());
        result.put("isOpen", point.isOpen());
        result.put("isValid", point.isValid());
        result.put("isStale", stale);
        result.put("usableForPlanning", point.isValid() && !stale && Boolean.TRUE.equals(point.isOpen()));
        return result;
    }

    public Map<String, Object> getDaily(long id, LocalDate date) {
        ensureRideExists(id);
        List<Point> points = pointsForDate(id, date);
        List<Point> valid = points.stream().filter(this::isAccepted).toList();
        List<Hour> hours = calculateHours(points, date);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceRideId", id);
        result.put("date", date);
        result.put("timezone", properties.getZone());
        result.put("averageWait", calculateAverage(valid));
        result.put("sampleCount", valid.size());
        result.put("observedCount", points.size());
        int expected = expectedSamples(dayStart(date), dayStart(date.plusDays(1)), Instant.now());
        result.put("expectedSamples", expected);
        result.put("coverage", coverage(valid.size(), expected));
        result.put("closedCount", points.stream().filter(point -> Boolean.FALSE.equals(point.isOpen())).count());
        result.put("staleCount", points.stream().filter(Point::isStale).count());
        result.put("invalidCount", points.stream().filter(point -> !point.isValid()).count());
        result.put("minWait", valid.stream().min(Comparator.comparingInt(Point::waitMinutes)).orElse(null));
        result.put("maxWait", valid.stream().max(Comparator.comparingInt(Point::waitMinutes)).orElse(null));
        result.put("quietHour", hours.stream().filter(hour -> hour.averageWait() != null).min(Comparator.comparingDouble(Hour::averageWait)).orElse(null));
        result.put("peakHour", hours.stream().filter(hour -> hour.averageWait() != null).max(Comparator.comparingDouble(Hour::averageWait)).orElse(null));
        result.put("hours", hours);
        result.put("timeline", points);
        result.put("updatedAt", points.stream().map(Point::collectedAt).max(Instant::compareTo).orElse(null));
        result.put("provisional", date.equals(LocalDate.now(displayZone())));
        return result;
    }

    public List<Hour> getHourly(long id, LocalDate date) {
        ensureRideExists(id);
        return calculateHours(pointsForDate(id, date), date);
    }

    public List<Hour> calculateHours(List<Point> points, LocalDate date) {
        Map<Integer, List<Point>> groups = points.stream().collect(Collectors.groupingBy(point -> point.collectionSlot().atZone(displayZone()).getHour()));
        List<Hour> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            List<Point> observed = groups.getOrDefault(hour, List.of());
            List<Point> valid = observed.stream().filter(this::isAccepted).toList();
            Instant start = date.atTime(hour, 0).atZone(displayZone()).toInstant();
            int expected = expectedSamples(start, start.plusSeconds(3600), Instant.now());
            hours.add(new Hour(hour, calculateAverage(valid), valid.size(), observed.size(), expected, coverage(valid.size(), expected), valid.size() < properties.getLowCoverageSamples()));
        }
        return hours;
    }

    public List<HistoricalHour> aggregateHistoricalHours(Map<LocalDate, List<Hour>> days) {
        List<HistoricalHour> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            int selected = hour;
            List<Hour> matches = days.values().stream().flatMap(List::stream).filter(item -> item.hour() == selected && item.averageWait() != null).toList();
            List<Double> values = matches.stream().map(Hour::averageWait).sorted().toList();
            result.add(new HistoricalHour(hour, values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()), values.isEmpty() ? null : values.get((int) Math.ceil(values.size() * .75) - 1), values.size(), matches.stream().mapToInt(Hour::sampleCount).sum(), values.isEmpty() ? "INSUFFICIENT" : values.size() >= 7 && matches.stream().noneMatch(Hour::lowCoverage) ? "HIGH" : values.size() >= 3 && matches.stream().noneMatch(Hour::lowCoverage) ? "MEDIUM" : "LOW"));
        }
        return result;
    }

    public Map<String, Object> getPatterns(long id, LocalDate from, LocalDate to) {
        ensureRideExists(id);
        validateRange(from, to, 90);
        List<Point> points = repository.findSnapshots(dayStart(from), dayStart(to.plusDays(1)), id).stream().map(this::convertPoint).toList();
        Map<LocalDate, List<Point>> days = points.stream().collect(Collectors.groupingBy(point -> point.collectionSlot().atZone(displayZone()).toLocalDate(), TreeMap::new, Collectors.toList()));
        Map<LocalDate, List<Hour>> dailyHours = new LinkedHashMap<>();
        List<Map<String, Object>> comparisons = new ArrayList<>();
        days.forEach((date, samples) -> {
            List<Point> valid = samples.stream().filter(this::isAccepted).toList();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("weekday", date.getDayOfWeek());
            day.put("averageWait", calculateAverage(valid));
            day.put("sampleCount", valid.size());
            day.put("coverage", coverage(valid.size(), expectedSamples(dayStart(date), dayStart(date.plusDays(1)), Instant.now())));
            comparisons.add(day);
            if (!date.isAfter(LocalDate.now(displayZone()))) {
                dailyHours.put(date, calculateHours(samples, date));
            }
        });
        List<HistoricalHour> allDays = aggregateHistoricalHours(dailyHours);
        Map<String, List<HistoricalHour>> weekdays = new LinkedHashMap<>();
        for (DayOfWeek weekday : DayOfWeek.values()) {
            weekdays.put(weekday.name(), aggregateHistoricalHours(dailyHours.entrySet().stream().filter(entry -> entry.getKey().getDayOfWeek() == weekday).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        }
        List<Map<String, Object>> rising = new ArrayList<>();
        for (int hour = 1; hour < 24; hour++) {
            int currentHour = hour;
            List<Double> differences = dailyHours.values().stream().filter(items -> items.get(currentHour).averageWait() != null && items.get(currentHour - 1).averageWait() != null).map(items -> items.get(currentHour).averageWait() - items.get(currentHour - 1).averageWait()).toList();
            double increase = differences.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (!differences.isEmpty() && increase >= 10) {
                rising.add(Map.of("hour", hour, "increaseMinutes", round(increase), "daysCount", differences.size()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceRideId", id);
        result.put("from", from);
        result.put("to", to);
        result.put("timezone", properties.getZone());
        result.put("daysCount", dailyHours.size());
        result.put("sampleCount", points.stream().filter(this::isAccepted).count());
        result.put("updatedAt", points.stream().map(Point::collectedAt).max(Instant::compareTo).orElse(null));
        result.put("allDays", allDays);
        result.put("weekdays", weekdays);
        result.put("days", comparisons);
        result.put("risingHours", rising);
        result.put("afterObservedOpening", calculateRelativeHours(days));
        result.put("openingBasis", "First fresh closed-to-open transition for the ride with gap <=15 minutes; not official park opening. Missing transitions excluded.");
        result.put("method", "Equal weight per observed day-hour, including partial current day. Uses all available valid readings, even one or two days; low coverage lowers confidence. Not a guaranteed forecast.");
        return result;
    }

    private List<Map<String, Object>> calculateRelativeHours(Map<LocalDate, List<Point>> days) {
        Map<Integer, List<Double>> means = new TreeMap<>();
        Map<Integer, Integer> counts = new HashMap<>();
        days.forEach((date, points) -> {
            if (date.isAfter(LocalDate.now(displayZone()))) {
                return;
            }
            Instant opening = null;
            for (int index = 1; index < points.size(); index++) {
                Point previous = points.get(index - 1);
                Point current = points.get(index);
                if (Boolean.FALSE.equals(previous.isOpen()) && previous.isValid() && !previous.isStale() && isAccepted(current) && Duration.between(previous.collectionSlot(), current.collectionSlot()).toMinutes() <= 15) {
                    opening = current.collectionSlot();
                    break;
                }
            }
            if (opening == null) {
                return;
            }
            Instant start = opening;
            points.stream().filter(this::isAccepted).filter(point -> !point.collectionSlot().isBefore(start)).collect(Collectors.groupingBy(point -> (int) Duration.between(start, point.collectionSlot()).toHours())).forEach((hour, samples) -> {
                if (!samples.isEmpty()) {
                    means.computeIfAbsent(hour, key -> new ArrayList<>()).add(calculateAverage(samples));
                    counts.merge(hour, samples.size(), Integer::sum);
                }
            });
        });
        List<Map<String, Object>> result = new ArrayList<>();
        means.forEach((hour, values) -> result.add(Map.of("hoursAfterObservedOpening", hour, "averageWait", round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()), "daysCount", values.size(), "sampleCount", counts.get(hour))));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlanningInsights(long id, LocalDate targetDate, int arrivalHour, int durationHours) {
        if (arrivalHour < 0 || arrivalHour > 23 || durationHours < 1 || arrivalHour + durationHours > 24) {
            throw new ApiException("Arrival hour must be 0–23 and duration must fit within the same local day");
        }
        LocalDate today = LocalDate.now(displayZone());
        Map<String, Object> patterns = getPatterns(id, today.minusDays(60), today);
        Map<String, List<HistoricalHour>> weekdays = (Map<String, List<HistoricalHour>>) patterns.get("weekdays");
        List<HistoricalHour> matching = weekdays.get(targetDate.getDayOfWeek().name());
        List<HistoricalHour> all = (List<HistoricalHour>) patterns.get("allDays");
        List<Map<String, Object>> windows = new ArrayList<>();
        for (int hour = arrivalHour; hour < arrivalHour + durationHours; hour++) {
            HistoricalHour weekday = matching.get(hour);
            HistoricalHour selected = weekday.daysCount() >= 2 ? weekday : all.get(hour);
            Map<String, Object> window = new LinkedHashMap<>();
            window.put("hour", hour);
            window.put("averageWait", selected.averageWait());
            window.put("p75DailyMean", selected.p75DailyMean());
            window.put("daysCount", selected.daysCount());
            window.put("sampleCount", selected.sampleCount());
            window.put("confidence", selected.confidence());
            window.put("basis", weekday.daysCount() >= 2 ? "SAME_WEEKDAY" : "ALL_DAYS");
            window.put("usable", selected.daysCount() >= 1);
            windows.add(window);
        }
        List<Map<String, Object>> ranked = windows.stream().filter(window -> Boolean.TRUE.equals(window.get("usable"))).sorted(Comparator.comparingDouble(window -> ((Number) window.get("averageWait")).doubleValue())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceRideId", id);
        result.put("targetDate", targetDate);
        result.put("timezone", properties.getZone());
        result.put("windows", windows);
        result.put("recommendedWindow", ranked.isEmpty() ? null : ranked.get(0));
        result.put("avoidWindow", ranked.isEmpty() ? null : ranked.get(ranked.size() - 1));
        result.put("risingHours", patterns.get("risingHours"));
        result.put("afterObservedOpening", patterns.get("afterObservedOpening"));
        result.put("latest", getLatest(id));
        result.put("historicalDataSufficient", !ranked.isEmpty());
        result.put("updatedAt", patterns.get("updatedAt"));
        result.put("note", "Uses available observations, even one day. LOW confidence is provisional. Verify current status, official opening, walking and group constraints in Sikka.");
        return result;
    }

    public Map<String, Object> getCollectorStatus() {
        LocalDate today = LocalDate.now(displayZone());
        Map<String, Object> totals = repository.findCollectionTotals();
        List<Map<String, Object>> recent = repository.findRecentRuns();
        List<Map<String, Object>> runs = repository.findRunsInRange(dayStart(today), dayStart(today.plusDays(1)));
        int successes = (int) runs.stream().filter(run -> "SUCCEEDED".equals(run.get("status"))).count();
        int expected = expectedSamples(dayStart(today), dayStart(today.plusDays(1)), Instant.now());
        List<Map<String, Object>> rides = listRides();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverTime", Instant.now());
        result.put("timezone", properties.getZone());
        result.put("enabled", properties.isCollectorEnabled());
        result.put("intervalMinutes", properties.getIntervalMinutes());
        result.put("collectionWindowEnabled", properties.isCollectionWindowEnabled());
        result.put("collectionStartHour", properties.getCollectionStartHour());
        result.put("collectionEndHour", properties.getCollectionEndHour());
        result.put("withinCollectionWindow", properties.isWithinCollectionWindow(Instant.now()));
        result.put("staleMinutes", properties.getStaleMinutes());
        result.put("parkId", properties.getParkId());
        result.put("totalSnapshots", totals.get("totalSnapshots"));
        for (String key : List.of("firstReadingAt", "lastSuccessAt", "lastFailureAt")) {
            result.put(key, instantValue(totals.get(key)));
        }
        result.put("lastError", recent.stream().filter(run -> "FAILED".equals(run.get("status"))).findFirst().orElse(null));
        result.put("todaySuccessfulRuns", successes);
        result.put("todayExpectedRuns", expected);
        result.put("todayCoverage", coverage(successes, expected));
        result.put("todayMissingRuns", Math.max(0, expected - runs.size()));
        result.put("todayFailedRuns", runs.size() - successes);
        result.put("totalRides", rides.size());
        result.put("freshOpenRides", rides.stream().filter(ride -> ride.get("latest") instanceof Map<?, ?> latest && Boolean.TRUE.equals(latest.get("usableForPlanning"))).count());
        result.put("recentRuns", recent);
        return result;
    }

    public String exportSnapshots(LocalDate from, LocalDate to, Long id) {
        validateRange(from, to, 31);
        if (id != null) {
            ensureRideExists(id);
        }
        StringBuilder csv = new StringBuilder("\uFEFFsource_ride_id,name,land_name,collection_slot_utc,collected_at_utc,source_updated_at_utc,wait_minutes,is_open,is_stale,is_valid\r\n");
        for (Map<String, Object> row : repository.findSnapshots(dayStart(from), dayStart(to.plusDays(1)), id)) {
            Point point = convertPoint(row);
            csv.append(number(row, "sourceRideId")).append(',').append(csvCell(row.get("name"))).append(',').append(csvCell(row.get("landName"))).append(',').append(point.collectionSlot()).append(',').append(point.collectedAt()).append(',').append(point.sourceUpdatedAt() == null ? "" : point.sourceUpdatedAt()).append(',').append(point.waitMinutes() == null ? "" : point.waitMinutes()).append(',').append(point.isOpen() == null ? "" : point.isOpen()).append(',').append(point.isStale()).append(',').append(point.isValid()).append("\r\n");
        }
        return csv.toString();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.stripLeading().matches("^[=+@\\-].*")) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public void validateRange(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= maxDays) {
            throw new ApiException("Date range must be ordered and no longer than " + maxDays + " days");
        }
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(displayZone()).toInstant();
    }

    private List<Point> pointsForDate(long id, LocalDate date) {
        return repository.findSnapshots(dayStart(date), dayStart(date.plusDays(1)), id).stream().map(this::convertPoint).toList();
    }

    public boolean isAccepted(Point point) {
        return properties.isWithinCollectionWindow(point.collectionSlot()) && point.isValid() && !point.isStale() && Boolean.TRUE.equals(point.isOpen()) && point.waitMinutes() != null && point.waitMinutes() >= 0;
    }

    public Double calculateAverage(List<Point> points) {
        return points.isEmpty() ? null : round(points.stream().mapToInt(Point::waitMinutes).average().orElseThrow());
    }

    public int expectedSamples(Instant start, Instant end, Instant now) {
        Instant limit = now.isBefore(end) ? now : end.minusNanos(1);
        if (limit.isBefore(start)) {
            return 0;
        }
        long seconds = properties.getIntervalMinutes() * 60L;
        long firstSlot = Math.floorDiv(start.getEpochSecond() + seconds - 1, seconds) * seconds;
        int expected = 0;
        for (long slot = firstSlot; slot <= limit.getEpochSecond(); slot += seconds) {
            if (properties.isWithinCollectionWindow(Instant.ofEpochSecond(slot))) {
                expected++;
            }
        }
        return expected;
    }

    private double coverage(int count, int expected) {
        return expected == 0 ? 0 : Math.min(100.0, round(count * 100.0 / expected));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void ensureRideExists(long id) {
        if (repository.findAllRides().stream().noneMatch(ride -> number(ride, "sourceRideId") == id)) {
            throw new ApiException("Ride not found; collection may not have started yet");
        }
    }

    public Point convertPoint(Map<String, Object> row) {
        Object wait = row.get("waitMinutes");
        return new Point(instantValue(row.get("collectionSlot")), instantValue(row.get("collectedAt")), instantValue(row.get("sourceUpdatedAt")), wait instanceof Number number ? number.intValue() : null, nullableBoolean(row.get("isOpen")), Boolean.TRUE.equals(nullableBoolean(row.get("isStale"))), Boolean.TRUE.equals(nullableBoolean(row.get("isValid"))));
    }

    private Boolean nullableBoolean(Object value) {
        return value == null ? null : value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0;
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Instant instantValue(Object value) {
        return value == null ? null : value instanceof Timestamp timestamp ? timestamp.toInstant() : value instanceof LocalDateTime dateTime ? dateTime.toInstant(ZoneOffset.UTC) : (Instant) value;
    }
}
