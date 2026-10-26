package com.example.queueanalytics.Config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "analytics")
public class AnalyticsProperties {
    @Positive
    private int parkId = 335;
    @NotBlank
    private String sourceUrl;
    private String zone = "Asia/Riyadh";
    @Min(5)
    @Max(60)
    private int intervalMinutes = 5;
    @Positive
    private int staleMinutes = 15;
    @Positive
    private int lowCoverageSamples = 6;
    private boolean collectorEnabled = true;
    private String corsOrigins = "";
    private boolean collectionWindowEnabled = false;
    private java.util.Set<java.time.DayOfWeek> collectionClosedDays = java.util.Set.of();
    @Min(0)
    @Max(23)
    private int collectionStartHour = 16;
    @Min(1)
    @Max(24)
    private int collectionEndHour = 24;

    public boolean isWithinCollectionWindow(java.time.Instant instant) {
        if (!collectionWindowEnabled) {
            return true;
        }
        java.time.ZonedDateTime local = instant.atZone(java.time.ZoneId.of(zone));
        if (collectionClosedDays.contains(local.getDayOfWeek())) {
            return false;
        }
        int hour = local.getHour();
        return collectionStartHour < collectionEndHour ? hour >= collectionStartHour && hour < collectionEndHour : hour >= collectionStartHour || hour < collectionEndHour;
    }
}
