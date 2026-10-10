-- 控制指令下发闭环所需字段：
--   dispatched_at       最近一次下发到设备的时间
--   attempt_started_at  本轮计时起点（创建或重试时写入），超时判定以它为准
--   retry_count         已重试次数
ALTER TABLE control_command ADD COLUMN dispatched_at DATETIME NULL;
ALTER TABLE control_command ADD COLUMN attempt_started_at DATETIME NULL;
ALTER TABLE control_command ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

UPDATE control_command
SET attempt_started_at = created_at
WHERE attempt_started_at IS NULL;
