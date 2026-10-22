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
@Table(name = "collection_run")
public class CollectionRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant collectionSlot;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant startedAt;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private Instant finishedAt;
    private String status;
    private Integer httpStatus;
    private int ridesCount;
    private int savedCount;
    private String errorMessage;
}
