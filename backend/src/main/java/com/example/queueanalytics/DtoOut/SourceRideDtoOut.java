package com.example.queueanalytics.DtoOut;

import java.time.Instant;

public record SourceRideDtoOut(long sourceRideId, String name, Long landId, String landName, Boolean isOpen, Integer waitMinutes, Instant sourceUpdatedAt, boolean isValid, boolean isStale) {
}
