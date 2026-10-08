-- Keep leave requests compatible with the project-domain tables used by the boss pending UNION query.
alter table biz_staff_leave_request
  convert to character set utf8mb4 collate utf8mb4_0900_ai_ci;
