-- 子项目拨款只作为预算额度，不再自动生成主项目支出、子项目收入或预计收入。
delete from biz_project_proposal_revenue where revenue_type='PARENT_FUNDING';

-- 保留已有经营事实的类别快照；类别停用后不再出现在手工录入选项中。
update biz_fact_category
set status='1'
where category_code in ('SUBPROJECT_FUNDING_COST','SUBPROJECT_FUNDING_REVENUE');
