-- Clean duplicate AI config rows and prevent future duplicate config keys.
-- Run in `ry-vue` after backing up the database if the environment already has data.

delete c1
from sys_config c1
join sys_config c2
  on c1.config_key = c2.config_key
 and c1.config_id < c2.config_id
where c1.config_key like 'live.ai.%';

alter table sys_config add unique key uk_config_key (config_key);
