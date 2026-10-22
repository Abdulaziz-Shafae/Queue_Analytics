package com.example.queueanalytics.Repository;

import com.example.queueanalytics.DtoOut.SourceRideDtoOut;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {
    private final JdbcTemplate jdbcTemplate;

    public void lockCollector() {
        jdbcTemplate.queryForObject("SELECT id FROM collector_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public boolean hasCollectionSlot(Instant slot) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM collection_run WHERE collection_slot = ?", Integer.class, Timestamp.from(slot)) > 0;
    }

    public void saveCollectionRun(Instant slot, Instant startedAt, Instant finishedAt, String status, Integer httpStatus, int ridesCount, int savedCount, String errorMessage) {
        jdbcTemplate.update("INSERT INTO collection_run(collection_slot, started_at, finished_at, status, http_status, rides_count, saved_count, error_message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Timestamp.from(slot), Timestamp.from(startedAt), Timestamp.from(finishedAt), status, httpStatus, ridesCount, savedCount, errorMessage);
    }

    public void saveRideObservation(int parkId, SourceRideDtoOut ride, Instant slot, Instant collectedAt) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM ride WHERE park_id = ? AND source_ride_id = ?", (rs, row) -> rs.getLong(1), parkId, ride.sourceRideId());
        if (ids.isEmpty()) {
            jdbcTemplate.update("INSERT INTO ride(park_id, source_ride_id, name, land_id, land_name, first_seen_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?)", parkId, ride.sourceRideId(), ride.name(), ride.landId(), ride.landName(), Timestamp.from(collectedAt), Timestamp.from(collectedAt));
        } else {
            jdbcTemplate.update("UPDATE ride SET name = ?, land_id = COALESCE(?, land_id), land_name = COALESCE(?, land_name), last_seen_at = ? WHERE id = ?", ride.name(), ride.landId(), ride.landName(), Timestamp.from(collectedAt), ids.get(0));
        }
        Long rideId = jdbcTemplate.queryForObject("SELECT id FROM ride WHERE park_id = ? AND source_ride_id = ?", Long.class, parkId, ride.sourceRideId());
        jdbcTemplate.update("INSERT INTO wait_time_snapshot(ride_id, collection_slot, collected_at, source_updated_at, wait_minutes, is_open, is_stale, is_valid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rideId, Timestamp.from(slot), Timestamp.from(collectedAt), ride.sourceUpdatedAt() == null ? null : Timestamp.from(ride.sourceUpdatedAt()), ride.waitMinutes(), ride.isOpen(), ride.isStale(), ride.isValid());
    }

    public List<Map<String, Object>> findAllRides() {
        return jdbcTemplate.queryForList("SELECT id, park_id AS parkId, source_ride_id AS sourceRideId, name, land_id AS landId, land_name AS landName, first_seen_at AS firstSeenAt, last_seen_at AS lastSeenAt FROM ride ORDER BY name");
    }

    public List<Map<String, Object>> findSnapshots(Instant from, Instant to, Long sourceRideId) {
        String sql = "SELECT s.id, r.source_ride_id AS sourceRideId, r.name, r.land_name AS landName, s.collection_slot AS collectionSlot, s.collected_at AS collectedAt, s.source_updated_at AS sourceUpdatedAt, s.wait_minutes AS waitMinutes, s.is_open AS isOpen, s.is_stale AS isStale, s.is_valid AS isValid FROM wait_time_snapshot s JOIN ride r ON r.id = s.ride_id WHERE s.collection_slot >= ? AND s.collection_slot < ?";
        if (sourceRideId != null) {
            return jdbcTemplate.queryForList(sql + " AND r.source_ride_id = ? ORDER BY s.collection_slot, s.id", Timestamp.from(from), Timestamp.from(to), sourceRideId);
        }
        return jdbcTemplate.queryForList(sql + " ORDER BY s.collection_slot, s.id", Timestamp.from(from), Timestamp.from(to));
    }

    public List<Map<String, Object>> findLatestSnapshots() {
        return jdbcTemplate.queryForList("SELECT r.source_ride_id AS sourceRideId, s.collection_slot AS collectionSlot, s.collected_at AS collectedAt, s.source_updated_at AS sourceUpdatedAt, s.wait_minutes AS waitMinutes, s.is_open AS isOpen, s.is_stale AS isStale, s.is_valid AS isValid FROM wait_time_snapshot s JOIN ride r ON r.id = s.ride_id WHERE s.id = (SELECT ss.id FROM wait_time_snapshot ss WHERE ss.ride_id = r.id ORDER BY ss.collection_slot DESC LIMIT 1)");
    }

    public List<Map<String, Object>> findRecentRuns() {
        return jdbcTemplate.queryForList("SELECT id, collection_slot AS collectionSlot, started_at AS startedAt, finished_at AS finishedAt, status, http_status AS httpStatus, rides_count AS ridesCount, saved_count AS savedCount, error_message AS errorMessage FROM collection_run ORDER BY collection_slot DESC LIMIT 100");
    }

    public List<Map<String, Object>> findRunsInRange(Instant from, Instant to) {
        return jdbcTemplate.queryForList("SELECT collection_slot AS collectionSlot, status FROM collection_run WHERE collection_slot >= ? AND collection_slot < ?", Timestamp.from(from), Timestamp.from(to));
    }

    public Map<String, Object> findCollectionTotals() {
        return jdbcTemplate.queryForMap("SELECT (SELECT COUNT(*) FROM wait_time_snapshot) AS totalSnapshots, (SELECT MIN(collected_at) FROM wait_time_snapshot) AS firstReadingAt, (SELECT MAX(finished_at) FROM collection_run WHERE status = 'SUCCEEDED') AS lastSuccessAt, (SELECT MAX(finished_at) FROM collection_run WHERE status = 'FAILED') AS lastFailureAt");
    }
}
