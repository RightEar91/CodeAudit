INSERT IGNORE INTO ca_rules (name, category, language, description, prompt, is_builtin, is_enabled, created_at, updated_at) VALUES
('SQL注入检查', 'SECURITY', 'Java', '检测 SQL 拼接、未参数化查询', '重点检查是否存在SQL拼接（String.format/StringBuilder拼接SQL）、未使用PreparedStatement、MyBatis中${}代替#{}等SQL注入风险。', true, true, NOW(), NOW()),
('空指针检查', 'BUG', 'Java', '检测 null 检查缺失、Optional 误用', '检查未进行null检查直接调用方法、Optional.get()未做isPresent判断、方法返回值可能为null但未处理等情况。', true, true, NOW(), NOW()),
('性能优化', 'PERFORMANCE', 'Java', '检测 N+1 查询、String 拼接、重复对象创建', '检查循环中的数据库调用(N+1问题)、循环内String使用+拼接应改用StringBuilder、循环内重复创建Pattern/DateFormat等对象。', true, true, NOW(), NOW()),
('资源泄漏', 'BUG', 'Java', '检测流/连接/锁未正确关闭', '检查IO流、数据库连接、HTTP连接等资源未在finally块或try-with-resources中关闭的情况。', true, true, NOW(), NOW());
