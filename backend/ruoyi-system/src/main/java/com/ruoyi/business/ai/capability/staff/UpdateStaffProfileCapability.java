package com.ruoyi.business.ai.capability.staff;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.AiCapabilityInputs;
import com.ruoyi.business.ai.capability.AiCapabilityInvocation;
import com.ruoyi.business.ai.capability.AiConfirmableCapability;
import com.ruoyi.business.ai.capability.AiSchemas;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.business.service.IBusinessStaffService;

@Component
public class UpdateStaffProfileCapability implements AiConfirmableCapability
{
    private final IBusinessStaffService service;
    @Autowired public UpdateStaffProfileCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.profile.update"; }
    @Override public String description() { return "编辑人员档案和组织关系。仅修改老板明确提出的字段，其余字段保留；操作前应先读取当前完整档案。"; }
    @Override public String requiredPermission() { return "business:staff:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = CreateStaffCapability.profileSchema(true);
        return AiSchemas.required(schema, "staffUserId");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return "更新人员 " + AiCapabilityInputs.number(input.get("staffUserId")) + " 的档案字段：" + changedFields(input);
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        Long userId = AiCapabilityInputs.number(input.get("staffUserId"));
        BusinessStaffProfile current = service.getStaffProfile(userId);
        overlay(current, input);
        Object staff = service.updateStaff(current, invocation.getActor().getUserName());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("staffUserId", userId); result.put("changedFields", changedFields(input)); result.put("staff", staff);
        return result;
    }
    private void overlay(BusinessStaffProfile p, Map<String, Object> in)
    {
        if (in.containsKey("nickName")) p.setNickName(AiCapabilityInputs.text(in.get("nickName")));
        if (in.containsKey("employeeNo")) p.setEmployeeNo(AiCapabilityInputs.text(in.get("employeeNo")));
        if (in.containsKey("deptId")) p.setDeptId(AiCapabilityInputs.number(in.get("deptId")));
        if (in.containsKey("phoneCountryCode")) p.setPhoneCountryCode(AiCapabilityInputs.text(in.get("phoneCountryCode")));
        if (in.containsKey("phonenumber")) p.setPhonenumber(AiCapabilityInputs.text(in.get("phonenumber")));
        if (in.containsKey("email")) p.setEmail(AiCapabilityInputs.text(in.get("email")));
        if (in.containsKey("countryRegion")) p.setCountryRegion(AiCapabilityInputs.upper(in.get("countryRegion")));
        if (in.containsKey("sex")) p.setSex(AiCapabilityInputs.text(in.get("sex")));
        if (in.containsKey("positionName")) p.setPositionName(AiCapabilityInputs.text(in.get("positionName")));
        if (in.containsKey("managerUserId")) p.setManagerUserId(AiCapabilityInputs.number(in.get("managerUserId")));
        if (in.containsKey("employmentType")) p.setEmploymentType(AiCapabilityInputs.upper(in.get("employmentType")));
        if (in.containsKey("employmentStatus")) p.setEmploymentStatus(AiCapabilityInputs.upper(in.get("employmentStatus")));
        if (in.containsKey("hireDate")) p.setHireDate(AiCapabilityInputs.date(in.get("hireDate")));
        if (in.containsKey("workLocation")) p.setWorkLocation(AiCapabilityInputs.text(in.get("workLocation")));
        if (in.containsKey("remark")) p.setRemark(AiCapabilityInputs.text(in.get("remark")));
    }
    private String changedFields(Map<String, Object> input)
    {
        StringBuilder fields = new StringBuilder();
        for (String key : input.keySet()) if (!"staffUserId".equals(key))
        { if (fields.length() > 0) fields.append('、'); fields.append(key); }
        return fields.length() == 0 ? "无" : fields.toString();
    }
}
