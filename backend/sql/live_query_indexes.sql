-- Composite indexes for the customer maintenance statistics queries.
-- This migration is safe to run more than once on MySQL 8.

set @schema_name = database();

set @ddl = (
  select if(count(*) = 0,
    'alter table live_gift_record add index idx_streamer_customer_date (streamer_id, customer_id, biz_date)',
    'select 1')
  from information_schema.statistics
  where table_schema = @schema_name
    and table_name = 'live_gift_record'
    and index_name = 'idx_streamer_customer_date'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @ddl = (
  select if(count(*) = 0,
    'alter table live_chat_contact add index idx_streamer_customer_date_interaction (streamer_id, customer_id, biz_date, has_interaction)',
    'select 1')
  from information_schema.statistics
  where table_schema = @schema_name
    and table_name = 'live_chat_contact'
    and index_name = 'idx_streamer_customer_date_interaction'
);
prepare stmt from @ddl;
execute stmt;       
deallocate prepare stmt;

set @ddl = (
  select if(count(*) = 0,
    'alter table live_follow_record add index idx_streamer_customer_date_status (streamer_id, customer_id, biz_date, follow_status)',
    'select 1')
  from information_schema.statistics
  where table_schema = @schema_name
    and table_name = 'live_follow_record'
    and index_name = 'idx_streamer_customer_date_status'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;
