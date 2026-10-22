package com.example.queueanalytics.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "wait_time_snapshot")
public class WaitTimeSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long rideId;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant collectionSlot;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant collectedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant sourceUpdatedAt;
    private Integer waitMinutes;
    private Boolean isOpen;
    private boolean isStale;
    private boolean isValid;
}
