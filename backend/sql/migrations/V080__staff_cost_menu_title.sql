-- V080 renames the staff cost entry while preserving its route and permissions.
update sys_menu
set menu_name='人员成本设置', menu_name_vi='Thiết lập chi phí nhân sự',
    update_by='migration', update_time=sysdate()
where component='business/cost-policies/index' and menu_type='C';
