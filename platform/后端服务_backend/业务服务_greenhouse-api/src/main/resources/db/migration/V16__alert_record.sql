CREATE TABLE alert_record (
  alert_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  metric_code VARCHAR(32) NOT NULL,
  metric_value DECIMAL(12, 4) NOT NULL,
  alert_level VARCHAR(16) NOT NULL,
  alert_min DECIMAL(12, 4) NOT NULL,
  alert_max DECIMAL(12, 4) NOT NULL,
  message VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at DATETIME NULL,
  acknowledged_by VARCHAR(64) NULL,
  INDEX idx_alert_greenhouse_status (greenhouse_id, status, created_at),
  CONSTRAINT fk_alert_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
