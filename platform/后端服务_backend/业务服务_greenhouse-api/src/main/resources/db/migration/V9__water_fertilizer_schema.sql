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
