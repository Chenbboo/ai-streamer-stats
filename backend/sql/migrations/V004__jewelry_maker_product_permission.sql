-- 制单员只能通过组装单内联创建目标成品，不授予商品档案全局新增权限。
delete rm
from sys_role_menu rm
join sys_role r on r.role_id = rm.role_id
where r.role_key = 'jewelry_maker'
  and r.del_flag = '0'
  and rm.menu_id = 3103;
