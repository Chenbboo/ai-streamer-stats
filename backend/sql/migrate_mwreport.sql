-- MWreport 7月数据迁移到 ai-streamer-stats
-- 执行前先备份本地数据库

-- 1. 插入主播（如果不存在）
INSERT IGNORE INTO live_streamer (user_id, stage_name, tiktok_handle, status, create_by, create_time) VALUES
(1, 'Zhenzhen', '@zhenzhen', '0', 'admin', sysdate()),
(2, 'Xixi', '@xixi', '0', 'admin', sysdate()),
(3, 'Mina', '@mina', '0', 'admin', sysdate()),
(4, 'Naomi', '@naomi', '0', 'admin', sysdate()),
(5, 'Rynna', '@rynna-005', '0', 'admin', sysdate()),
(6, 'Vivan', '@vivan-006', '0', 'admin', sysdate());

-- 2. 获取主播ID映射
-- 假设 streamer_id: Zhenzhen=100, Xixi=101, Mina=102, Naomi=103, Rynna=104, Vivan=105
-- 实际ID需要根据插入后的自增ID调整

-- 3. 插入客户和打赏记录（示例，需要根据实际数据批量执行）
-- 先插入客户（按主播隔离）
INSERT INTO live_customer (streamer_id, nickname, first_seen_date, last_seen_date, create_by, create_time)
SELECT s.streamer_id, '里長柏 🌙', '2026-07-01', '2026-07-03', 'admin', sysdate()
FROM live_streamer s WHERE s.stage_name = 'Naomi'
AND NOT EXISTS (SELECT 1 FROM live_customer WHERE nickname = '里長柏 🌙' AND streamer_id = s.streamer_id);

-- 然后插入打赏记录
-- INSERT INTO live_gift_record (biz_date, streamer_id, customer_id, xu, rank_no, confirm_status, ai_confidence, create_time, update_by, update_time)
-- SELECT '2026-07-01', s.streamer_id, c.customer_id, 2472, 1, '1', '1', sysdate(), 'admin', sysdate()
-- FROM live_streamer s, live_customer c
-- WHERE s.stage_name = 'Naomi' AND c.nickname = '里長柏 🌙' AND c.streamer_id = s.streamer_id;
