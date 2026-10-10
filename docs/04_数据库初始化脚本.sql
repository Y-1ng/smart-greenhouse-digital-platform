-- 智慧温室大棚农产品全链条数字化管理平台
-- 数据库初始化脚本（MySQL 8.0）
-- 由后端 Flyway 迁移 V1~V16 的表结构汇总生成，仅包含结构，不含演示种子数据。
-- 实际启动时由 业务服务_greenhouse-api 自动执行 classpath:db/migration 下的迁移，本文件用于建库和结构核对。

CREATE DATABASE IF NOT EXISTS smart_greenhouse
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE smart_greenhouse;

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
  dispatched_at DATETIME NULL,
  attempt_started_at DATETIME NULL,
  retry_count INT NOT NULL DEFAULT 0,
  INDEX idx_control_status (status, created_at),
  CONSTRAINT fk_command_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id),
  CONSTRAINT fk_command_device
    FOREIGN KEY (device_id) REFERENCES device (device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE quality_report (
  report_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  institution_name VARCHAR(255),
  report_no VARCHAR(128) NOT NULL UNIQUE,
  result_summary TEXT,
  file_url VARCHAR(512),
  content_hash CHAR(64) NOT NULL,
  tested_at DATETIME,
  CONSTRAINT fk_quality_report_batch
    FOREIGN KEY (batch_id) REFERENCES crop_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE certificate (
  certificate_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  certificate_no VARCHAR(128) NOT NULL UNIQUE,
  content_hash CHAR(64) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  issued_at DATETIME NULL,
  revoked_at DATETIME NULL,
  CONSTRAINT fk_certificate_batch
    FOREIGN KEY (batch_id) REFERENCES crop_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE trace_code (
  trace_code_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  field_no VARCHAR(64) NOT NULL,
  product_code VARCHAR(128) NOT NULL UNIQUE,
  public_key_id VARCHAR(128) NOT NULL,
  signature_value VARCHAR(1024) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_trace_code_field (field_no),
  CONSTRAINT fk_trace_code_batch
    FOREIGN KEY (batch_id) REFERENCES crop_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE trace_node (
  node_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  field_no VARCHAR(64) NOT NULL,
  stage_type VARCHAR(64) NOT NULL,
  previous_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_trace_node_field_time (field_no, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE scan_record (
  scan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_code_id BIGINT NOT NULL,
  scan_time DATETIME NOT NULL,
  scan_channel VARCHAR(64),
  ip_hash CHAR(64),
  risk_result VARCHAR(32) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  INDEX idx_scan_code_time (trace_code_id, scan_time),
  CONSTRAINT fk_scan_record_trace_code
    FOREIGN KEY (trace_code_id) REFERENCES trace_code (trace_code_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE village (
  village_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  village_name VARCHAR(128) NOT NULL,
  land_area DECIMAL(12, 2),
  labor_count INT,
  capital_amount DECIMAL(14, 2),
  asset_description TEXT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE cooperation_share (
  share_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  village_id BIGINT NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  share_ratio DECIMAL(8, 4) NOT NULL,
  effective_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL,
  CONSTRAINT fk_share_village
    FOREIGN KEY (village_id) REFERENCES village (village_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE dividend_record (
  dividend_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  share_id BIGINT NOT NULL,
  period_name VARCHAR(64) NOT NULL,
  amount DECIMAL(14, 2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  confirmed_at DATETIME NULL,
  CONSTRAINT fk_dividend_share
    FOREIGN KEY (share_id) REFERENCES cooperation_share (share_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE certificate_sign_log (
  sign_log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(64) NOT NULL UNIQUE,
  certificate_id BIGINT NULL,
  certificate_no VARCHAR(128) NOT NULL,
  public_key_id VARCHAR(128) NOT NULL,
  key_id VARCHAR(128) NOT NULL,
  canonical_digest CHAR(64) NOT NULL,
  signature_value VARCHAR(1024) NOT NULL,
  verify_result TINYINT NOT NULL,
  failure_reason VARCHAR(255) NULL,
  verified_at DATETIME NOT NULL,
  CONSTRAINT fk_sign_log_certificate
    FOREIGN KEY (certificate_id) REFERENCES certificate (certificate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE quality_report
  ADD COLUMN institution_id VARCHAR(128) NULL AFTER batch_id;

CREATE TABLE water_fertilizer_plan (
  plan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_no VARCHAR(64) NOT NULL UNIQUE,
  greenhouse_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  recipe_code VARCHAR(64) NOT NULL,
  recipe_version VARCHAR(32) NOT NULL,
  ec_target DECIMAL(8, 3) NOT NULL,
  ph_target DECIMAL(6, 3) NOT NULL,
  zone_name VARCHAR(64) NOT NULL,
  planned_at DATETIME NOT NULL,
  water_volume DECIMAL(10, 3) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
  operator_name VARCHAR(64) NULL,
  executed_at DATETIME NULL,
  failure_reason VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_water_plan_greenhouse_time (greenhouse_id, planned_at),
  CONSTRAINT fk_water_plan_greenhouse
    FOREIGN KEY (greenhouse_id) REFERENCES greenhouse (greenhouse_id),
  CONSTRAINT fk_water_plan_batch
    FOREIGN KEY (batch_id) REFERENCES crop_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

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

ALTER TABLE device
  ADD COLUMN operating_state VARCHAR(32) NOT NULL DEFAULT 'OFF' AFTER online_status;

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

-- V20：外部平台调用留痕（京东农场 / 气象 / 短信）
CREATE TABLE external_sync_log (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  request_no VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 1,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  mode VARCHAR(16) NOT NULL DEFAULT 'mock',
  message VARCHAR(512) NULL,
  payload_summary VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_external_platform_time (platform, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
