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

