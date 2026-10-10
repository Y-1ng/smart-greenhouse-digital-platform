CREATE TABLE user_account (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE greenhouse (
  greenhouse_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_code VARCHAR(64) NOT NULL UNIQUE,
  greenhouse_name VARCHAR(128) NOT NULL,
  area DECIMAL(10, 2),
  location VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE device (
  device_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  device_code VARCHAR(64) NOT NULL UNIQUE,
  device_type VARCHAR(64) NOT NULL,
  online_status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
  last_seen_at DATETIME NULL,
  CONSTRAINT fk_device_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE environment_cycle (
  cycle_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  cycle_start DATETIME NOT NULL,
  cycle_end DATETIME NOT NULL,
  data_digest CHAR(64) NOT NULL,
  root_hash CHAR(64) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  chain_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_environment_cycle (
    greenhouse_id,
    cycle_start,
    cycle_end
  ),
  CONSTRAINT fk_cycle_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sensor_reading (
  reading_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cycle_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  metric_code VARCHAR(32) NOT NULL,
  metric_value DECIMAL(12, 4) NOT NULL,
  unit VARCHAR(16),
  collected_at DATETIME NOT NULL,
  INDEX idx_sensor_cycle_metric (cycle_id, metric_code),
  CONSTRAINT fk_reading_cycle
    FOREIGN KEY (cycle_id) REFERENCES environment_cycle (cycle_id),
  CONSTRAINT fk_reading_device
    FOREIGN KEY (device_id) REFERENCES device (device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE crop_batch (
  batch_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  crop_name VARCHAR(128) NOT NULL,
  variety_name VARCHAR(128),
  planting_date DATE,
  expected_harvest_date DATE,
  actual_harvest_date DATE NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'GROWING',
  CONSTRAINT fk_crop_batch_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE control_command (
  command_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  command_no VARCHAR(64) NOT NULL UNIQUE,
  greenhouse_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  command_type VARCHAR(64) NOT NULL,
  command_payload JSON NOT NULL,
  operator_name VARCHAR(64) NOT NULL,
  timeout_seconds INT NOT NULL DEFAULT 30,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  failure_reason VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at DATETIME NULL,
  completed_at DATETIME NULL,
  INDEX idx_control_status (status, created_at),
  CONSTRAINT fk_command_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id),
  CONSTRAINT fk_command_device
    FOREIGN KEY (device_id) REFERENCES device (device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

