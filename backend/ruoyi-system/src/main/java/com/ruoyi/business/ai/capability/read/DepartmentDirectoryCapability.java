package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.common.core.domain.entity.SysDept;

@Component
public class DepartmentDirectoryCapability implements AiCapability
{
    private final IBusinessDepartmentService service;
    @Autowired public DepartmentDirectoryCapability(IBusinessDepartmentService service) { this.service = service; }
    @Override public String code() { return "department.directory.get"; }
    @Override public String description() { return "读取两家公司及其部门组织、负责人和人员数量。"; }
    @Override public String requiredPermission() { return "business:department:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema() { return AiSchemas.object(); }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("departments", service.listDepartments(new SysDept())); return result;
    }
}
