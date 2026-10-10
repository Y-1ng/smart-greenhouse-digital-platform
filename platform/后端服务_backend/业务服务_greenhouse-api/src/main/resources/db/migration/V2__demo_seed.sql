INSERT INTO greenhouse
  (greenhouse_id, greenhouse_code, greenhouse_name, area, location, status)
VALUES
  (1, 'GH-001', '1号温室', 1280.00, '示范基地 A区', 'ACTIVE'),
  (2, 'GH-002', '2号温室', 1160.00, '示范基地 A区', 'ACTIVE'),
  (3, 'GH-003', '3号温室', 920.00, '示范基地 B区', 'WARNING'),
  (4, 'GH-004', '4号温室', 1210.00, '示范基地 B区', 'ACTIVE'),
  (5, 'GH-005', '5号温室', 980.00, '示范基地 C区', 'STANDBY'),
  (6, 'GH-006', '6号温室', 760.00, '示范基地 C区', 'ACTIVE');

INSERT INTO device
  (device_id, greenhouse_id, device_code, device_type, online_status, last_seen_at)
VALUES
  (1, 1, 'FAN-A-02', 'FAN', 'ONLINE', '2026-09-18 14:36:20'),
  (2, 1, 'VALVE-B-01', 'VALVE', 'ONLINE', '2026-09-18 14:36:18'),
  (3, 1, 'LIGHT-A-03', 'GROW_LIGHT', 'OFFLINE', '2026-09-18 14:31:05'),
  (4, 2, 'FAN-A-04', 'FAN', 'ONLINE', '2026-09-18 14:36:10'),
  (5, 3, 'LIGHT-B-02', 'GROW_LIGHT', 'ONLINE', '2026-09-18 14:34:42'),
  (6, 4, 'VALVE-B-03', 'VALVE', 'ONLINE', '2026-09-18 14:36:04'),
  (7, 5, 'MIST-C-01', 'MIST', 'ONLINE', '2026-09-18 14:33:51'),
  (8, 6, 'FAN-C-02', 'FAN', 'ONLINE', '2026-09-18 14:35:59');

INSERT INTO environment_cycle
  (cycle_id, greenhouse_id, cycle_start, cycle_end, data_digest, root_hash, chain_status)
VALUES
  (1001, 1, '2026-09-18 14:30:00', '2026-09-18 14:35:00',
   'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
   'CONFIRMED');

INSERT INTO sensor_reading
  (cycle_id, device_id, metric_code, metric_value, unit, collected_at)
VALUES
  (1001, 1, 'temperature', 22.6000, '℃', '2026-09-18 14:35:00'),
  (1001, 1, 'humidity', 68.4000, '%', '2026-09-18 14:35:00'),
  (1001, 1, 'light', 18500.0000, 'lx', '2026-09-18 14:35:00'),
  (1001, 1, 'soil_moisture', 43.2000, '%', '2026-09-18 14:35:00');

INSERT INTO crop_batch
  (batch_id, greenhouse_id, crop_name, variety_name, planting_date, expected_harvest_date, status)
VALUES
  (2001, 1, '番茄', '金棚一号', '2026-06-12', '2026-09-28', 'GROWING'),
  (2002, 2, '番茄', '普罗旺斯', '2026-06-16', '2026-10-02', 'GROWING'),
  (2003, 3, '草莓', '红颜', '2026-07-08', '2026-11-15', 'GROWING'),
  (2004, 4, '黄瓜', '津优', '2026-06-20', '2026-09-25', 'GROWING'),
  (2005, 5, '辣椒', '陇椒', '2026-06-24', '2026-10-08', 'GROWING'),
  (2006, 6, '生菜', '奶油', '2026-08-02', '2026-09-22', 'GROWING');

