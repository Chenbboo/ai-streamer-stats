-- 美丸集团下设两家公司；两位公司老板共享查看和维护组织架构。
update sys_dept
set dept_name='美丸集团', order_num=0, status='0'
where dept_id=100 and parent_id=0 and del_flag='0';

insert into sys_dept(dept_id,parent_id,ancestors,dept_name,order_num,status,del_flag,create_by,create_time)
values
(110,100,'0,100','上海美丸文化公司',1,'0','0','admin',sysdate()),
(111,100,'0,100','越南meimaru公司',2,'0','0','admin',sysdate())
on duplicate key update parent_id=values(parent_id),ancestors=values(ancestors),dept_name=values(dept_name),
  order_num=values(order_num),status='0',del_flag='0';

-- 保留原有部门和账号，将旧根节点下的组织整体归入上海公司，后续再按实际业务调整。
update sys_dept child
join sys_dept legacy on legacy.parent_id=100 and legacy.dept_id not in (110,111)
  and child.dept_id<>legacy.dept_id
  and child.ancestors like concat('0,100,',legacy.dept_id,'%')
set child.ancestors=concat('0,100,110,',substring(child.ancestors,length('0,100,')+1))
where child.del_flag='0';

update sys_dept
set parent_id=110, ancestors='0,100,110'
where parent_id=100 and dept_id not in (110,111) and del_flag='0';
