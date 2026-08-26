-- 立项申请允许只设置计划开始日期；空结束日期表示项目不限期。

alter table biz_project_proposal
  modify column plan_end_date date null comment '计划结束日期；空值表示不限期';
