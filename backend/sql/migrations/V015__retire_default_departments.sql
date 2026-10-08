-- 清理若依默认演示部门；只调整组织归属，不删除账号、不修改密码和已有角色。
-- 正式库的默认组织包含 101-109，必须整体停用，避免只停用父部门后留下孤立子部门。
update sys_user
set dept_id=100, update_by='admin', update_time=sysdate()
where dept_id between 101 and 109 and del_flag='0';

update sys_dept
set del_flag='2', update_by='admin', update_time=sysdate()
where dept_id between 101 and 109 and del_flag='0';
