-- 正式库升级前只读检查。
-- 在迁移前后各执行一次并保存输出；两个 identity_checksum 和 legacy_role_checksum 必须完全一致。
-- 校验不输出密码，只对身份字段和密码哈希再次做 SHA-256 摘要。

set session group_concat_max_len=1048576;

select
  count(*) as active_user_count,
  sum(status='0') as enabled_user_count,
  sha2(group_concat(concat_ws('|',
    user_id,
    hex(coalesce(user_name,'')),
    hex(coalesce(nick_name,'')),
    hex(coalesce(user_type,'')),
    hex(coalesce(email,'')),
    hex(coalesce(phonenumber,'')),
    hex(coalesce(sex,'')),
    sha2(coalesce(password,''),256),
    coalesce(status,''),
    coalesce(del_flag,'')
  ) order by user_id separator ';'),256) as identity_checksum
from sys_user
where del_flag='0';

select
  count(*) as legacy_role_link_count,
  sha2(group_concat(concat_ws('|',u.user_id,hex(u.user_name),r.role_key)
    order by u.user_id,r.role_key separator ';'),256) as legacy_role_checksum
from sys_user_role ur
join sys_user u on u.user_id=ur.user_id and u.del_flag='0'
join sys_role r on r.role_id=ur.role_id and r.del_flag='0'
where r.role_key not in ('company_owner','project_user','company_staff');

select 'reserved_company_dept_id_conflict' check_name,count(*) problem_rows
from sys_dept
where del_flag='0' and (
  (dept_id=110 and dept_name<>'上海美丸文化公司')
  or (dept_id=111 and dept_name<>'越南meimaru公司')
)
union all
select 'duplicate_active_user_name',count(*)
from (
  select user_name from sys_user where del_flag='0'
  group by user_name having count(*)>1
) duplicate_user
union all
select 'jianglan_alias_conflict',greatest(count(*)-1,0)
from sys_user
where del_flag='0' and user_name in ('jianglan','GLY-jl')
union all
select 'missing_wangfuzhang_account',if(count(*)=0,1,0)
from sys_user
where del_flag='0' and user_name='wangfuzhang';
