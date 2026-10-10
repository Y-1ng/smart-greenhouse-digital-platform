INSERT INTO user_account
  (user_id, username, password_hash, display_name, role_code, status)
VALUES
  (1, 'admin',
   '$2a$10$y3u8MR.0LoJUK/URYdGCSO2xUeZ0v3M9Oea6Me6amTIwpvslFgucO',
   '基地管理员', 'ADMIN', 1),
  (2, 'operator',
   '$2a$10$JXpNu394fKpDSw7mH5QcM.r1BVBMrF8drgw5FxzyuntAbte1MaLWO',
   '温室操作员', 'OPERATOR', 1),
  (3, 'quality',
   '$2a$10$Pd6jxKqIw29.WSyOivOARudreDcXitqt66maVqax9Ob72WgKO1vHi',
   '质检管理员', 'QUALITY', 1),
  (4, 'government',
   '$2a$10$y3u8MR.0LoJUK/URYdGCSO2xUeZ0v3M9Oea6Me6amTIwpvslFgucO',
   '政府监管人员', 'GOVERNMENT', 1),
  (5, 'coop',
   '$2a$10$y3u8MR.0LoJUK/URYdGCSO2xUeZ0v3M9Oea6Me6amTIwpvslFgucO',
   '合作社管理员', 'COOPERATIVE', 1);
