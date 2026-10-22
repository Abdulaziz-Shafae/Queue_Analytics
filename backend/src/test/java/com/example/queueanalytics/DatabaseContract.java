package com.example.queueanalytics;

import com.example.queueanalytics.Config.AnalyticsProperties;
import com.example.queueanalytics.DtoOut.SourceRideDtoOut;
import com.example.queueanalytics.Service.*;
import com.example.queueanalytics.Repository.AnalyticsRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

abstract class DatabaseContract {
    @Autowired
    CollectorService collector;
    @Autowired
    AnalyticsService analytics;
    @Autowired
    AnalyticsRepository repository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AnalyticsProperties properties;
    @MockitoBean
    SourceClient source;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM wait_time_snapshot");
        jdbc.update("DELETE FROM ride");
        jdbc.update("DELETE FROM collection_run");
        reset(source);
    }

    private SourceRideDtoOut ride(Instant time) {
        return new SourceRideDtoOut(16289, "Falcons Flight", null, null, true, 20, time, true, false);
    }

    @Test
    void savesRealRowsAndDuplicateSlotDoesNotRefetchOrDuplicate() throws Exception {
        when(source.fetchSourceRides(any())).thenAnswer(invocation -> List.of(ride(invocation.getArgument(0))));
        collector.collectCurrentSlot();
        collector.collectCurrentSlot();
        verify(source, times(1)).fetchSourceRides(any());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wait_time_snapshot", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM collection_run", Integer.class));
        assertEquals(20.0, analytics.getDaily(16289, LocalDate.now(analytics.displayZone())).get("averageWait"));
    }

    @Test
    void concurrentCollectorsSerializeAndOnlyOneSaves() throws Exception {
        when(source.fetchSourceRides(any())).thenAnswer(invocation -> List.of(ride(invocation.getArgument(0))));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> a = CompletableFuture.runAsync(collector::collectCurrentSlot, executor);
            CompletableFuture<Void> b = CompletableFuture.runAsync(collector::collectCurrentSlot, executor);
            CompletableFuture.allOf(a, b).get(20, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM collection_run", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wait_time_snapshot", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sourceFailureIsRecordedWithoutInventingClosures() throws Exception {
        when(source.fetchSourceRides(any())).thenThrow(new SourceClient.SourceException(503, "Source HTTP 503"));
        collector.collectCurrentSlot();
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM collection_run", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wait_time_snapshot", Integer.class));
        assertEquals(503, ((Number) repository.findRecentRuns().get(0).get("httpStatus")).intValue());
    }

    @Test
    void failedWriteRollsBackWholeCycleAndCanBeRetried() throws Exception {
        SourceRideDtoOut invalid = new SourceRideDtoOut(2, "x".repeat(300), null, null, true, 20, Instant.now(), true, false);
        when(source.fetchSourceRides(any())).thenAnswer(invocation -> List.of(ride(invocation.getArgument(0)), invalid));
        assertThrows(RuntimeException.class, collector::collectCurrentSlot);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wait_time_snapshot", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM collection_run", Integer.class));
        when(source.fetchSourceRides(any())).thenAnswer(invocation -> List.of(ride(invocation.getArgument(0))));
        collector.collectCurrentSlot();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wait_time_snapshot", Integer.class));
    }

    @Test
    void twoDaysOfHistoryProducePlanningWindowAndCsvPreservesSourceTime() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(analytics.displayZone());
        for (int daysAgo = 1; daysAgo <= 2; daysAgo++) {
            Instant slot = today.minusDays(daysAgo).atTime(15, 0).atZone(analytics.displayZone()).toInstant();
            repository.saveRideObservation(335, ride(slot), slot, slot);
        }
        Map<String, Object> insight = analytics.getPlanningInsights(16289, today, 15, 1);
        assertEquals(true, insight.get("historicalDataSufficient"));
        assertNotNull(insight.get("recommendedWindow"));
        String csv = analytics.exportSnapshots(today.minusDays(2), today, 16289L);
        assertTrue(csv.contains("Falcons Flight"));
        assertTrue(csv.contains("source_updated_at_utc"));
        assertEquals(2, analytics.getPatterns(16289, today.minusDays(2), today).get("daysCount"));
        assertEquals(true, analytics.getLatest(16289).get("isStale"));
    }
}
