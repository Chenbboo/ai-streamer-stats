-- A returned work report creates one member-only inbox notification in the review transaction.
create table if not exists biz_project_work_report_notification (
    notification_id bigint not null auto_increment,
    report_id bigint not null,
    recipient_user_id bigint not null,
    read_time datetime null,
    create_time datetime not null default current_timestamp,
    primary key (notification_id),
    unique key uk_work_report_notification_report (report_id),
    key idx_work_report_notification_inbox (recipient_user_id, read_time, notification_id)
) engine=InnoDB default charset=utf8mb4 comment='工作汇报退回通知';

-- Backfill earlier returns once; the unique report key keeps reruns idempotent.
insert into biz_project_work_report_notification (report_id, recipient_user_id, create_time)
select r.report_id, r.submitted_user_id, coalesce(r.reviewed_time, r.create_time)
from biz_project_work_report r
left join biz_project_work_report_notification n on n.report_id = r.report_id
where r.status = 'RETURNED' and n.notification_id is null;
