-- V078 moves the existing boss workbench into the project system, retaining its menu ID and permissions.
update sys_menu
set menu_name='老板工作台', menu_name_vi='Bàn làm việc của chủ doanh nghiệp',
    parent_id=4000, order_num=1, update_by='migration', update_time=sysdate()
where menu_id=4001 and component='business/boss/index' and perms='business:boss:view';

-- Existing workbench roles need the parent directory to render the moved menu.
insert ignore into sys_role_menu(role_id,menu_id)
select role_id,4000 from sys_role_menu where menu_id=4001;
