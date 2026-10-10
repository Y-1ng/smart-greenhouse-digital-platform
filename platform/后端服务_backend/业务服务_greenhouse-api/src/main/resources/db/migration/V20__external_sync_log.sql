-- 外部平台（京东农场、气象、短信预警）调用留痕：请求编号、结果、重试次数、耗时与错误原因。
-- 适配器可切换 mock/rest，两条路径都写同一张表，便于答辩时说明"外部依赖可替换且可观测"。
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
