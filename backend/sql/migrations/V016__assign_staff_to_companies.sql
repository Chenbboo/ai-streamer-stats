-- 按现有业务角色分配公司：珠宝业务归上海，主播业务归越南。
-- 只更新 dept_id；线上账号、密码、启停状态和原有角色关系全部保留。
update sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
set u.dept_id=110, u.update_by='admin', u.update_time=sysdate()
where u.del_flag='0'
  and r.role_key in ('jewelry_maker','jewelry_reviewer','jewelry_admin');

update sys_user u
join sys_user_role ur on ur.user_id=u.user_id
join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
set u.dept_id=111, u.update_by='admin', u.update_time=sysdate()
where u.del_flag='0' and r.role_key='streamer';
