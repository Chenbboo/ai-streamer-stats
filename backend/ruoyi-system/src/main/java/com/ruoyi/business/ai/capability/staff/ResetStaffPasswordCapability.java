package com.ruoyi.business.ai.capability.staff;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;

@Component
public class ResetStaffPasswordCapability implements AiConfirmableCapability
{
    private final IBusinessStaffService service;
    @Autowired public ResetStaffPasswordCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.password.reset"; }
    @Override public String description()
    { return "重置员工登录密码。先用人员目录取得稳定用户ID；明文密码只用于本轮生成不可逆密文，不写入确认单、工具审计或回答。确认后执行。"; }
    @Override public String requiredPermission() { return "business:staff:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> s = AiSchemas.object(); AiSchemas.property(s, "staffUserId", "number", "人员目录返回的用户ID");
        AiSchemas.property(s, "newPassword", "string", "6到20个字符的新密码，不得包含尖括号、引号、竖线或反斜线");
        return AiSchemas.required(s, "staffUserId", "newPassword");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { validatePassword(text(input.get("newPassword"))); return "重置员工账号“" + staffName(input) + "”的登录密码"; }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("staffUserId", number(input.get("staffUserId")));
        result.put("staffName", staffName(input)); result.put("password", "******"); return result;
    }
    @Override public Map<String, Object> persistedInput(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        String password = text(input.get("newPassword")); validatePassword(password);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("staffUserId", number(input.get("staffUserId")));
        result.put("encodedPassword", SecurityUtils.encryptPassword(password)); return result;
    }
    @Override public Map<String, Object> auditInput(Map<String, Object> input)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (input != null) result.putAll(input); if (result.containsKey("newPassword")) result.put("newPassword", "******");
        if (result.containsKey("encodedPassword")) result.put("encodedPassword", "[REDACTED]"); return result;
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long userId = number(input.get("staffUserId")); service.resetEncodedPassword(userId,
            text(input.get("encodedPassword")), invocation.getActor().getUserName());
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("staffUserId", userId);
        result.put("status", "PASSWORD_RESET"); return result;
    }
    private String staffName(Map<String, Object> input)
    {
        Long id = number(input.get("staffUserId")); if (id == null) throw new ServiceException("请先确定要重置密码的员工");
        List<Map<String, Object>> rows = service.listOptions();
        if (rows != null) for (Map<String, Object> row : rows) if (id.equals(number(row.get("userId"))))
            return text(row.get("nickName")).isEmpty() ? text(row.get("userName")) : text(row.get("nickName"));
        throw new ServiceException("员工不存在或当前账号不可见");
    }
    private void validatePassword(String password)
    {
        if (StringUtils.isBlank(password) || password.length() < 6 || password.length() > 20)
            throw new ServiceException("密码长度必须介于6到20个字符之间");
        if (password.matches(".*[<>\"'|\\\\].*")) throw new ServiceException("密码包含非法字符");
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long number(Object value)
    { try { return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
}
