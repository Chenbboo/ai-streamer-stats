-- A project remains active until an administrator approves its owner's deletion request.
create table if not exists biz_project_delete_request (
    request_id bigint not null auto_increment comment '申请ID',
    project_id bigint not null comment '项目ID',
    project_name varchar(200) not null comment '项目名称快照',
    reason varchar(500) not null comment '删除原因',
    status varchar(16) not null default 'PENDING' comment 'PENDING/APPROVED/REJECTED',
    request_user_id bigint not null comment '申请人ID',
    request_user_name varchar(64) not null comment '申请人',
    request_time datetime not null comment '申请时间',
    review_user_id bigint default null comment '审核人ID',
    review_user_name varchar(64) default null comment '审核人',
    review_comment varchar(500) default null comment '审核说明',
    review_time datetime default null comment '审核时间',
    pending_project_id bigint generated always as (case when status='PENDING' then project_id else null end) stored,
    primary key (request_id),
    unique key uk_project_delete_pending (pending_project_id),
    key idx_project_delete_project (project_id,request_id),
    key idx_project_delete_status (status,request_id)
) engine=InnoDB default charset=utf8mb4 comment='项目删除申请与管理员审核记录';
