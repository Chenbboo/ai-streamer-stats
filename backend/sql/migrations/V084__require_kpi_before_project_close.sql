-- KPI is a project-close prerequisite. Repair records closed under the earlier
-- separated-delivery rule while their accounting and KPI settlement stay open.
START TRANSACTION;

INSERT INTO biz_project_event(
  project_id,event_type,from_status,to_status,operator_user_id,operator_name,
  event_comment,create_time
)
SELECT
  project.project_id,'REOPEN_KPI','CLOSED','ACCEPTANCE',
  COALESCE(project.sponsor_owner_user_id,project.initiator_user_id,project.main_owner_user_id,1),
  'migration-v084',
  '项目结项时KPI尚未完成，已恢复为待结项；完成并确认全部KPI结算后可重新结项',
  NOW()
FROM biz_project project
WHERE project.status='CLOSED'
  AND project.delivery_policy_version='SEPARATED_V1'
  AND COALESCE(project.accounting_state,'OPEN')='OPEN'
  AND EXISTS (
    SELECT 1
    FROM biz_project_kpi_plan plan
    LEFT JOIN biz_project_kpi_settlement settlement ON settlement.plan_id=plan.plan_id
    WHERE plan.project_id=project.project_id
      AND plan.status='PUBLISHED'
      AND COALESCE(settlement.status,'DRAFT')<>'CONFIRMED'
  );

UPDATE biz_project project
SET project.status='ACCEPTANCE',
    project.actual_end_date=NULL,
    project.version=project.version+1,
    project.update_by='migration-v084',
    project.update_time=NOW()
WHERE project.status='CLOSED'
  AND project.delivery_policy_version='SEPARATED_V1'
  AND COALESCE(project.accounting_state,'OPEN')='OPEN'
  AND EXISTS (
    SELECT 1
    FROM biz_project_kpi_plan plan
    LEFT JOIN biz_project_kpi_settlement settlement ON settlement.plan_id=plan.plan_id
    WHERE plan.project_id=project.project_id
      AND plan.status='PUBLISHED'
      AND COALESCE(settlement.status,'DRAFT')<>'CONFIRMED'
  );

COMMIT;
