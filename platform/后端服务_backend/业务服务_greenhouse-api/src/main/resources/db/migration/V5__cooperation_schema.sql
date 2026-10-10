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

