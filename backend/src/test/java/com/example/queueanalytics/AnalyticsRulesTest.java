package com.example.queueanalytics;

import com.example.queueanalytics.Config.AnalyticsProperties;
import com.example.queueanalytics.DtoOut.SourceRideDtoOut;
import com.example.queueanalytics.Service.*;
import com.example.queueanalytics.Repository.AnalyticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsRulesTest {
    private final AnalyticsProperties properties = new AnalyticsProperties();
    private final AnalyticsService analytics = new AnalyticsService(mock(AnalyticsRepository.class), properties);
    private final Instant instant = Instant.parse("2026-10-20T12:00:00Z");

    private AnalyticsService.Point observation(int wait, Boolean open, boolean stale, boolean valid) {
        return new AnalyticsService.Point(instant, instant, instant, wait, open, stale, valid);
    }

    @Test
    void excludesClosedStaleAndInvalidButIncludesOpenZero() {
        List<AnalyticsService.Point> points = List.of(observation(0, true, false, true), observation(30, true, false, true), observation(0, false, false, true), observation(100, true, true, true), observation(-1, true, false, false));
        assertEquals(15.0, analytics.calculateAverage(points.stream().filter(analytics::isAccepted).toList()));
        assertNull(analytics.calculateAverage(List.of()));
    }

    @Test
    void localMidnightIsPreviousUtcDayAndFutureHoursHaveNoExpectedSamples() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        Instant start = date.atStartOfDay(analytics.displayZone()).toInstant();
        assertEquals(Instant.parse("2026-10-19T21:00:00Z"), start);
        assertEquals(288, analytics.expectedSamples(start, start.plusSeconds(86400), start.plusSeconds(90000)));
        assertEquals(0, analytics.expectedSamples(start, start.plusSeconds(3600), start.minusSeconds(1)));
        assertEquals(1, analytics.expectedSamples(start, start.plusSeconds(3600), start.plusSeconds(299)));
    }

    @Test
    void missingHoursStayNullRatherThanZero() {
        List<AnalyticsService.Hour> hours = analytics.calculateHours(List.of(observation(0, true, false, true)), LocalDate.of(2026, 10, 20));
        assertNull(hours.get(14).averageWait());
        assertEquals(0.0, hours.get(15).averageWait());
        assertTrue(hours.get(15).lowCoverage());
    }

    @Test
    void historicalDayMeansHaveEqualWeightAndTwoDaysRemainUsable() {
        AnalyticsService.Hour first = new AnalyticsService.Hour(15, 20.0, 12, 12, 12, 100, false);
        AnalyticsService.Hour second = new AnalyticsService.Hour(15, 40.0, 1, 1, 12, 8.3, true);
        List<AnalyticsService.HistoricalHour> result = analytics.aggregateHistoricalHours(Map.of(LocalDate.of(2026, 10, 19), List.of(first), LocalDate.of(2026, 10, 20), List.of(second)));
        assertEquals(30.0, result.get(15).averageWait());
        assertEquals(2, result.get(15).daysCount());
        assertEquals("LOW", result.get(15).confidence());
        assertEquals(40.0, result.get(15).p75DailyMean());
    }

    @Test
    void invalidAndOversizedDateRangesAreRejected() {
        assertThrows(RuntimeException.class, () -> analytics.validateRange(LocalDate.now(), LocalDate.now().minusDays(1), 90));
        assertThrows(RuntimeException.class, () -> analytics.validateRange(LocalDate.now().minusDays(90), LocalDate.now(), 90));
    }

    @Test
    void sourceParserReadsNestedAndTopLevelDeduplicatesAndKeepsInvalidWaitNull() throws Exception {
        SourceClient client = new SourceClient(properties, new ObjectMapper());
        String json = "{\"lands\":[{\"id\":2,\"name\":\"Land\",\"rides\":[{\"id\":1,\"name\":\"Ride\",\"is_open\":true,\"wait_time\":0,\"last_updated\":\"2026-10-20T12:00:00Z\"}]}],\"rides\":[{\"id\":1,\"name\":\"Duplicate\",\"is_open\":true,\"wait_time\":0,\"last_updated\":\"2026-10-20T12:00:00Z\"},{\"id\":3,\"name\":\"Missing wait\",\"is_open\":true,\"last_updated\":\"2026-10-20T11:00:00Z\"}]}";
        List<SourceRideDtoOut> rides = client.parseSourceRides(json, instant);
        assertEquals(2, rides.size());
        assertEquals("Land", rides.get(0).landName());
        assertTrue(rides.get(0).isValid());
        assertNull(rides.get(1).waitMinutes());
        assertFalse(rides.get(1).isValid());
        assertTrue(rides.get(1).isStale());
        assertThrows(Exception.class, () -> client.parseSourceRides("{}", instant));
        assertThrows(Exception.class, () -> client.parseSourceRides("{\"lands\":[],\"rides\":[]}", instant));
    }

    @Test
    void collectionSlotsAreStableAcrossRetries() {
        assertEquals(Instant.parse("2026-10-20T12:00:00Z"), CollectorService.calculateSlot(instant.plusSeconds(299), 5));
        assertEquals(instant.plusSeconds(300), CollectorService.calculateSlot(instant.plusSeconds(300), 5));
    }
}
