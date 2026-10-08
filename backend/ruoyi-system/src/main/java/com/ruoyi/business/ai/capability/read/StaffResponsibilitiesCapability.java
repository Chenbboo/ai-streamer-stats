package com.ruoyi.business.ai.capability.read;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class StaffResponsibilitiesCapability implements AiCapability
{
    private final IBusinessStaffService service;
    @Autowired public StaffResponsibilitiesCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.project.responsibilities"; }
    @Override public String description() { return "按人员稳定用户ID读取其负责和参与的项目、立项老板及可见职责。应先用人员目录取得ID。"; }
    @Override public String requiredPermission() { return "business:staff:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "staffUserId", "number", "人员目录返回的稳定用户ID");
        return AiSchemas.required(schema, "staffUserId");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long staffUserId = number(input.get("staffUserId"));
        if (staffUserId == null) throw new ServiceException("请先确定要查看的人员");
        return service.projectResponsibilities(staffUserId, invocation.getActor().getUserId(),
            invocation.getActor().isAdministrator(), invocation.getActor().hasPermission("business:boss:view"));
    }
    private Long number(Object value)
    {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (Exception ex) { return null; }
    }
}
