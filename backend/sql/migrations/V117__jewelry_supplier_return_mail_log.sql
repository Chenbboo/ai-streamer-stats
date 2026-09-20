-- One digest attempt per local calendar day. An ambiguous SMTP result must not be sent twice.
create table if not exists jewelry_supplier_return_mail_log (
  alert_date date not null primary key,
  recipients varchar(2000) not null,
  warning_count int not null,
  status varchar(16) not null,
  error_message varchar(500) null,
  create_time datetime not null,
  sent_time datetime null
) engine=InnoDB default charset=utf8mb4 comment='珠宝成品退供期限邮件发送记录';
