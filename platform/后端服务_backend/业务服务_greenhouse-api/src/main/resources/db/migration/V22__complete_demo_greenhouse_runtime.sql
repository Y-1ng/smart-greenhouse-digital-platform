-- 演示园区每座温室都需要独立设备和环境周期，避免切换温室后出现空图表、
-- 空设备列表或错误回退到 1 号温室数据。
INSERT INTO device (
  device_id,
  greenhouse_id,
  device_code,
  device_type,
  online_status,
  operating_state,
  last_seen_at
)
VALUES
  (10, 2, 'VALVE-A-02', 'VALVE', 'ONLINE', 'OFF', '2026-09-28 09:58:00'),
  (11, 2, 'CURTAIN-A-02', 'CURTAIN', 'ONLINE', 'ON', '2026-09-28 09:58:04'),
  (13, 3, 'MIST-B-01', 'MIST', 'ONLINE', 'OFF', '2026-09-28 09:57:48'),
  (14, 4, 'FAN-B-03', 'FAN', 'ONLINE', 'ON', '2026-09-28 09:58:08'),
  (17, 5, 'VALVE-C-01', 'VALVE', 'ONLINE', 'OFF', '2026-09-28 09:57:44'),
  (18, 6, 'LIGHT-C-02', 'GROW_LIGHT', 'ONLINE', 'ON', '2026-09-28 09:58:06'),
  (19, 6, 'MIST-C-02', 'MIST', 'ONLINE', 'OFF', '2026-09-28 09:57:50');

INSERT INTO environment_threshold (
  greenhouse_id,
  metric_code,
  target_min,
  target_max,
  alert_min,
  alert_max,
  control_action,
  enabled
)
VALUES
  (2, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (2, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (2, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (2, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1),
  (3, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (3, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (3, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (3, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1),
  (4, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (4, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (4, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (4, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1),
  (5, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (5, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (5, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (5, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1),
  (6, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (6, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (6, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (6, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1);

INSERT INTO environment_cycle (
  cycle_id,
  greenhouse_id,
  cycle_start,
  cycle_end,
  data_digest,
  root_hash,
  chain_status
)
VALUES
  (1101, 1, '2026-09-28 08:00:00', '2026-09-28 08:30:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1102, 1, '2026-09-28 08:35:00', '2026-09-28 09:05:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1103, 1, '2026-09-28 09:10:00', '2026-09-28 09:40:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1104, 1, '2026-09-28 09:45:00', '2026-09-28 10:15:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1105, 2, '2026-09-28 08:00:00', '2026-09-28 08:30:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1106, 2, '2026-09-28 08:35:00', '2026-09-28 09:05:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1107, 2, '2026-09-28 09:10:00', '2026-09-28 09:40:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1108, 2, '2026-09-28 09:45:00', '2026-09-28 10:15:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1109, 3, '2026-09-28 08:00:00', '2026-09-28 08:30:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1110, 3, '2026-09-28 08:35:00', '2026-09-28 09:05:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1111, 3, '2026-09-28 09:10:00', '2026-09-28 09:40:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1112, 3, '2026-09-28 09:45:00', '2026-09-28 10:15:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1113, 4, '2026-09-28 08:00:00', '2026-09-28 08:30:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1114, 4, '2026-09-28 08:35:00', '2026-09-28 09:05:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1115, 4, '2026-09-28 09:10:00', '2026-09-28 09:40:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1116, 4, '2026-09-28 09:45:00', '2026-09-28 10:15:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1117, 5, '2026-09-28 08:00:00', '2026-09-28 08:30:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1118, 5, '2026-09-28 08:35:00', '2026-09-28 09:05:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1119, 5, '2026-09-28 09:10:00', '2026-09-28 09:40:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1120, 5, '2026-09-28 09:45:00', '2026-09-28 10:15:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1121, 6, '2026-09-28 08:00:00', '2026-09-28 08:30:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1122, 6, '2026-09-28 08:35:00', '2026-09-28 09:05:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1123, 6, '2026-09-28 09:10:00', '2026-09-28 09:40:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING'),
  (1124, 6, '2026-09-28 09:45:00', '2026-09-28 10:15:00',
   REPEAT('d', 64),
   REPEAT('a', 64), 'PENDING');

INSERT INTO sensor_reading (
  cycle_id,
  device_id,
  metric_code,
  metric_value,
  unit,
  collected_at
)
SELECT
  c.cycle_id,
  (
    SELECT d.device_id
    FROM device d
    WHERE d.greenhouse_id = c.greenhouse_id
    ORDER BY d.device_id
    LIMIT 1
  ),
  m.metric_code,
  CASE m.metric_code
    WHEN 'temperature' THEN 20.5 + MOD(c.cycle_id, 5) * 0.6 + c.greenhouse_id * 0.2
    WHEN 'humidity' THEN 61.0 + MOD(c.cycle_id, 6) * 1.8 + c.greenhouse_id * 0.5
    WHEN 'light' THEN 15000 + MOD(c.cycle_id, 7) * 850 + c.greenhouse_id * 420
    WHEN 'soil_moisture' THEN 38.0 + MOD(c.cycle_id, 5) * 1.4 + c.greenhouse_id * 0.6
  END,
  m.unit,
  c.cycle_end
FROM environment_cycle c
JOIN (
  SELECT 'temperature' AS metric_code, 'C' AS unit
  UNION ALL SELECT 'humidity', '%'
  UNION ALL SELECT 'light', 'lx'
  UNION ALL SELECT 'soil_moisture', '%'
) m
WHERE c.cycle_id BETWEEN 1101 AND 1124;
