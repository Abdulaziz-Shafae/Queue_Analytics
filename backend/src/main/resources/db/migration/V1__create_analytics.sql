CREATE TABLE ride (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 park_id INT NOT NULL,
 source_ride_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL,
 land_id BIGINT NULL,
 land_name VARCHAR(255) NULL,
 first_seen_at TIMESTAMP(6) NOT NULL,
 last_seen_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_ride_source UNIQUE (park_id, source_ride_id)
);
CREATE TABLE collection_run (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 collection_slot TIMESTAMP(6) NOT NULL,
 started_at TIMESTAMP(6) NOT NULL,
 finished_at TIMESTAMP(6) NULL,
 status VARCHAR(20) NOT NULL,
 http_status INT NULL,
 rides_count INT NOT NULL DEFAULT 0,
 saved_count INT NOT NULL DEFAULT 0,
 error_message VARCHAR(500) NULL,
 CONSTRAINT uk_run_slot UNIQUE (collection_slot)
);
CREATE TABLE wait_time_snapshot (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 ride_id BIGINT NOT NULL,
 collection_slot TIMESTAMP(6) NOT NULL,
 collected_at TIMESTAMP(6) NOT NULL,
 source_updated_at TIMESTAMP(6) NULL,
 wait_minutes INT NULL,
 is_open BOOLEAN NULL,
 is_stale BOOLEAN NOT NULL,
 is_valid BOOLEAN NOT NULL,
 CONSTRAINT fk_snapshot_ride FOREIGN KEY (ride_id) REFERENCES ride(id),
 CONSTRAINT uk_snapshot_slot UNIQUE (ride_id, collection_slot)
);
CREATE INDEX ix_snapshot_time ON wait_time_snapshot(collection_slot, ride_id);
CREATE TABLE collector_lock (id INT PRIMARY KEY);
INSERT INTO collector_lock(id) VALUES (1);
