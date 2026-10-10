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

