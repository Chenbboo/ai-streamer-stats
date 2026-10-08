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

@Component
public class ChangeStaffStatusCapability implements AiConfirmableCapability
{
    private final IBusinessStaffService service;
    @Autowired public ChangeStaffStatusCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.account.status.change"; }
    @Override public String description()
    { return "启用或停用指定员工账号。先用人员目录取得稳定用户ID；老板确认后才执行，受保护的老板和管理员账号不能修改。"; }
    @Override public String requiredPermission() { return "business:staff:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "staffUserId", "number", "人员目录返回的稳定用户ID");
        Map<String, Object> status = AiSchemas.property(schema, "status", "string", "0表示启用，1表示停用");
        status.put("enum", java.util.Arrays.asList("0", "1"));
        return AiSchemas.required(schema, "staffUserId", "status");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return ("0".equals(status(input)) ? "启用" : "停用") + "员工账号“" + name(input) + "”"; }
    @Override public Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("staffUserId", number(input.get("staffUserId"))); result.put("staffName", name(input));
        result.put("status", status(input)); return result;
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long userId = number(input.get("staffUserId")); String status = status(input);
        service.changeStatus(userId, status, invocation.getActor().getUserName());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("staffUserId", userId); result.put("staffName", name(input)); result.put("status", status); return result;
    }
    private String name(Map<String, Object> input)
    {
        Long id = number(input.get("staffUserId"));
        if (id == null) throw new ServiceException("请先确定要操作的员工");
        List<Map<String, Object>> staff = service.listOptions();
        if (staff != null) for (Map<String, Object> row : staff)
            if (id.equals(number(row.get("userId"))))
            {
                Object value = row.get("nickName") == null ? row.get("userName") : row.get("nickName");
                return String.valueOf(value);
            }
        throw new ServiceException("员工不存在或当前账号不可见");
    }
    private String status(Map<String, Object> input)
    {
        String value = input.get("status") == null ? "" : String.valueOf(input.get("status"));
        if (!"0".equals(value) && !"1".equals(value)) throw new ServiceException("账号状态只能是启用或停用");
        return value;
    }
    private Long number(Object value)
    { try { return value instanceof Number ? ((Number)value).longValue() : Long.valueOf(String.valueOf(value)); }
      catch (Exception ex) { return null; } }
}
