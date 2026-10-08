-- 稳定期测算依赖下一预算周期的有效人员成本。
-- 尚未配置未来费率时必须保留空值，避免把未知成本错误保存为 0。
alter table biz_project_proposal
  modify recurring_estimated_total_cost decimal(20,2) null default null comment '稳定期预计总成本，资料不完整时为空',
  modify recurring_expected_profit decimal(20,2) null default null comment '稳定期预计利润，资料不完整时为空';
