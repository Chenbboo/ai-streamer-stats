-- 修复曾通过错误客户端编码写入的江澜显示姓名。
-- 只匹配已知损坏字节，不覆盖线上正常姓名、账号、密码、角色或其他资料。
update sys_user
set nick_name=convert(0xE6B19FE6BE9C using utf8mb4),update_by='system',update_time=sysdate()
where user_name in('jianglan','GLY-jl')
  and hex(nick_name)='C3A6C2B1C29FC3A6C2BEC29C';
