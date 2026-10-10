CREATE TABLE environment_threshold (
  threshold_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  metric_code VARCHAR(32) NOT NULL,
  target_min DECIMAL(12, 4) NOT NULL,
  target_max DECIMAL(12, 4) NOT NULL,
  alert_min DECIMAL(12, 4) NOT NULL,
  alert_max DECIMAL(12, 4) NOT NULL,
  control_action VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_environment_threshold (greenhouse_id, metric_code),
  CONSTRAINT fk_threshold_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO environment_threshold (
  greenhouse_id,
  metric_code,
  target_min,
  target_max,
  alert_min,
  alert_max,
  control_action,
  enabled
) VALUES
  (1, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (1, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (1, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (1, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1);
