-- 公司组织负责人绑定：上海美丸文化公司对应江澜，越南meimaru公司对应王赋章。
-- 该绑定只用于组织管理和人员成本范围；立项申请的审批/归属老板由申请人手动指定。

update sys_dept company
join sys_user owner_user on owner_user.user_id=(
  select selected.user_id
  from (
    select u.user_id
    from sys_user u
    where u.del_flag='0' and u.status='0' and u.user_name in('jianglan','GLY-jl')
    order by case when u.user_name='jianglan' then 0 else 1 end,u.user_id
    limit 1
  ) selected
)
set company.leader_user_id=owner_user.user_id,
    company.leader=coalesce(nullif(owner_user.nick_name,''),owner_user.user_name),
    company.phone=owner_user.phonenumber,
    company.email=owner_user.email,
    company.update_by='migration',company.update_time=sysdate()
where company.dept_id=110 and company.parent_id=100 and company.del_flag='0';

update sys_dept company
join sys_user owner_user on owner_user.user_name='wangfuzhang'
  and owner_user.del_flag='0' and owner_user.status='0'
set company.leader_user_id=owner_user.user_id,
    company.leader=coalesce(nullif(owner_user.nick_name,''),owner_user.user_name),
    company.phone=owner_user.phonenumber,
    company.email=owner_user.email,
    company.update_by='migration',company.update_time=sysdate()
where company.dept_id=111 and company.parent_id=100 and company.del_flag='0';
