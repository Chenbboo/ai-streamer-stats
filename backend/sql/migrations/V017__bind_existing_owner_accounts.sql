-- 将老板身份绑定到既有账号，不覆盖用户名、密码、状态或原有业务角色。
-- 本地优先使用 jianglan；正式库当前使用 GLY-jl。两者同时存在时只绑定 jianglan，避免重复老板身份。
-- 王赋章账号须通过系统用户管理以一次性密码安全创建；账号存在后重复执行本脚本即可绑定老板角色。

insert ignore into sys_user_role(user_id,role_id)
select u.user_id,r.role_id
from sys_user u
join sys_role r on r.role_key='company_owner' and r.del_flag='0'
where u.del_flag='0'
  and (
    u.user_name='jianglan'
    or (u.user_name='GLY-jl' and not exists(
      select 1 from sys_user local_owner
      where local_owner.user_name='jianglan' and local_owner.del_flag='0'
    ))
    or u.user_name='wangfuzhang'
  );

update sys_user u
set u.dept_id=100,u.update_by='admin',u.update_time=sysdate()
where u.del_flag='0'
  and (
    u.user_name='jianglan'
    or (u.user_name='GLY-jl' and not exists(
      select 1 from (select user_name,del_flag from sys_user) local_owner
      where local_owner.user_name='jianglan' and local_owner.del_flag='0'
    ))
    or u.user_name='wangfuzhang'
  );

-- 老板权限已覆盖项目参与人员权限，避免角色叠加重新显示“我的工作台”。
delete project_user_role
from sys_user_role project_user_role
join sys_role project_role on project_role.role_id=project_user_role.role_id
  and project_role.role_key='project_user' and project_role.del_flag='0'
join sys_user_role owner_user_role on owner_user_role.user_id=project_user_role.user_id
join sys_role owner_role on owner_role.role_id=owner_user_role.role_id
  and owner_role.role_key='company_owner' and owner_role.del_flag='0';
