ALTER TABLE device
  ADD COLUMN operating_state VARCHAR(32) NOT NULL DEFAULT 'OFF' AFTER online_status;

UPDATE device
SET operating_state = CASE device_code
  WHEN 'FAN-A-02' THEN 'ON'
  WHEN 'VALVE-B-01' THEN 'OFF'
  WHEN 'LIGHT-A-03' THEN 'OFF'
  WHEN 'FAN-A-04' THEN 'ON'
  WHEN 'LIGHT-B-02' THEN 'ON'
  WHEN 'VALVE-B-03' THEN 'OFF'
  WHEN 'MIST-C-01' THEN 'OFF'
  WHEN 'FAN-C-02' THEN 'ON'
  ELSE 'OFF'
END;

INSERT INTO device (
  device_id,
  greenhouse_id,
  device_code,
  device_type,
  online_status,
  operating_state,
  last_seen_at
) VALUES (
  9,
  1,
  'CURTAIN-A-01',
  'CURTAIN',
  'ONLINE',
  'ON',
  '2026-09-18 14:36:22'
);
