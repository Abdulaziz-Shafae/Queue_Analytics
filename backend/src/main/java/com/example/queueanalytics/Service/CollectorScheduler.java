package com.example.queueanalytics.Service;

import com.example.queueanalytics.Config.AnalyticsProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectorScheduler {
    private static final Logger logger = LoggerFactory.getLogger(CollectorScheduler.class);
    private final CollectorService collectorService;
    private final AnalyticsProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void collectOnStartup() {
        collectScheduledSlot();
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void collectScheduledSlot() {
        if (properties.isCollectorEnabled() && properties.isWithinCollectionWindow(java.time.Instant.now())) {
            try {
                collectorService.collectCurrentSlot();
            } catch (RuntimeException exception) {
                logger.error("Collection transaction failed; scheduler will retry", exception);
            }
        }
    }
}
