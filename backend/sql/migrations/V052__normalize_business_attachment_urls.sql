-- Company-operation attachment URLs are web paths. Older Windows uploads persisted backslashes,
-- which broke browser requests and project-scoped authorization checks.

update biz_operating_fact set attachment_urls=replace(attachment_urls,char(92),'/')
where attachment_urls like concat('%',char(92),'%');
update biz_project_acceptance set attachment_urls=replace(attachment_urls,char(92),'/')
where attachment_urls like concat('%',char(92),'%');
update biz_project_stage_acceptance set attachment_urls=replace(attachment_urls,char(92),'/')
where attachment_urls like concat('%',char(92),'%');
update biz_project_task_report set evidence_urls=replace(evidence_urls,char(92),'/')
where evidence_urls like concat('%',char(92),'%');
update biz_project_progress_report set evidence_urls=replace(evidence_urls,char(92),'/')
where evidence_urls like concat('%',char(92),'%');
update biz_project_routine_report set evidence_urls=replace(evidence_urls,char(92),'/')
where evidence_urls like concat('%',char(92),'%');
update biz_project_kpi_result set attachment_urls=replace(attachment_urls,char(92),'/')
where attachment_urls like concat('%',char(92),'%');
update biz_staff_leave_request set attachment_urls=replace(attachment_urls,char(92),'/')
where attachment_urls like concat('%',char(92),'%');
