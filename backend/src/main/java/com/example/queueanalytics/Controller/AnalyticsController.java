package com.example.queueanalytics.Controller;

import com.example.queueanalytics.Service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/rides")
    public ResponseEntity<List<Map<String, Object>>> getRides() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analyticsService.listRides());
    }

    @GetMapping("/rides/{sourceRideId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestReading(@PathVariable long sourceRideId) {
        return ResponseEntity.ok().body(analyticsService.getLatest(sourceRideId));
    }

    @GetMapping("/rides/{sourceRideId}/daily")
    public ResponseEntity<Map<String, Object>> getDailyAnalysis(@PathVariable long sourceRideId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().body(analyticsService.getDaily(sourceRideId, date));
    }

    @GetMapping("/rides/{sourceRideId}/hourly")
    public ResponseEntity<List<AnalyticsService.Hour>> getHourlyAnalysis(@PathVariable long sourceRideId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok().body(analyticsService.getHourly(sourceRideId, date));
    }

    @GetMapping("/rides/{sourceRideId}/patterns")
    public ResponseEntity<Map<String, Object>> getHistoricalPatterns(@PathVariable long sourceRideId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().body(analyticsService.getPatterns(sourceRideId, from, to));
    }

    @GetMapping("/rides/{sourceRideId}/planning-insights")
    public ResponseEntity<Map<String, Object>> getPlanningInsights(@PathVariable long sourceRideId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(defaultValue = "12") int arrivalHour, @RequestParam(defaultValue = "8") int durationHours) {
        return ResponseEntity.ok().body(analyticsService.getPlanningInsights(sourceRideId, date, arrivalHour, durationHours));
    }

    @GetMapping("/collector/status")
    public ResponseEntity<Map<String, Object>> getCollectorStatus() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analyticsService.getCollectorStatus());
    }

    @GetMapping(value = "/export/snapshots", produces = "text/csv")
    public ResponseEntity<String> exportSnapshots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required = false) Long sourceRideId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=queue-snapshots.csv").contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)).body(analyticsService.exportSnapshots(from, to, sourceRideId));
    }
}
