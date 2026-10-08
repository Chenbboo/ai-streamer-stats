-- 人员扩展档案。账号、密码、角色和联系方式继续保存在 sys_user，避免影响现有业务登录。

create table if not exists biz_staff_profile (
  user_id bigint not null comment '系统用户ID',
  employee_no varchar(32) null comment '员工编号',
  phone_country_code varchar(8) not null default '+86' comment '电话国家区号',
  country_region varchar(16) not null default 'CN' comment '国家或地区代码',
  position_name varchar(64) null comment '岗位名称',
  manager_user_id bigint null comment '直属负责人用户ID',
  employment_type varchar(20) not null default 'FULL_TIME' comment '用工类型',
  employment_status varchar(20) not null default 'ACTIVE' comment '任职状态',
  hire_date date null comment '入职日期',
  work_location varchar(100) null comment '工作地点',
  create_by varchar(64) not null default '' comment '创建人',
  create_time datetime null comment '创建时间',
  update_by varchar(64) not null default '' comment '更新人',
  update_time datetime null comment '更新时间',
  primary key (user_id),
  unique key uk_biz_staff_profile_employee_no (employee_no),
  key idx_biz_staff_profile_manager (manager_user_id),
  key idx_biz_staff_profile_status (employment_status)
) engine=innodb default charset=utf8mb4 comment='公司人员扩展档案';

-- 为现有账号建立兼容档案；越南公司人员默认使用越南区号，其余账号默认使用中国区号。
insert ignore into biz_staff_profile(user_id,phone_country_code,country_region,employment_type,
  employment_status,create_by,create_time,update_by,update_time)
select u.user_id,
       case when vn.dept_id is not null then '+84' else '+86' end,
       case when vn.dept_id is not null then 'VN' else 'CN' end,
       'FULL_TIME','ACTIVE','migration',sysdate(),'migration',sysdate()
from sys_user u
left join sys_dept d on d.dept_id=u.dept_id and d.del_flag='0'
left join sys_dept vn on vn.dept_name='越南meimaru公司' and vn.del_flag='0'
  and (vn.dept_id=d.dept_id or find_in_set(vn.dept_id,d.ancestors))
where u.del_flag='0';
