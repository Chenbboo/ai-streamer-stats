package com.ruoyi.business.ai.capability.project;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.business.ai.capability.*;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.service.IBusinessProjectService;
import com.ruoyi.common.exception.ServiceException;
import static com.ruoyi.business.ai.capability.AiCapabilityInputs.*;

@Component
public class UpdateProjectCapability implements AiConfirmableCapability
{
    private final IBusinessProjectService service;
    @Autowired public UpdateProjectCapability(IBusinessProjectService service){this.service=service;}
    public String code(){return "project.update";}
    public String description(){return "修改一个已存在项目的基础资料。先查询项目详情取得稳定项目ID，只传需要修改的字段；确认后才保存。预算和主负责人必须使用各自专用工具。";}
    public String requiredPermission(){return "business:project:edit";}
    public Map<String,Object> inputSchema(){Map<String,Object>s=AiSchemas.object();
        AiSchemas.property(s,"projectId","number","项目详情返回的稳定项目ID");
        AiSchemas.property(s,"projectName","string","新项目名称");AiSchemas.property(s,"parentId","number","新上级项目ID");
        AiSchemas.property(s,"companyDeptId","number","新归属公司或部门ID");AiSchemas.property(s,"projectType","string","项目类型");
        AiSchemas.property(s,"accountingMode","string","PROFIT/COST/VALUE/HYBRID");AiSchemas.property(s,"managementMode","string","SIMPLE/STANDARD/DELIVERY");
        AiSchemas.property(s,"objective","string","新的可验收项目目标");AiSchemas.property(s,"planStartDate","string","计划开始日期 YYYY-MM-DD");
        AiSchemas.property(s,"planEndDate","string","计划结束日期 YYYY-MM-DD");AiSchemas.property(s,"priority","string","LOW/MEDIUM/HIGH");
        AiSchemas.property(s,"executionSource","string","执行来源，例如 LIVE 或 MANUAL");return AiSchemas.required(s,"projectId");}
    public String confirmationSummary(AiCapabilityInvocation i,Map<String,Object>in){BusinessProject p=current(i,in);Map<String,Object>c=changes(in);if(c.isEmpty())throw new ServiceException("请说明要修改的项目字段");return "修改项目“"+p.getProjectName()+"”："+c;}
    public Map<String,Object> confirmationDetails(AiCapabilityInvocation i,Map<String,Object>in){Map<String,Object>d=new LinkedHashMap<String,Object>();BusinessProject p=current(i,in);d.put("projectId",p.getProjectId());d.put("projectName",p.getProjectName());d.put("changes",changes(in));return d;}
    public Map<String,Object> executeConfirmed(AiCapabilityInvocation i,Map<String,Object>in){BusinessProject p=current(i,in);apply(p,in);BusinessProject saved=service.updateProject(p,i.getActor().getUserId(),i.getActor().getUserName(),true);Map<String,Object>r=new LinkedHashMap<String,Object>();r.put("projectId",saved.getProjectId());r.put("projectName",saved.getProjectName());r.put("status",saved.getStatus());r.put("changes",changes(in));return r;}
    private BusinessProject current(AiCapabilityInvocation i,Map<String,Object>in){Long id=number(in.get("projectId"));if(id==null)throw new ServiceException("请先确定要修改的项目");return service.getProject(id,i.getActor().getUserId(),i.getActor().isAdministrator(),true);}
    private Map<String,Object> changes(Map<String,Object>in){Map<String,Object>r=new LinkedHashMap<String,Object>();for(String k:new String[]{"projectName","parentId","companyDeptId","projectType","accountingMode","managementMode","objective","planStartDate","planEndDate","priority","executionSource"})if(has(in,k))r.put(k,in.get(k));return r;}
    private void apply(BusinessProject p,Map<String,Object>in){if(has(in,"projectName"))p.setProjectName(text(in.get("projectName")));if(has(in,"parentId"))p.setParentId(number(in.get("parentId")));if(has(in,"companyDeptId"))p.setCompanyDeptId(number(in.get("companyDeptId")));if(has(in,"projectType"))p.setProjectType(upper(in.get("projectType")));if(has(in,"accountingMode"))p.setAccountingMode(upper(in.get("accountingMode")));if(has(in,"managementMode"))p.setManagementMode(upper(in.get("managementMode")));if(has(in,"objective"))p.setObjective(text(in.get("objective")));if(has(in,"planStartDate"))p.setPlanStartDate(date(in.get("planStartDate")));if(has(in,"planEndDate"))p.setPlanEndDate(date(in.get("planEndDate")));if(has(in,"priority"))p.setPriority(upper(in.get("priority")));if(has(in,"executionSource"))p.setExecutionSource(upper(in.get("executionSource")));}
}
