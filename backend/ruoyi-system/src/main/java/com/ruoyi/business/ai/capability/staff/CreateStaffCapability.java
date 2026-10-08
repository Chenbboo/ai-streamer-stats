package com.ruoyi.business.ai.capability.staff;

import java.security.SecureRandom;
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
public class CreateStaffCapability implements AiConfirmableCapability
{
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private final IBusinessStaffService service;
    private final SecureRandom secureRandom = new SecureRandom();
    @Autowired public CreateStaffCapability(IBusinessStaffService service) { this.service = service; }
    @Override public String code() { return "staff.create"; }
    @Override public String description() { return "新增员工账号和人员档案。出于密码安全，AI不收集或保存密码；创建后账号保持停用，需通过安全密码设置流程再启用。"; }
    @Override public String requiredPermission() { return "business:staff:manage"; }
    @Override public Map<String, Object> inputSchema()
    {
        Map<String, Object> schema = profileSchema(false);
        AiSchemas.property(schema, "userName", "string", "唯一登录账号");
        return AiSchemas.required(schema, "userName", "nickName", "deptId", "countryRegion");
    }
    @Override public String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        return "新增员工“" + AiCapabilityInputs.text(input.get("nickName")) + "”（登录账号 "
            + AiCapabilityInputs.text(input.get("userName")) + "），归属组织 "
            + AiCapabilityInputs.number(input.get("deptId")) + "；创建后账号先保持停用";
    }
    @Override public Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input)
    {
        BusinessStaffProfile p = apply(new BusinessStaffProfile(), input);
        p.setUserName(AiCapabilityInputs.text(input.get("userName")));
        p.setPassword(temporaryPassword());
        service.createStaff(p, invocation.getActor().getUserName());
        service.changeStatus(p.getUserId(), "1", invocation.getActor().getUserName());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("staffUserId", p.getUserId()); result.put("userName", p.getUserName());
        result.put("nickName", p.getNickName()); result.put("status", "1");
        result.put("requiresSecurePasswordSetup", true);
        return result;
    }
    private String temporaryPassword()
    {
        StringBuilder value = new StringBuilder(18);
        for (int i = 0; i < 18; i++) value.append(PASSWORD_CHARS[secureRandom.nextInt(PASSWORD_CHARS.length)]);
        return value.toString();
    }
    static Map<String, Object> profileSchema(boolean includeId)
    {
        Map<String, Object> s = AiSchemas.object();
        if (includeId) AiSchemas.property(s, "staffUserId", "number", "稳定人员ID");
        AiSchemas.property(s, "nickName", "string", "人员姓名"); AiSchemas.property(s, "employeeNo", "string", "员工编号");
        AiSchemas.property(s, "deptId", "number", "所属公司或部门ID"); AiSchemas.property(s, "phoneCountryCode", "string", "电话国家区号");
        AiSchemas.property(s, "phonenumber", "string", "手机号码，不含国家区号"); AiSchemas.property(s, "email", "string", "邮箱");
        AiSchemas.property(s, "countryRegion", "string", "国家/地区代码，例如 CN、VN"); AiSchemas.property(s, "sex", "string", "0男、1女、2未知");
        AiSchemas.property(s, "positionName", "string", "岗位名称，可留空"); AiSchemas.property(s, "managerUserId", "number", "直属负责人用户ID");
        AiSchemas.property(s, "employmentType", "string", "用工类型，例如 FULL_TIME"); AiSchemas.property(s, "employmentStatus", "string", "任职状态，例如 ACTIVE");
        AiSchemas.property(s, "hireDate", "string", "入职日期 YYYY-MM-DD"); AiSchemas.property(s, "workLocation", "string", "工作地点");
        AiSchemas.property(s, "remark", "string", "备注"); return s;
    }
    static BusinessStaffProfile apply(BusinessStaffProfile p, Map<String, Object> in)
    {
        p.setNickName(AiCapabilityInputs.text(in.get("nickName"))); p.setEmployeeNo(AiCapabilityInputs.text(in.get("employeeNo")));
        p.setDeptId(AiCapabilityInputs.number(in.get("deptId"))); p.setPhoneCountryCode(AiCapabilityInputs.text(in.get("phoneCountryCode")));
        p.setPhonenumber(AiCapabilityInputs.text(in.get("phonenumber"))); p.setEmail(AiCapabilityInputs.text(in.get("email")));
        p.setCountryRegion(AiCapabilityInputs.upper(in.get("countryRegion"))); p.setSex(AiCapabilityInputs.text(in.get("sex")));
        p.setPositionName(AiCapabilityInputs.text(in.get("positionName"))); p.setManagerUserId(AiCapabilityInputs.number(in.get("managerUserId")));
        p.setEmploymentType(AiCapabilityInputs.upper(in.get("employmentType"))); p.setEmploymentStatus(AiCapabilityInputs.upper(in.get("employmentStatus")));
        p.setHireDate(AiCapabilityInputs.date(in.get("hireDate"))); p.setWorkLocation(AiCapabilityInputs.text(in.get("workLocation")));
        p.setRemark(AiCapabilityInputs.text(in.get("remark"))); return p;
    }
}
