-- 项目管理费不再使用最低利润门槛和封顶金额。
-- 保留历史列以兼容旧版本，统一清空已有配置，运行时也不再读取这些字段。
update biz_project_management_fee
set minimum_profit=0,
    cap_amount=null
where minimum_profit<>0 or cap_amount is not null;
