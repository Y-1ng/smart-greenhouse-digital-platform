CREATE TABLE iot_telemetry (
  telemetry_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  metric_code VARCHAR(32) NOT NULL,
  metric_value DECIMAL(12, 4) NOT NULL,
  unit VARCHAR(16),
  collected_at DATETIME NOT NULL,
  raw_topic VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_iot_telemetry_device_time (device_id, collected_at),
  INDEX idx_iot_telemetry_greenhouse_metric (greenhouse_id, metric_code, collected_at),
  CONSTRAINT fk_iot_telemetry_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id),
  CONSTRAINT fk_iot_telemetry_device
    FOREIGN KEY (device_id) REFERENCES device (device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
