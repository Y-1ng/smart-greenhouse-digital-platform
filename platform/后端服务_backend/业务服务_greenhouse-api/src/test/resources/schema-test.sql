DROP TABLE IF EXISTS sensor_reading;
DROP TABLE IF EXISTS iot_telemetry;
DROP TABLE IF EXISTS environment_threshold;
DROP TABLE IF EXISTS alert_record;
DROP TABLE IF EXISTS environment_cycle;
DROP TABLE IF EXISTS control_command;
DROP TABLE IF EXISTS water_fertilizer_plan;
DROP TABLE IF EXISTS scan_record;
DROP TABLE IF EXISTS trace_node;
DROP TABLE IF EXISTS trace_code;
DROP TABLE IF EXISTS certificate_sign_log;
DROP TABLE IF EXISTS certificate;
DROP TABLE IF EXISTS quality_report;
DROP TABLE IF EXISTS dividend_record;
DROP TABLE IF EXISTS cooperation_share;
DROP TABLE IF EXISTS village;
DROP TABLE IF EXISTS crop_batch;
DROP TABLE IF EXISTS device;
DROP TABLE IF EXISTS greenhouse;
DROP TABLE IF EXISTS user_account;

DROP TABLE IF EXISTS external_sync_log;

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
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_account (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE greenhouse (
  greenhouse_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_code VARCHAR(64) NOT NULL UNIQUE,
  greenhouse_name VARCHAR(128) NOT NULL,
  area DECIMAL(10, 2),
  location VARCHAR(255),
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE device (
  device_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  device_code VARCHAR(64) NOT NULL UNIQUE,
  device_type VARCHAR(64) NOT NULL,
  online_status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
  operating_state VARCHAR(32) NOT NULL DEFAULT 'OFF',
  last_seen_at TIMESTAMP NULL,
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id)
);

CREATE TABLE environment_cycle (
  cycle_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  cycle_start TIMESTAMP NOT NULL,
  cycle_end TIMESTAMP NOT NULL,
  data_digest CHAR(64) NOT NULL,
  root_hash CHAR(64) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  chain_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (greenhouse_id, cycle_start, cycle_end),
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id)
);

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
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (greenhouse_id, metric_code),
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id)
);

CREATE TABLE sensor_reading (
  reading_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cycle_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  metric_code VARCHAR(32) NOT NULL,
  metric_value DECIMAL(12, 4) NOT NULL,
  unit VARCHAR(16),
  collected_at TIMESTAMP NOT NULL,
  FOREIGN KEY (cycle_id) REFERENCES environment_cycle(cycle_id),
  FOREIGN KEY (device_id) REFERENCES device(device_id)
);

CREATE TABLE iot_telemetry (
  telemetry_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  metric_code VARCHAR(32) NOT NULL,
  metric_value DECIMAL(12, 4) NOT NULL,
  unit VARCHAR(16),
  collected_at TIMESTAMP NOT NULL,
  raw_topic VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id),
  FOREIGN KEY (device_id) REFERENCES device(device_id)
);

CREATE TABLE crop_batch (
  batch_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  greenhouse_id BIGINT NOT NULL,
  crop_name VARCHAR(128) NOT NULL,
  variety_name VARCHAR(128),
  planting_date DATE,
  expected_harvest_date DATE,
  actual_harvest_date DATE NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'GROWING',
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id)
);

CREATE TABLE control_command (
  command_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  command_no VARCHAR(64) NOT NULL UNIQUE,
  greenhouse_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  command_type VARCHAR(64) NOT NULL,
  command_payload CLOB NOT NULL,
  operator_name VARCHAR(64) NOT NULL,
  timeout_seconds INT NOT NULL DEFAULT 30,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  failure_reason VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  dispatched_at TIMESTAMP NULL,
  attempt_started_at TIMESTAMP NULL,
  retry_count INT NOT NULL DEFAULT 0,
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id),
  FOREIGN KEY (device_id) REFERENCES device(device_id)
);

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
  planned_at TIMESTAMP NOT NULL,
  water_volume DECIMAL(10, 3) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
  operator_name VARCHAR(64) NULL,
  executed_at TIMESTAMP NULL,
  failure_reason VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id),
  FOREIGN KEY (batch_id) REFERENCES crop_batch(batch_id)
);

CREATE TABLE quality_report (
  report_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  institution_id VARCHAR(128),
  institution_name VARCHAR(255),
  report_no VARCHAR(128) NOT NULL UNIQUE,
  result_summary CLOB,
  file_url VARCHAR(512),
  content_hash CHAR(64) NOT NULL,
  tested_at TIMESTAMP,
  FOREIGN KEY (batch_id) REFERENCES crop_batch(batch_id)
);

CREATE TABLE certificate (
  certificate_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  certificate_no VARCHAR(128) NOT NULL UNIQUE,
  content_hash CHAR(64) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  issued_at TIMESTAMP NULL,
  revoked_at TIMESTAMP NULL,
  FOREIGN KEY (batch_id) REFERENCES crop_batch(batch_id)
);

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
  verified_at TIMESTAMP NOT NULL,
  FOREIGN KEY (certificate_id) REFERENCES certificate(certificate_id)
);

CREATE TABLE trace_code (
  trace_code_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  field_no VARCHAR(64) NOT NULL,
  product_code VARCHAR(128) NOT NULL UNIQUE,
  public_key_id VARCHAR(128) NOT NULL,
  signature_value VARCHAR(1024) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (batch_id) REFERENCES crop_batch(batch_id)
);

CREATE TABLE trace_node (
  node_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  field_no VARCHAR(64) NOT NULL,
  stage_type VARCHAR(64) NOT NULL,
  previous_hash CHAR(64) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_trace_node_field_time (field_no, created_at)
);

CREATE TABLE scan_record (
  scan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_code_id BIGINT NOT NULL,
  scan_time TIMESTAMP NOT NULL,
  scan_channel VARCHAR(64),
  ip_hash CHAR(64),
  risk_result VARCHAR(32) NOT NULL,
  chain_tx_hash CHAR(66) NULL,
  FOREIGN KEY (trace_code_id) REFERENCES trace_code(trace_code_id),
  INDEX idx_scan_code_time (trace_code_id, scan_time)
);

CREATE TABLE village (
  village_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  village_name VARCHAR(128) NOT NULL,
  land_area DECIMAL(12, 2),
  labor_count INT,
  capital_amount DECIMAL(14, 2),
  asset_description CLOB
);

CREATE TABLE cooperation_share (
  share_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  village_id BIGINT NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  share_ratio DECIMAL(8, 4) NOT NULL,
  effective_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL,
  FOREIGN KEY (village_id) REFERENCES village(village_id)
);

CREATE TABLE dividend_record (
  dividend_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  share_id BIGINT NOT NULL,
  period_name VARCHAR(64) NOT NULL,
  amount DECIMAL(14, 2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  confirmed_at TIMESTAMP NULL,
  FOREIGN KEY (share_id) REFERENCES cooperation_share(share_id)
);

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
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at TIMESTAMP NULL,
  acknowledged_by VARCHAR(64) NULL,
  FOREIGN KEY (greenhouse_id) REFERENCES greenhouse(greenhouse_id)
);
