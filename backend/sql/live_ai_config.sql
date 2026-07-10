-- AI recognition and chat config. Run in `ry-vue`.
-- This script only inserts missing keys, so rerunning it will not wipe API keys.

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Enabled', 'live.ai.enabled', 'false', 'N', 'admin', sysdate(), 'true=real AI,false=Mock'
where not exists (select 1 from sys_config where config_key = 'live.ai.enabled');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Provider', 'live.ai.provider', 'openai-compatible-chat', 'N', 'admin', sysdate(), 'mock/openai-responses/openai-compatible-chat'
where not exists (select 1 from sys_config where config_key = 'live.ai.provider');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Model', 'live.ai.model', 'gpt-4o-mini', 'N', 'admin', sysdate(), 'vision/text model from selected provider'
where not exists (select 1 from sys_config where config_key = 'live.ai.model');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI API Key', 'live.ai.apiKey', '', 'N', 'admin', sysdate(), 'vision model API key'
where not exists (select 1 from sys_config where config_key = 'live.ai.apiKey');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Base URL', 'live.ai.baseUrl', 'https://api.openai.com/v1/chat/completions', 'N', 'admin', sysdate(), 'vision model endpoint URL'
where not exists (select 1 from sys_config where config_key = 'live.ai.baseUrl');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Timeout', 'live.ai.timeout', '60', 'N', 'admin', sysdate(), 'vision model timeout seconds'
where not exists (select 1 from sys_config where config_key = 'live.ai.timeout');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Chat Enabled', 'live.ai.chat.enabled', 'true', 'N', 'admin', sysdate(), 'true=enable stats chat'
where not exists (select 1 from sys_config where config_key = 'live.ai.chat.enabled');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Chat Provider', 'live.ai.chat.provider', 'openai-compatible-chat', 'N', 'admin', sysdate(), 'chat model provider'
where not exists (select 1 from sys_config where config_key = 'live.ai.chat.provider');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Chat API Key', 'live.ai.chat.apiKey', '', 'N', 'admin', sysdate(), 'stats chat model API key'
where not exists (select 1 from sys_config where config_key = 'live.ai.chat.apiKey');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Chat Base URL', 'live.ai.chat.baseUrl', 'https://api.openai.com/v1/chat/completions', 'N', 'admin', sysdate(), 'stats chat endpoint URL'
where not exists (select 1 from sys_config where config_key = 'live.ai.chat.baseUrl');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Chat Model', 'live.ai.chat.model', 'gpt-4o-mini', 'N', 'admin', sysdate(), 'stats chat model'
where not exists (select 1 from sys_config where config_key = 'live.ai.chat.model');

insert into sys_config(config_name, config_key, config_value, config_type, create_by, create_time, remark)
select 'Live AI Chat Timeout', 'live.ai.chat.timeout', '60', 'N', 'admin', sysdate(), 'stats chat timeout seconds'
where not exists (select 1 from sys_config where config_key = 'live.ai.chat.timeout');
