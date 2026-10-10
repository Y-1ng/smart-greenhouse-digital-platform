-- AI 问答会话记录：按 session_id 分组保存，前端刷新后仍能恢复对话。
-- source 记录这条回复由谁产生：deepseek=真实大模型，local=本地兜底结论（未配置密钥或调用失败）。
CREATE TABLE ai_chat_message (
  message_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  operator_name VARCHAR(64) NULL,
  role VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'deepseek',
  model VARCHAR(64) NULL,
  latency_ms BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_chat_session_time (session_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
