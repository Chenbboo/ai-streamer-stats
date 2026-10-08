-- 兼容入口：直播业务表已经迁移到安全、幂等的结构基线。
-- 请从 backend/sql 目录使用 MySQL 命令行执行本文件。
-- 本文件不会再删除任何业务表或业务数据。

source migrations/V002__live_schema_baseline.sql;
