package com.ruoyi.business.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.business.mapper.BusinessCompanyAccessMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/** Company grants supplement menu permissions; personal/reporting identities remain unchanged. */
@Service
public class BusinessCompanyAccessService
{
    public static final List<String> CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
        "BUSINESS", "STAFF", "COST_READ", "COST_WRITE", "ATTENDANCE_READ", "INTEGRATION", "CUTOVER", "AUTHORIZE"));
    @Autowired private BusinessCompanyAccessMapper mapper;
    @Autowired private com.ruoyi.system.service.ISysUserService users;
    @Autowired private com.ruoyi.system.service.ISysDeptService departments;

    public boolean allowed(Long userId, Long companyId, String capability)
    { return userId != null && companyId != null && CAPABILITIES.contains(capability) && mapper.allowed(userId,companyId,capability)>0; }
    public boolean project(BusinessProject project, Long userId)
    { return project != null && allowed(userId,project.getCompanyDeptId(),"BUSINESS"); }
    public boolean project(Long projectId, Long userId)
    { return projectId != null && allowed(userId,mapper.projectCompany(projectId),"BUSINESS"); }
    public Long departmentCompany(Long deptId) { return deptId == null ? null : mapper.departmentCompany(deptId); }
    public Long staffCompany(Long userId) { return userId == null ? null : mapper.staffCompany(userId); }
    public boolean staff(Long actor, Long userId, String capability)
    { return allowed(actor,staffCompany(userId),capability); }
    public boolean isCompanyBoss(Long userId) { return userId!=null && mapper.activeBoss(userId)>0; }
    public void requireStaff(Long userId)
    {
        if(SecurityUtils.isAdmin())return;
        if(!isCompanyBoss(SecurityUtils.getUserId())) { users.checkUserDataScope(userId);return; }
        if(!staff(SecurityUtils.getUserId(),userId,"STAFF")) throw new ServiceException("没有该公司人员管理权限");
    }
    public void requireDepartment(Long deptId)
    {
        if(SecurityUtils.isAdmin())return;
        if(!isCompanyBoss(SecurityUtils.getUserId())) { departments.checkDeptDataScope(deptId);return; }
        if(!allowed(SecurityUtils.getUserId(),departmentCompany(deptId),"STAFF")) throw new ServiceException("没有该公司组织管理权限");
    }
    public Map<String,Object> workspace(Long actor)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        List<Map<String,Object>> companies=mapper.companies(actor,SecurityUtils.isAdmin(actor));
        result.put("companies",companies); result.put("capabilities",CAPABILITIES);
        result.put("users",companies.isEmpty()?Collections.emptyList():mapper.bosses());
        return result;
    }
    public List<Map<String,Object>> grants(Long companyId,Long actor)
    { requireAuthorization(companyId,actor); return mapper.grants(companyId); }
    private void requireAuthorization(Long companyId,Long actor)
    { if(!SecurityUtils.isAdmin(actor)&&!allowed(actor,companyId,"AUTHORIZE")) throw new ServiceException("没有该公司的授权管理权限"); }
    @Transactional
    public void save(Long companyId, Long userId, List<String> capabilities, int version, String reason, Long actor, String actorName)
    {
        if(mapper.lockCompany(companyId)==null) throw new ServiceException("公司不存在或已停用");
        requireAuthorization(companyId,actor);
        if(userId==null || mapper.activeBoss(userId)==0) throw new ServiceException("请选择有效的老板账号");
        if(capabilities==null || !CAPABILITIES.containsAll(capabilities)) throw new ServiceException("公司权限不正确");
        if(reason==null || reason.trim().isEmpty() || reason.length()>500) throw new ServiceException("请填写500字以内的授权变更原因");
        if(!SecurityUtils.isAdmin(actor))
        {
            if(actor.equals(userId)) throw new ServiceException("不能修改本人的公司授权，请由另一位授权管理人调整");
            Set<String> existing=new HashSet<>();
            for(Map<String,Object> row:mapper.grants(companyId)) if(String.valueOf(userId).equals(String.valueOf(row.get("userId"))))
                existing.addAll(Arrays.asList(String.valueOf(row.get("capabilities")).split(",")));
            Set<String> affected=new HashSet<>(existing);affected.addAll(capabilities);
            Set<String> unchanged=new HashSet<>(existing);unchanged.retainAll(capabilities);affected.removeAll(unchanged);
            for(String capability:affected) if(!capability.isEmpty()&&!allowed(actor,companyId,capability))
                throw new ServiceException("不能调整超出本人权限范围的授权");
        }
        if(capabilities.contains("COST_WRITE")&&!capabilities.contains("COST_READ")) throw new ServiceException("修改人员成本须同时拥有查看权限");
        if(mapper.version(companyId,userId)!=version) throw new ServiceException("授权已被修改，请刷新后重试");
        Map<String,Object> row=new HashMap<>();row.put("companyId",companyId);row.put("userId",userId);
        row.put("capabilities",String.join(",",new TreeSet<>(capabilities)));row.put("actorId",actor);row.put("actorName",actorName);row.put("reason",reason.trim());
        mapper.replace(row);mapper.audit(row);
    }
}
