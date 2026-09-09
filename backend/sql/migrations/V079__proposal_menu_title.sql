-- V079 renames the existing proposal entry without changing its route or permissions.
update sys_menu
set menu_name='立项申请', update_by='migration', update_time=sysdate()
where menu_id=4009 and component='business/proposal/index';
