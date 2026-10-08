-- Allow incomplete proposal drafts. Launch validates these fields before creating any project.
-- Repeatable; existing records and all formal project constraints remain unchanged.
alter table biz_project_proposal
  modify sponsor_owner_user_id bigint null comment '归属老板，草稿可暂缺',
  modify company_dept_id bigint null comment '归属公司，草稿可暂缺',
  modify plan_start_date date null comment '计划开始日期，草稿可暂缺';
