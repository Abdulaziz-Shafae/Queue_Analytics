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
@Table(name = "ride")
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer parkId;
    private Long sourceRideId;
    private String name;
    private Long landId;
    private String landName;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant firstSeenAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant lastSeenAt;
}
