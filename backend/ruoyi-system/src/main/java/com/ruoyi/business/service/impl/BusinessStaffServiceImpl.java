package com.ruoyi.business.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessStaffProfileMapper;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;

@Service
public class BusinessStaffServiceImpl implements IBusinessStaffService
{
    private static final String DEFAULT_PHONE_CODE = "+86";
    private static final String DEFAULT_REGION = "CN";
    private static final String DEFAULT_EMPLOYMENT_TYPE = "FULL_TIME";
    private static final String DEFAULT_EMPLOYMENT_STATUS = "ACTIVE";

    @Autowired private ISysUserService userService;
    @Autowired private ISysDeptService deptService;
    @Autowired private BusinessProjectMapper projectMapper;
    @Autowired private BusinessStaffProfileMapper profileMapper;

    @Override
    public TableDataInfo listStaff(SysUser query)
    {
        SysUser safeQuery = query == null ? new SysUser() : query;
        List<SysUser> users = userService.selectUserList(safeQuery);
        long total = new PageInfo<SysUser>(users).getTotal();
        Map<Long, BusinessStaffProfile> profiles = profilesFor(users);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (SysUser user : users) rows.add(toView(user, profiles.get(user.getUserId())));
        TableDataInfo result = new TableDataInfo();
        result.setCode(200);
        result.setMsg("查询成功");
        result.setRows(rows);
        result.setTotal(total);
        return result;
    }

    @Override
    public BusinessStaffProfile getStaffProfile(Long userId)
    {
        SysUser user = requireExisting(userId);
        BusinessStaffProfile profile = profileMapper.selectByUserId(userId);
        if (profile == null) profile = new BusinessStaffProfile();
        profile.setUserId(user.getUserId());
        profile.setUserName(user.getUserName());
        profile.setNickName(user.getNickName());
        profile.setDeptId(user.getDeptId());
        profile.setPhonenumber(user.getPhonenumber());
        profile.setEmail(user.getEmail());
        profile.setSex(user.getSex());
        profile.setStatus(user.getStatus());
        profile.setRemark(user.getRemark());
        if (StringUtils.isBlank(profile.getPhoneCountryCode())) profile.setPhoneCountryCode(DEFAULT_PHONE_CODE);
        if (StringUtils.isBlank(profile.getCountryRegion())) profile.setCountryRegion(DEFAULT_REGION);
        if (StringUtils.isBlank(profile.getEmploymentType())) profile.setEmploymentType(DEFAULT_EMPLOYMENT_TYPE);
        if (StringUtils.isBlank(profile.getEmploymentStatus())) profile.setEmploymentStatus(DEFAULT_EMPLOYMENT_STATUS);
        return profile;
    }

    @Override
    public List<Map<String, Object>> listOptions()
    {
        SysUser query = new SysUser();
        query.setStatus("0");
        List<SysUser> users = userService.selectUserList(query);
        Map<Long, BusinessStaffProfile> profiles = profilesFor(users);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (SysUser user : users)
        {
            BusinessStaffProfile profile = profiles.get(user.getUserId());
            Map<String, Object> option = new LinkedHashMap<String, Object>();
            option.put("userId", user.getUserId());
            option.put("userName", user.getUserName());
            option.put("nickName", user.getNickName());
            option.put("deptId", user.getDeptId());
            option.put("deptName", user.getDept() == null ? null : user.getDept().getDeptName());
            option.put("companyName", profile == null ? null : profile.getCompanyName());
            option.put("companyDeptId", profile == null ? null : profile.getCompanyDeptId());
            option.put("positionName", profile == null ? null : profile.getPositionName());
            result.add(option);
        }
        return result;
    }

    @Override
    public Map<String, Object> projectResponsibilities(Long staffUserId, Long viewerUserId, boolean viewAll, boolean boss)
    {
        requireExisting(staffUserId);
        List<Map<String, Object>> source = projectMapper.selectStaffResponsibilities(staffUserId, viewerUserId, viewAll, boss);
        if (source == null) source = Collections.emptyList();
        List<Map<String, Object>> projects = new ArrayList<Map<String, Object>>();
        int ownerCount = 0;
        int memberCount = 0;
        int openCount = 0;
        int assignedTaskCount = 0;
        int completedTaskCount = 0;
        for (Map<String, Object> sourceRow : source)
        {
            Map<String, Object> row = new LinkedHashMap<String, Object>(sourceRow);
            boolean activeResponsibility = "0".equals(String.valueOf(row.get("responsibilityStatus")));
            boolean owner = "OWNER".equals(row.get("responsibilityRole"));
            if (activeResponsibility && owner) ownerCount++;
            if (activeResponsibility && !owner) memberCount++;
            boolean foreignBossProject = boss && !viewAll
                && !sameLong(row.get("initiatorUserId"), viewerUserId);
            row.put("canOpen", !foreignBossProject);
            if (foreignBossProject)
            {
                // Bosses may see cross-owner project names and ownership, but not operational details.
                row.remove("projectNo");
                row.remove("projectType");
                row.remove("status");
                row.remove("priority");
                row.remove("planStartDate");
                row.remove("planEndDate");
                row.remove("mainOwnerUserId");
                row.remove("mainOwnerName");
                row.remove("joinedDate");
                row.remove("leftDate");
                row.remove("assignedTaskCount");
                row.remove("completedTaskCount");
            }
            else
            {
                if (!"CLOSED".equals(row.get("status")) && !"CANCELED".equals(row.get("status"))) openCount++;
                assignedTaskCount += intValue(row.get("assignedTaskCount"));
                completedTaskCount += intValue(row.get("completedTaskCount"));
            }
            projects.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("projects", projects);
        result.put("ownerCount", ownerCount);
        result.put("memberCount", memberCount);
        result.put("openCount", openCount);
        result.put("assignedTaskCount", assignedTaskCount);
        result.put("completedTaskCount", completedTaskCount);
        return result;
    }

    @Override
    public List<TreeSelect> departmentOptions()
    {
        return deptService.selectDeptTreeList(new SysDept());
    }

    @Override
    @Transactional
    public Object createStaff(BusinessStaffProfile input, String operatorName)
    {
        validateCreate(input);
        validateDepartment(input.getDeptId(), false);
        if (!userService.checkUserNameUnique(input)) throw new ServiceException("登录账号已存在");
        normalizeAndValidateContact(input);
        validateContactUnique(input);
        prepareAndValidateProfile(input, null);
        Long roleId = projectMapper.selectRoleIdByKey("project_user");
        if (roleId == null) throw new ServiceException("项目参与人员角色尚未初始化");
        input.setRoleIds(new Long[] { roleId });
        input.setPostIds(new Long[0]);
        input.setStatus("0");
        if (StringUtils.isBlank(input.getSex())) input.setSex("2");
        input.setPassword(SecurityUtils.encryptPassword(input.getPassword()));
        input.setCreateBy(operatorName);
        if (userService.insertUser(input) != 1) throw new ServiceException("新增人员失败");
        input.setUpdateBy(operatorName);
        if (profileMapper.upsert(input) < 1) throw new ServiceException("新增人员档案失败");
        return toView(userService.selectUserById(input.getUserId()), profileMapper.selectByUserId(input.getUserId()));
    }

    @Override
    @Transactional
    public Object updateStaff(BusinessStaffProfile input, String operatorName)
    {
        SysUser existing = requireExisting(input == null ? null : input.getUserId());
        boolean protectedAccount = isProtected(existing);
        if (StringUtils.isBlank(input.getNickName())) throw new ServiceException("人员姓名不能为空");
        if (protectedAccount) input.setDeptId(existing.getDeptId());
        validateDepartment(input.getDeptId(), protectedAccount);
        normalizeAndValidateContact(input);
        validateContactUnique(input);
        BusinessStaffProfile existingProfile = profileMapper.selectByUserId(existing.getUserId());
        if (protectedAccount && existingProfile != null)
        {
            input.setManagerUserId(existingProfile.getManagerUserId());
            input.setEmploymentStatus(existingProfile.getEmploymentStatus());
        }
        prepareAndValidateProfile(input, existing.getUserId());

        SysUser patch = new SysUser();
        patch.setUserId(existing.getUserId());
        patch.setDeptId(input.getDeptId());
        patch.setNickName(input.getNickName());
        patch.setPhonenumber(input.getPhonenumber());
        patch.setEmail(input.getEmail());
        patch.setSex(StringUtils.isBlank(input.getSex()) ? "2" : input.getSex());
        patch.setRemark(input.getRemark());
        patch.setUpdateBy(operatorName);
        if (userService.updateUserProfile(patch) != 1) throw new ServiceException("修改人员失败");
        input.setUpdateBy(operatorName);
        if (profileMapper.upsert(input) < 1) throw new ServiceException("修改人员档案失败");
        profileMapper.syncDepartmentLeader(existing.getUserId());
        return toView(userService.selectUserById(existing.getUserId()), profileMapper.selectByUserId(existing.getUserId()));
    }

    @Override
    public void changeStatus(Long userId, String status, String operatorName)
    {
        requireManageable(userId);
        if (!"0".equals(status) && !"1".equals(status)) throw new ServiceException("账号状态不正确");
        SysUser patch = new SysUser(userId);
        patch.setStatus(status);
        patch.setUpdateBy(operatorName);
        if (userService.updateUserStatus(patch) != 1) throw new ServiceException("修改账号状态失败");
    }

    @Override
    public void resetPassword(Long userId, String password, String operatorName)
    {
        validatePassword(password);
        resetEncodedPassword(userId, SecurityUtils.encryptPassword(password), operatorName);
    }

    @Override
    public void resetEncodedPassword(Long userId, String encodedPassword, String operatorName)
    {
        requireManageable(userId);
        if (StringUtils.isBlank(encodedPassword) || !encodedPassword.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$"))
            throw new ServiceException("密码密文格式不正确");
        SysUser patch = new SysUser(userId);
        patch.setPassword(encodedPassword);
        patch.setUpdateBy(operatorName);
        if (userService.resetPwd(patch) != 1) throw new ServiceException("重置密码失败");
    }

    private Map<Long, BusinessStaffProfile> profilesFor(List<SysUser> users)
    {
        if (users == null || users.isEmpty()) return Collections.emptyMap();
        List<Long> userIds = new ArrayList<Long>();
        for (SysUser user : users) userIds.add(user.getUserId());
        Map<Long, BusinessStaffProfile> result = new HashMap<Long, BusinessStaffProfile>();
        for (BusinessStaffProfile profile : profileMapper.selectByUserIds(userIds)) result.put(profile.getUserId(), profile);
        return result;
    }

    private SysUser requireExisting(Long userId)
    {
        if (userId == null) throw new ServiceException("人员ID不能为空");
        SysUser user = userService.selectUserById(userId);
        if (user == null || "2".equals(user.getDelFlag())) throw new ServiceException("人员不存在");
        return user;
    }

    private SysUser requireManageable(Long userId)
    {
        SysUser user = requireExisting(userId);
        if (isProtected(user)) throw new ServiceException("系统管理员和老板账号为受保护账号");
        return user;
    }

    private boolean isProtected(SysUser user)
    {
        return SecurityUtils.isAdmin(user.getUserId())
            || projectMapper.countUserRoleByKey(user.getUserId(), "company_owner") > 0;
    }

    private void validateCreate(BusinessStaffProfile input)
    {
        if (input == null || StringUtils.isBlank(input.getUserName())) throw new ServiceException("登录账号不能为空");
        if (input.getUserName().length() > 30) throw new ServiceException("登录账号不能超过30个字符");
        if (StringUtils.isBlank(input.getNickName())) throw new ServiceException("人员姓名不能为空");
        validatePassword(input.getPassword());
    }

    private void validatePassword(String password)
    {
        if (StringUtils.isBlank(password) || password.length() < 6 || password.length() > 20)
            throw new ServiceException("密码长度必须介于6到20个字符之间");
        if (password.matches(".*[<>\"'|\\\\].*")) throw new ServiceException("密码包含非法字符");
    }

    private void normalizeAndValidateContact(BusinessStaffProfile input)
    {
        if (StringUtils.isNotBlank(input.getPhonenumber()))
        {
            String phone = input.getPhonenumber().replaceAll("[\\s-]", "");
            if (!phone.matches("\\d{6,15}")) throw new ServiceException("手机号码应为6到15位数字");
            input.setPhonenumber(phone);
        }
    }

    private void validateContactUnique(SysUser user)
    {
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
            throw new ServiceException("手机号码已存在");
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
            throw new ServiceException("邮箱账号已存在");
    }

    private void prepareAndValidateProfile(BusinessStaffProfile input, Long currentUserId)
    {
        input.setEmployeeNo(StringUtils.trim(input.getEmployeeNo()));
        input.setPhoneCountryCode(StringUtils.isBlank(input.getPhoneCountryCode()) ? DEFAULT_PHONE_CODE : input.getPhoneCountryCode().trim());
        input.setCountryRegion(StringUtils.isBlank(input.getCountryRegion()) ? DEFAULT_REGION : input.getCountryRegion().trim().toUpperCase());
        input.setEmploymentType(StringUtils.isBlank(input.getEmploymentType()) ? DEFAULT_EMPLOYMENT_TYPE : input.getEmploymentType());
        input.setEmploymentStatus(StringUtils.isBlank(input.getEmploymentStatus()) ? DEFAULT_EMPLOYMENT_STATUS : input.getEmploymentStatus());
        if (StringUtils.isNotEmpty(input.getEmployeeNo()))
        {
            if (input.getEmployeeNo().length() > 32) throw new ServiceException("员工编号不能超过32个字符");
            if (profileMapper.countEmployeeNo(input.getEmployeeNo(), currentUserId) > 0) throw new ServiceException("员工编号已存在");
        }
        if (!input.getPhoneCountryCode().matches("\\+\\d{1,4}")) throw new ServiceException("电话国家区号格式不正确");
        if (input.getCountryRegion().length() > 16) throw new ServiceException("国家或地区代码不能超过16个字符");
        if (StringUtils.isNotEmpty(input.getPositionName()) && input.getPositionName().length() > 64)
            throw new ServiceException("岗位名称不能超过64个字符");
        if (StringUtils.isNotEmpty(input.getWorkLocation()) && input.getWorkLocation().length() > 100)
            throw new ServiceException("工作地点不能超过100个字符");
        if (!isOneOf(input.getEmploymentType(), "FULL_TIME", "PART_TIME", "CONTRACTOR", "INTERN"))
            throw new ServiceException("用工类型不正确");
        if (!isOneOf(input.getEmploymentStatus(), "PROBATION", "ACTIVE", "ON_LEAVE", "LEFT"))
            throw new ServiceException("任职状态不正确");
        if (input.getManagerUserId() != null)
        {
            if (input.getManagerUserId().equals(currentUserId)) throw new ServiceException("直属负责人不能选择本人");
            SysUser manager = userService.selectUserById(input.getManagerUserId());
            if (manager == null || !"0".equals(manager.getDelFlag()) || !"0".equals(manager.getStatus()))
                throw new ServiceException("所选直属负责人不存在或已停用");
        }
    }

    private boolean isOneOf(String value, String... options)
    {
        for (String option : options) if (option.equals(value)) return true;
        return false;
    }

    private boolean sameLong(Object value, Long expected)
    {
        return value != null && expected != null && String.valueOf(value).equals(String.valueOf(expected));
    }

    private int intValue(Object value)
    {
        if (value == null) return 0;
        return Integer.parseInt(String.valueOf(value));
    }

    private void validateDepartment(Long deptId, boolean allowGroupRoot)
    {
        if (deptId == null) throw new ServiceException("请选择所属公司或部门");
        SysDept dept = deptService.selectDeptById(deptId);
        if (dept == null || !"0".equals(dept.getStatus())) throw new ServiceException("所选部门不存在或已停用");
        if (!allowGroupRoot && Long.valueOf(0L).equals(dept.getParentId()))
            throw new ServiceException("普通人员必须归属具体公司或部门");
    }

    private Map<String, Object> toView(SysUser user, BusinessStaffProfile profile)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("userId", user.getUserId());
        row.put("userName", user.getUserName());
        row.put("nickName", user.getNickName());
        row.put("deptId", user.getDeptId());
        row.put("deptName", user.getDept() == null ? null : user.getDept().getDeptName());
        row.put("deptAncestors", user.getDept() == null ? null : user.getDept().getAncestors());
        row.put("phonenumber", user.getPhonenumber());
        row.put("email", user.getEmail());
        row.put("sex", user.getSex());
        row.put("status", user.getStatus());
        row.put("loginDate", user.getLoginDate());
        row.put("createTime", user.getCreateTime());
        row.put("remark", user.getRemark());
        row.put("employeeNo", profile == null ? null : profile.getEmployeeNo());
        row.put("phoneCountryCode", profile == null ? DEFAULT_PHONE_CODE : profile.getPhoneCountryCode());
        row.put("countryRegion", profile == null ? DEFAULT_REGION : profile.getCountryRegion());
        row.put("positionName", profile == null ? null : profile.getPositionName());
        row.put("managerUserId", profile == null ? null : profile.getManagerUserId());
        row.put("managerName", profile == null ? null : profile.getManagerName());
        row.put("employmentType", profile == null ? DEFAULT_EMPLOYMENT_TYPE : profile.getEmploymentType());
        row.put("employmentStatus", profile == null ? DEFAULT_EMPLOYMENT_STATUS : profile.getEmploymentStatus());
        row.put("hireDate", profile == null ? null : profile.getHireDate());
        row.put("workLocation", profile == null ? null : profile.getWorkLocation());
        row.put("companyDeptId", profile == null ? null : profile.getCompanyDeptId());
        row.put("companyName", profile == null ? null : profile.getCompanyName());
        boolean admin = SecurityUtils.isAdmin(user.getUserId());
        boolean owner = !admin && projectMapper.countUserRoleByKey(user.getUserId(), "company_owner") > 0;
        row.put("protectedAccount", admin || owner);
        row.put("accountType", admin ? "系统管理员" : owner ? "老板" : "员工");
        row.put("roleNames", projectMapper.selectUserRoleNames(user.getUserId()));
        return row;
    }
}
