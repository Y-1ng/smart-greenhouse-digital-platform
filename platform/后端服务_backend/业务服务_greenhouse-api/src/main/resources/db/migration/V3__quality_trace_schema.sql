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

