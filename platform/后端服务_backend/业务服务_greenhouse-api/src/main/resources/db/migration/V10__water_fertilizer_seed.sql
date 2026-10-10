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
