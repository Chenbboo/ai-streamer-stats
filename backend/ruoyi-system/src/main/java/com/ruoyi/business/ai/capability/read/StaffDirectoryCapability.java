package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessStaffService;

@Component
public class StaffDirectoryCapability implements AiCapability
{
    private final IBusinessStaffService service;
    @Autowired public StaffDirectoryCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.directory.get"; }
    @Override public String description() { return "读取公司人员选项及稳定用户ID，供模型定位负责人、成员和人员查询对象。"; }
    @Override public String requiredPermission() { return "business:staff:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("staff", service.listOptions());
        return result;
    }
}
