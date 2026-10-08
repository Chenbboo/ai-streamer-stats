-- 子项目申请人和实际负责人分开保存；正式项目沿用 main_owner_user_id。
alter table biz_project_proposal
    add column assigned_owner_user_id bigint null comment '子项目负责人用户ID' after parent_project_id,
    add column assigned_owner_name varchar(64) null comment '子项目负责人姓名快照' after assigned_owner_user_id;

-- 兼容历史子项目申请，不改变已有正式项目的负责人。
update biz_project_proposal proposal
left join biz_project project on project.project_id=proposal.created_project_id
set proposal.assigned_owner_user_id=coalesce(project.main_owner_user_id,proposal.applicant_user_id),
    proposal.assigned_owner_name=coalesce(project.main_owner_name,proposal.applicant_name)
where proposal.parent_project_id is not null;
