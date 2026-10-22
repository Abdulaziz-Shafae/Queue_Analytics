package com.example.queueanalytics.Service;

import com.example.queueanalytics.Config.AnalyticsProperties;
import com.example.queueanalytics.DtoOut.SourceRideDtoOut;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

@Service
public class SourceClient {
    private final AnalyticsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public SourceClient(AnalyticsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<SourceRideDtoOut> fetchSourceRides(Instant collectedAt) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getSourceUrl())).timeout(Duration.ofSeconds(12)).header("Accept", "application/json").header("User-Agent", "Sikka-Queue-Analytics/1.0").GET().build();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new SourceException(response.statusCode(), "Source HTTP " + response.statusCode());
                }
                return parseSourceRides(response.body(), collectedAt);
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
                if (attempt == 2 || exception instanceof SourceException sourceException && sourceException.httpStatus < 500 && sourceException.httpStatus != 429) {
                    throw exception;
                }
                Thread.sleep(1000L * (attempt + 1));
            }
        }
        throw new IllegalStateException("Source retries exhausted");
    }

    public List<SourceRideDtoOut> parseSourceRides(String body, Instant collectedAt) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject() || !root.path("lands").isArray() || !root.path("rides").isArray()) {
            throw new IllegalArgumentException("Invalid source payload: expected lands and rides arrays");
        }
        Map<Long, SourceRideDtoOut> rides = new LinkedHashMap<>();
        for (JsonNode land : root.path("lands")) {
            if (!land.path("rides").isArray()) {
                throw new IllegalArgumentException("Invalid land rides array");
            }
            for (JsonNode ride : land.path("rides")) {
                addSourceRide(rides, ride, land.path("id").canConvertToLong() ? land.path("id").longValue() : null, land.path("name").asText(null), collectedAt);
            }
        }
        for (JsonNode ride : root.path("rides")) {
            addSourceRide(rides, ride, null, null, collectedAt);
        }
        if (rides.isEmpty()) {
            throw new IllegalArgumentException("Source returned no rides; keeping previous observations");
        }
        return new ArrayList<>(rides.values());
    }

    private void addSourceRide(Map<Long, SourceRideDtoOut> rides, JsonNode ride, Long landId, String landName, Instant collectedAt) {
        if (!ride.path("id").isIntegralNumber() || !ride.path("id").canConvertToLong() || ride.path("id").longValue() <= 0 || !ride.path("name").isTextual() || ride.path("name").asText().isBlank() || ride.path("name").asText().length() > 255) {
            throw new IllegalArgumentException("Source ride identity is invalid");
        }
        long id = ride.path("id").longValue();
        Integer wait = ride.path("wait_time").isIntegralNumber() && ride.path("wait_time").canConvertToInt() ? ride.path("wait_time").intValue() : null;
        Boolean open = ride.path("is_open").isBoolean() ? ride.path("is_open").booleanValue() : null;
        Instant updated = null;
        try {
            updated = Instant.parse(ride.path("last_updated").asText());
        } catch (DateTimeException ignored) {
            // Missing or malformed source time remains unknown, never replaced with fetch time.
        }
        boolean valid = wait != null && wait >= 0 && open != null && updated != null && !updated.isAfter(collectedAt.plusSeconds(60));
        boolean stale = updated == null || updated.isBefore(collectedAt.minus(Duration.ofMinutes(properties.getStaleMinutes())));
        SourceRideDtoOut value = new SourceRideDtoOut(id, ride.path("name").asText(), landId, landName, open, wait, updated, valid, stale);
        SourceRideDtoOut previous = rides.get(id);
        if (previous == null || previous.sourceUpdatedAt() == null || updated != null && updated.isAfter(previous.sourceUpdatedAt())) {
            rides.put(id, value);
        }
    }

    public static class SourceException extends RuntimeException {
        public final int httpStatus;

        public SourceException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }
    }
}
