UPDATE user_account
SET password_hash =
  '$2a$10$xgcrQmI707rp3C0TIuHRx.Y27aTUc5fAsM8Dvth5Tyn95.VI995Oa'
WHERE username IN (
  'admin',
  'operator',
  'quality',
  'government',
  'coop'
);
