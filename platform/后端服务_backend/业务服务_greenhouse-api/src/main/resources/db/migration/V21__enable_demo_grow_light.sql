UPDATE device
SET online_status = 'ONLINE',
    last_seen_at = CURRENT_TIMESTAMP
WHERE device_code = 'LIGHT-A-03';
