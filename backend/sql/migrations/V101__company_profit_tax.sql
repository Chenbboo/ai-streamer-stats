create table if not exists biz_company_profit_tax (
 company_dept_id bigint primary key,tax_rate decimal(7,4) not null,
 version int not null default 1,update_by varchar(64) not null,update_time datetime not null default current_timestamp
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_company_profit_tax_event (
 event_id bigint primary key auto_increment,company_dept_id bigint not null,
 old_rate decimal(7,4),new_rate decimal(7,4) not null,reason varchar(500) not null,
 operator_id bigint not null,operator_name varchar(64) not null,create_time datetime not null default current_timestamp
) engine=InnoDB default charset=utf8mb4;
create table if not exists biz_project_profit_tax_snapshot (
 project_id bigint primary key,company_dept_id bigint,tax_rate decimal(7,4) not null,
 configured char(1) not null,pretax_profit decimal(30,4) not null,tax_amount decimal(30,2) not null,
 aftertax_profit decimal(30,4) not null,create_by varchar(64) not null,create_time datetime not null default current_timestamp
) engine=InnoDB default charset=utf8mb4;
-- Existing closed projects retain their recorded result; no retrospective tax is invented.
insert ignore into biz_project_profit_tax_snapshot(project_id,company_dept_id,tax_rate,configured,pretax_profit,tax_amount,aftertax_profit,create_by)
select p.project_id,p.company_dept_id,0,'0',coalesce(sum(r.profit_amount),0),0,coalesce(sum(r.profit_amount),0),'legacy-migration'
from biz_project p left join biz_project_daily_result r on r.project_id=p.project_id and r.is_current='1'
where p.accounting_state='CLOSED' group by p.project_id,p.company_dept_id;
