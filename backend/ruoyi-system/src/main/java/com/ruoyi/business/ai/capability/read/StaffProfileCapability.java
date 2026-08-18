package com.ruoyi.business.ai.capability.read;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapability;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiCapabilityRisk;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.exception.ServiceException;

@Component
public class StaffProfileCapability implements AiCapability
{
    private final IBusinessStaffService service;
    @Autowired public StaffProfileCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.profile.get"; }
    @Override public String description() { return "按稳定人员ID读取完整人员档案和组织关系。编辑前必须先读取当前档案，避免覆盖未提及字段。"; }
    @Override public String requiredPermission() { return "business:staff:list"; }
    @Override public AiCapabilityRisk risk() { return AiCapabilityRisk.READ_ONLY; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = AiSchemas.object();
        AiSchemas.property(schema, "staffUserId", "number", "人员目录返回的稳定人员ID");
        return AiSchemas.required(schema, "staffUserId");
    }
    @Override public Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long userId = AiCapabilityInputs.number(input.get("staffUserId"));
        if (userId == null) throw new ServiceException("请先确定要查看的人员");
        BusinessStaffProfile p = service.getStaffProfile(userId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("userId", p.getUserId()); result.put("userName", p.getUserName());
        result.put("nickName", p.getNickName()); result.put("employeeNo", p.getEmployeeNo());
        result.put("deptId", p.getDeptId()); result.put("phoneCountryCode", p.getPhoneCountryCode());
        result.put("phonenumber", p.getPhonenumber()); result.put("email", p.getEmail());
        result.put("countryRegion", p.getCountryRegion()); result.put("sex", p.getSex());
        result.put("positionName", p.getPositionName()); result.put("managerUserId", p.getManagerUserId());
        result.put("employmentType", p.getEmploymentType()); result.put("employmentStatus", p.getEmploymentStatus());
        result.put("hireDate", p.getHireDate()); result.put("workLocation", p.getWorkLocation());
        result.put("status", p.getStatus()); result.put("remark", p.getRemark());
        return result;
    }
}
