-- 制单员可维护达人商品价格、批量导入商品绑定及维护预设搭售；审核员保持只读。
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r
join sys_menu m on m.perms='jewelry:influencer:price' and m.status='0'
where r.role_key='jewelry_maker' and r.del_flag='0';
