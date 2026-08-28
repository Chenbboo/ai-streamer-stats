-- Bosses can return a draft operating fact to its submitter for correction without deleting its audit history.

alter table biz_operating_fact
  add column returned_user_id bigint default null after confirmed_time,
  add column returned_user_name varchar(100) default null after returned_user_id,
  add column returned_time datetime default null after returned_user_name,
  add column return_reason varchar(500) default null after returned_time;
