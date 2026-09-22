-- One owner notification is written in the same transaction as each deletion review.
create table if not exists biz_project_delete_notification (
    notification_id bigint not null auto_increment,
    request_id bigint not null,
    recipient_user_id bigint not null,
    read_time datetime null,
    create_time datetime not null default current_timestamp,
    primary key (notification_id),
    unique key uk_project_delete_notification_request (request_id),
    key idx_project_delete_notification_inbox (recipient_user_id,read_time,notification_id)
) engine=InnoDB default charset=utf8mb4 comment='项目删除审核结果通知';
