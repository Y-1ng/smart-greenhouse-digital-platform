INSERT INTO user_account
  (user_id, username, password_hash, display_name, role_code, status)
VALUES
  (1, 'admin',
   '$2a$10$xgcrQmI707rp3C0TIuHRx.Y27aTUc5fAsM8Dvth5Tyn95.VI995Oa',
   '基地管理员', 'ADMIN', 1),
  (2, 'operator',
   '$2a$10$xgcrQmI707rp3C0TIuHRx.Y27aTUc5fAsM8Dvth5Tyn95.VI995Oa',
   '温室操作员', 'OPERATOR', 1),
  (3, 'quality',
   '$2a$10$xgcrQmI707rp3C0TIuHRx.Y27aTUc5fAsM8Dvth5Tyn95.VI995Oa',
   '质检管理员', 'QUALITY', 1),
  (4, 'coop',
   '$2a$10$xgcrQmI707rp3C0TIuHRx.Y27aTUc5fAsM8Dvth5Tyn95.VI995Oa',
   '合作社管理员', 'COOPERATIVE', 1);

INSERT INTO greenhouse
  (greenhouse_id, greenhouse_code, greenhouse_name, area, location, status)
VALUES
  (1, 'GH-001', '1号温室', 1280.00, '示范基地 A区', 'ACTIVE');

INSERT INTO device
  (device_id, greenhouse_id, device_code, device_type, online_status,
   operating_state, last_seen_at)
VALUES
  (1, 1, 'FAN-A-02', 'FAN', 'ONLINE', 'ON', '2026-09-18 14:36:20'),
  (2, 1, 'VALVE-B-01', 'VALVE', 'ONLINE', 'OFF', '2026-09-18 14:36:18'),
  (3, 1, 'LIGHT-A-03', 'GROW_LIGHT', 'ONLINE', 'OFF', '2026-09-28 10:52:07'),
  (4, 1, 'CURTAIN-A-01', 'CURTAIN', 'ONLINE', 'ON', '2026-09-18 14:36:22');

INSERT INTO environment_cycle
  (cycle_id, greenhouse_id, cycle_start, cycle_end, data_digest, root_hash, chain_status)
VALUES
  (1001, 1, '2026-09-18 14:30:00', '2026-09-18 14:35:00',
   'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
   'CONFIRMED');

INSERT INTO environment_threshold
  (greenhouse_id, metric_code, target_min, target_max, alert_min,
   alert_max, control_action, enabled)
VALUES
  (1, 'temperature', 18.0000, 28.0000, 12.0000, 32.0000, 'VENTILATION', 1),
  (1, 'humidity', 55.0000, 80.0000, 40.0000, 90.0000, 'MIST', 1),
  (1, 'light', 12000.0000, 30000.0000, 5000.0000, 45000.0000, 'SUPPLEMENT_LIGHT', 1),
  (1, 'soil_moisture', 35.0000, 55.0000, 25.0000, 65.0000, 'IRRIGATION', 1);

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
  (2001, 1, '番茄', '金棚一号', '2026-06-12', '2026-09-28', 'GROWING');

INSERT INTO water_fertilizer_plan
  (plan_id, plan_no, greenhouse_id, batch_id, recipe_code, recipe_version,
   ec_target, ph_target, zone_name, planned_at, water_volume, status,
   operator_name, executed_at)
VALUES
  (10001, 'WF-2026-0918-001', 1, 2001, 'TOMATO-FRUIT', 'V3',
   1.800, 6.200, 'A区 / B区', '2026-09-18 11:30:00', 2.100, 'SUCCEEDED',
   '基地管理员', '2026-09-18 11:34:00'),
  (10002, 'WF-2026-0918-002', 1, 2001, 'TOMATO-FRUIT', 'V3',
   1.800, 6.200, 'A区', '2026-09-18 15:30:00', 2.400, 'PLANNED',
   NULL, NULL),
  (10003, 'WF-2026-0918-003', 1, 2001, 'TOMATO-FRUIT', 'V3',
   1.800, 6.200, 'B区', '2026-09-18 19:00:00', 1.600, 'PLANNED',
   NULL, NULL);

INSERT INTO quality_report
  (report_id, batch_id, institution_name, report_no, result_summary, file_url, content_hash, tested_at)
VALUES
  (3001, 2001, '寒旱区农产品质量检测中心', 'QR-2026-0915-018',
   '农残检测合格', 'minio://reports/QR-2026-0915-018.pdf',
   'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
   '2026-09-15 10:10:00');

INSERT INTO certificate
  (certificate_id, batch_id, certificate_no, content_hash, chain_tx_hash, status, issued_at)
VALUES
  (4001, 2001, 'CERT-2026-0916-0031',
   'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
   '0x1111111111111111111111111111111111111111111111111111111111111111',
   'VALID', '2026-09-16 09:25:00');

INSERT INTO trace_code
  (trace_code_id, batch_id, field_no, product_code, public_key_id, signature_value, chain_tx_hash, status)
VALUES
  (5001, 2001, 'F-A-03', 'GH-TRACE-2026-0001', 'PK-GREENHOUSE-01',
   'signature-demo-value',
   '0x2222222222222222222222222222222222222222222222222222222222222222',
   'ACTIVE');

INSERT INTO trace_node
  (node_id, field_no, stage_type, previous_hash, payload_hash, chain_tx_hash, created_at)
VALUES
  (6001, 'F-A-03', '种植', '0000000000000000000000000000000000000000000000000000000000000000',
   '1000000000000000000000000000000000000000000000000000000000000000',
   NULL,
   '2026-06-12 08:20:00'),
  (6002, 'F-A-03', '农事', '1000000000000000000000000000000000000000000000000000000000000000',
   '2000000000000000000000000000000000000000000000000000000000000000',
   NULL,
   '2026-07-08 15:30:00'),
  (6003, 'F-A-03', '检测', '2000000000000000000000000000000000000000000000000000000000000000',
   '3000000000000000000000000000000000000000000000000000000000000000',
   NULL,
   '2026-09-15 10:10:00'),
  (6004, 'F-A-03', '合格证', '3000000000000000000000000000000000000000000000000000000000000000',
   '4000000000000000000000000000000000000000000000000000000000000000',
   NULL,
   '2026-09-16 09:25:00'),
  (6005, 'F-A-03', '包装', '4000000000000000000000000000000000000000000000000000000000000000',
   '5000000000000000000000000000000000000000000000000000000000000000',
   NULL,
   '2026-09-17 16:40:00');

INSERT INTO village
  (village_id, village_name, land_area, labor_count, capital_amount, asset_description)
VALUES
  (7001, '示范村一', 320.00, 86, 420000.00, '日光温室与冷链分拣设施'),
  (7002, '示范村二', 260.00, 64, 330000.00, '种植基地与灌溉设施'),
  (7003, '示范村三', 210.00, 52, 260000.00, '育苗中心和仓储设施');

INSERT INTO cooperation_share
  (share_id, village_id, project_name, share_ratio, effective_date, status)
VALUES
  (8001, 7001, '智慧温室示范项目', 0.3800, '2026-01-01', 'ACTIVE'),
  (8002, 7002, '智慧温室示范项目', 0.3100, '2026-01-01', 'ACTIVE'),
  (8003, 7003, '智慧温室示范项目', 0.3100, '2026-01-01', 'ACTIVE');

INSERT INTO dividend_record
  (dividend_id, share_id, period_name, amount, status, confirmed_at)
VALUES
  (9001, 8001, '2026 Q3', 128000.00, 'CONFIRMED', '2026-09-18 10:00:00'),
  (9002, 8002, '2026 Q3', 104000.00, 'PENDING', NULL),
  (9003, 8003, '2026 Q3', 102000.00, 'CONFIRMED', '2026-09-18 10:20:00');
