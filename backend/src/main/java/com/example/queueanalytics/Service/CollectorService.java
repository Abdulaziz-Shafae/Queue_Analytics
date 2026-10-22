package com.example.queueanalytics.Service;

import com.example.queueanalytics.Config.AnalyticsProperties;
import com.example.queueanalytics.DtoOut.SourceRideDtoOut;
import com.example.queueanalytics.Repository.AnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectorService {
    private static final Logger logger = LoggerFactory.getLogger(CollectorService.class);
    private final AnalyticsRepository repository;
    private final SourceClient sourceClient;
    private final AnalyticsProperties properties;

    @Transactional
    public void collectCurrentSlot() {
        repository.lockCollector();
        Instant startedAt = Instant.now();
        Instant slot = calculateSlot(startedAt, properties.getIntervalMinutes());
        if (repository.hasCollectionSlot(slot)) {
            return;
        }
        List<SourceRideDtoOut> rides;
        try {
            rides = sourceClient.fetchSourceRides(startedAt);
        } catch (Exception exception) {
            Integer httpStatus = exception instanceof SourceClient.SourceException sourceException ? sourceException.httpStatus : null;
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            repository.saveCollectionRun(slot, startedAt, Instant.now(), "FAILED", httpStatus, 0, 0, message.substring(0, Math.min(500, message.length())));
            logger.warn("Queue collection failed: {}", message);
            return;
        }
        Instant collectedAt = Instant.now();
        for (SourceRideDtoOut ride : rides) {
            repository.saveRideObservation(properties.getParkId(), ride, slot, collectedAt);
        }
        repository.saveCollectionRun(slot, startedAt, Instant.now(), "SUCCEEDED", 200, rides.size(), rides.size(), null);
        logger.info("Collected {} rides for slot {}", rides.size(), slot);
    }

    public static Instant calculateSlot(Instant instant, int intervalMinutes) {
        long seconds = intervalMinutes * 60L;
        return Instant.ofEpochSecond(Math.floorDiv(instant.getEpochSecond(), seconds) * seconds);
    }
}
