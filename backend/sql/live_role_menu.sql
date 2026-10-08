-- 兼容入口：直播角色和菜单已经迁移到幂等脚本。
-- 请从 backend/sql 目录使用 MySQL 命令行执行本文件。
-- 本文件不会删除已有角色、菜单或授权。

source migrations/V001__system_menu_i18n.sql;
source migrations/V003__live_permissions_i18n.sql;
