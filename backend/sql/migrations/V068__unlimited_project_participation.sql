-- Allow open-ended participation for open-ended projects. Existing finite assignments are unchanged.
alter table biz_project_resource_assignment modify effective_to date null;
