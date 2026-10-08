-- V082 additive migration: existing facts, priced days and closed results are not rewritten.
CREATE TABLE IF NOT EXISTS biz_project_spend_confirmation (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id bigint NOT NULL, biz_date date NOT NULL,
 confirmed_user_id bigint NOT NULL, confirmed_user_name varchar(100) NOT NULL, confirmed_at datetime NOT NULL,
 UNIQUE KEY uk_project_day(project_id,biz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS biz_project_cost_pause (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id bigint NOT NULL, effective_from date NOT NULL,
 effective_to date DEFAULT NULL, operator_name varchar(100) NOT NULL, reason varchar(2000) NOT NULL,
 created_at datetime NOT NULL, resumed_by varchar(100) DEFAULT NULL, resumed_at datetime DEFAULT NULL,
 KEY ix_project(project_id,effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS biz_staff_departure (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,user_id bigint NOT NULL,effective_date date NOT NULL,
 reason varchar(2000) NOT NULL,handover_json longtext NOT NULL,status varchar(20) NOT NULL,
 requested_by varchar(100) NOT NULL,requested_user_id bigint NOT NULL,created_at datetime NOT NULL,
 completed_at datetime DEFAULT NULL,error_message varchar(2000) DEFAULT NULL,
 KEY ix_due(status,effective_date),KEY ix_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS biz_closed_project_adjustment (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,project_id bigint NOT NULL,original_fact_id bigint DEFAULT NULL,
 business_date date NOT NULL,posting_date date DEFAULT NULL,profit_delta decimal(18,2) NOT NULL,
 currency varchar(3) NOT NULL,reason varchar(2000) NOT NULL,status varchar(20) NOT NULL,
 request_id varchar(64) NOT NULL,requested_user_id bigint NOT NULL,requested_by varchar(100) NOT NULL,
 created_at datetime NOT NULL,reviewed_by varchar(100) DEFAULT NULL,reviewed_user_id bigint DEFAULT NULL,
 reviewed_at datetime DEFAULT NULL,review_comment varchar(2000) DEFAULT NULL,
 UNIQUE KEY uk_request(project_id,requested_user_id,request_id),KEY ix_posting(status,posting_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
