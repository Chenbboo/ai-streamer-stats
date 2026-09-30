-- Project-level owner bonus percentages shown in HR incentive settings.
-- Saving percentages does not create an award, accounting fact, allocation or payment.
create table if not exists biz_project_bonus_setting (
  project_id bigint not null,
  main_owner_bonus_rate decimal(7,4) not null default 0 comment 'Percentage of positive after-tax profit for the project main owner',
  sponsor_owner_bonus_rate decimal(7,4) not null default 0 comment 'Percentage of positive after-tax profit for the project sponsor owner',
  version int not null default 1,
  updated_user_id bigint not null,
  updated_user_name varchar(100) not null,
  create_time datetime not null default current_timestamp,
  update_time datetime not null default current_timestamp,
  primary key (project_id)
) engine=InnoDB default charset=utf8mb4 comment='Project bonus share settings; no payment state';

create table if not exists biz_project_bonus_setting_event (
  event_id bigint not null auto_increment,
  project_id bigint not null,
  old_main_owner_bonus_rate decimal(7,4) default null,
  new_main_owner_bonus_rate decimal(7,4) not null,
  old_sponsor_owner_bonus_rate decimal(7,4) default null,
  new_sponsor_owner_bonus_rate decimal(7,4) not null,
  operator_user_id bigint not null,
  operator_name varchar(100) not null,
  create_time datetime not null default current_timestamp,
  primary key (event_id),
  key idx_project_bonus_setting_event (project_id,event_id)
) engine=InnoDB default charset=utf8mb4 comment='Append-only project bonus share setting audit';

-- Rollback: stop new writes and retain both tables so percentage history is not lost.
