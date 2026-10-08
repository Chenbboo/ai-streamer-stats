-- Members can report at project level without choosing a continuous-work item.
-- Existing routine-linked reports remain intact.
alter table biz_project_work_report modify column routine_id bigint null;
