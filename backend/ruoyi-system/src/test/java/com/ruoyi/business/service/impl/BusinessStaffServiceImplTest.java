package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.business.mapper.BusinessProjectMapper;
import com.ruoyi.business.mapper.BusinessStaffProfileMapper;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class BusinessStaffServiceImplTest
{
    @Mock private ISysUserService userService;
    @Mock private ISysDeptService deptService;
    @Mock private BusinessProjectMapper projectMapper;
    @Mock private BusinessStaffProfileMapper profileMapper;
    @InjectMocks private BusinessStaffServiceImpl service;

    @Test
    void staffDirectoryOnlyEnablesCostActionsForOwnedCompany()
    {
        SysUser shanghaiStaff = new SysUser(147L);
        shanghaiStaff.setUserName("shanghai147"); shanghaiStaff.setNickName("上海员工"); shanghaiStaff.setStatus("0");
        SysUser vietnamStaff = new SysUser(148L);
        vietnamStaff.setUserName("vietnam148"); vietnamStaff.setNickName("越南员工"); vietnamStaff.setStatus("0");
        BusinessStaffProfile shanghaiProfile = new BusinessStaffProfile();
        shanghaiProfile.setUserId(147L); shanghaiProfile.setCompanyDeptId(110L);
        shanghaiProfile.setCompanyName("上海美丸文化公司"); shanghaiProfile.setCompanyLeaderUserId(120L);
        shanghaiProfile.setEmploymentStatus("ACTIVE");
        BusinessStaffProfile vietnamProfile = new BusinessStaffProfile();
        vietnamProfile.setUserId(148L); vietnamProfile.setCompanyDeptId(111L);
        vietnamProfile.setCompanyName("越南meimaru公司"); vietnamProfile.setCompanyLeaderUserId(143L);
        vietnamProfile.setEmploymentStatus("ACTIVE");
        when(userService.selectUserList(any())).thenReturn(Arrays.asList(shanghaiStaff, vietnamStaff));
        when(profileMapper.selectByUserIds(any())).thenReturn(Arrays.asList(shanghaiProfile, vietnamProfile));

        List<?> rows = service.listStaff(new SysUser(), 120L, false, true, true).getRows();

        @SuppressWarnings("unchecked") Map<String, Object> shanghai = (Map<String, Object>) rows.get(0);
        @SuppressWarnings("unchecked") Map<String, Object> vietnam = (Map<String, Object>) rows.get(1);
        assertEquals(true, shanghai.get("canViewCost"));
        assertEquals(true, shanghai.get("canManageCost"));
        assertEquals(false, vietnam.get("canViewCost"));
        assertEquals(false, vietnam.get("canManageCost"));
    }

    @Test
    void administratorCanManageCostsForAllActiveCompanies()
    {
        SysUser shanghaiStaff = new SysUser(147L);
        shanghaiStaff.setUserName("shanghai147"); shanghaiStaff.setStatus("0");
        SysUser vietnamStaff = new SysUser(148L);
        vietnamStaff.setUserName("vietnam148"); vietnamStaff.setStatus("0");
        BusinessStaffProfile shanghaiProfile = new BusinessStaffProfile();
        shanghaiProfile.setUserId(147L); shanghaiProfile.setCompanyLeaderUserId(120L);
        shanghaiProfile.setEmploymentStatus("ACTIVE");
        BusinessStaffProfile vietnamProfile = new BusinessStaffProfile();
        vietnamProfile.setUserId(148L); vietnamProfile.setCompanyLeaderUserId(143L);
        vietnamProfile.setEmploymentStatus("ACTIVE");
        when(userService.selectUserList(any())).thenReturn(Arrays.asList(shanghaiStaff, vietnamStaff));
        when(profileMapper.selectByUserIds(any())).thenReturn(Arrays.asList(shanghaiProfile, vietnamProfile));

        List<?> rows = service.listStaff(new SysUser(), 1L, true, true, true).getRows();

        @SuppressWarnings("unchecked") Map<String, Object> shanghai = (Map<String, Object>) rows.get(0);
        @SuppressWarnings("unchecked") Map<String, Object> vietnam = (Map<String, Object>) rows.get(1);
        assertEquals(true, shanghai.get("canViewCost"));
        assertEquals(true, shanghai.get("canManageCost"));
        assertEquals(true, vietnam.get("canViewCost"));
        assertEquals(true, vietnam.get("canManageCost"));
    }

    @Test
    void administratorCanManageCostForEmployedStaffWithDisabledLogin()
    {
        SysUser disabledStaff = new SysUser(111L);
        disabledStaff.setUserName("disabled111"); disabledStaff.setStatus("1");
        BusinessStaffProfile profile = new BusinessStaffProfile();
        profile.setUserId(111L); profile.setEmploymentStatus("ACTIVE");
        when(userService.selectUserList(any())).thenReturn(Arrays.asList(disabledStaff));
        when(profileMapper.selectByUserIds(any())).thenReturn(Arrays.asList(profile));

        List<?> rows = service.listStaff(new SysUser(), 1L, true, true, true).getRows();

        @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) rows.get(0);
        assertEquals(true, row.get("canViewCost"));
        assertEquals(true, row.get("canManageCost"));
    }

    @Test
    void projectOwnerCanManageActiveStaffCostWithoutFullPeopleManagement()
    {
        SysUser staff = new SysUser(147L);
        staff.setUserName("staff147"); staff.setStatus("0");
        BusinessStaffProfile profile = new BusinessStaffProfile();
        profile.setUserId(147L); profile.setEmploymentStatus("ACTIVE");
        when(userService.selectUserList(any())).thenReturn(Arrays.asList(staff));
        when(profileMapper.selectByUserIds(any())).thenReturn(Arrays.asList(profile));
        when(projectMapper.selectManagedProjectMemberUserIds(134L)).thenReturn(Arrays.asList(147L));

        List<?> rows = service.listStaff(new SysUser(), 134L, false, false, true).getRows();

        @SuppressWarnings("unchecked") Map<String, Object> row = (Map<String, Object>) rows.get(0);
        assertEquals(true, row.get("canViewCost"));
        assertEquals(true, row.get("canManageCost"));
    }

    @Test
    void creatingStaffAlwaysAssignsProjectAndCompanyStaffRoles()
    {
        BusinessStaffProfile input = new BusinessStaffProfile();
        input.setUserName("newstaff");
        input.setNickName("新员工");
        input.setPassword("abc12345");
        input.setDeptId(110L);
        SysDept company = new SysDept();
        company.setDeptId(110L);
        company.setParentId(100L);
        company.setStatus("0");
        when(deptService.selectDeptById(110L)).thenReturn(company);
        when(userService.checkUserNameUnique(input)).thenReturn(true);
        when(projectMapper.selectRoleIdByKey("project_user")).thenReturn(18L);
        when(projectMapper.selectRoleIdByKey("company_staff")).thenReturn(20L);
        when(userService.insertUser(input)).thenAnswer(invocation -> { input.setUserId(99L); return 1; });
        when(profileMapper.upsert(input)).thenReturn(1);
        when(profileMapper.selectByUserId(99L)).thenReturn(input);
        when(userService.selectUserById(99L)).thenReturn(input);
        when(projectMapper.countUserRoleByKey(99L, "company_owner")).thenReturn(0);
        when(projectMapper.selectUserRoleNames(99L)).thenReturn("项目参与人员");

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) service.createStaff(input, "jianglan");

        assertEquals(99L, created.get("userId"));
        assertEquals(18L, input.getRoleIds()[0]);
        assertEquals(20L, input.getRoleIds()[1]);
        assertEquals("0", input.getStatus());
        assertEquals("jianglan", input.getCreateBy());
        assertEquals("项目参与人员", created.get("roleNames"));
    }

    @Test
    void ownerAccountCannotBeDisabled()
    {
        SysUser owner = new SysUser(142L);
        when(userService.selectUserById(142L)).thenReturn(owner);
        when(projectMapper.countUserRoleByKey(142L, "company_owner")).thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.changeStatus(142L, "1", "wangfuzhang"));

        assertTrue(error.getMessage().contains("受保护"));
        verify(userService, never()).updateUserStatus(any());
    }

    @Test
    void editingStaffUsesProfileUpdateAndKeepsRoles()
    {
        SysUser existing = new SysUser(88L);
        existing.setUserName("staff88");
        BusinessStaffProfile input = new BusinessStaffProfile();
        input.setUserId(88L);
        input.setNickName("员工八八");
        input.setDeptId(110L);
        input.setPhonenumber("");
        input.setEmail("");
        SysDept company = new SysDept();
        company.setDeptId(110L);
        company.setParentId(100L);
        company.setStatus("0");
        when(deptService.selectDeptById(110L)).thenReturn(company);
        when(userService.selectUserById(88L)).thenReturn(existing, input);
        when(projectMapper.countUserRoleByKey(88L, "company_owner")).thenReturn(0);
        when(userService.updateUserProfile(any())).thenReturn(1);
        when(profileMapper.upsert(input)).thenReturn(1);
        when(profileMapper.selectByUserId(88L)).thenReturn(null, input);

        service.updateStaff(input, "jianglan");

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(patch.capture());
        assertEquals("员工八八", patch.getValue().getNickName());
        verify(userService, never()).updateUser(any());
        verify(profileMapper).syncDepartmentLeader(88L);
    }

    @Test
    void protectedOwnerCanEditContactButCannotMoveOrganization()
    {
        SysUser existing = new SysUser(142L);
        existing.setDeptId(100L);
        existing.setUserName("jianglan");
        BusinessStaffProfile input = new BusinessStaffProfile();
        input.setUserId(142L);
        input.setDeptId(111L);
        input.setNickName("江澜");
        input.setPhoneCountryCode("+86");
        input.setPhonenumber("13800138000");
        input.setEmail("jianglan@example.com");
        BusinessStaffProfile existingProfile = new BusinessStaffProfile();
        existingProfile.setUserId(142L);
        existingProfile.setManagerUserId(1L);
        existingProfile.setEmploymentStatus("ACTIVE");
        SysUser manager = new SysUser(1L);
        manager.setStatus("0");
        manager.setDelFlag("0");
        SysDept group = new SysDept();
        group.setDeptId(100L);
        group.setStatus("0");
        when(userService.selectUserById(142L)).thenReturn(existing, input);
        when(userService.selectUserById(1L)).thenReturn(manager);
        when(userService.checkPhoneUnique(input)).thenReturn(true);
        when(userService.checkEmailUnique(input)).thenReturn(true);
        when(deptService.selectDeptById(100L)).thenReturn(group);
        when(projectMapper.countUserRoleByKey(142L, "company_owner")).thenReturn(1);
        when(profileMapper.selectByUserId(142L)).thenReturn(existingProfile, input);
        when(userService.updateUserProfile(any())).thenReturn(1);
        when(profileMapper.upsert(input)).thenReturn(1);

        service.updateStaff(input, "admin");

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(patch.capture());
        assertEquals(100L, patch.getValue().getDeptId());
        assertEquals("13800138000", patch.getValue().getPhonenumber());
        assertEquals(1L, input.getManagerUserId());
    }

    @Test
    void bossCanSeeForeignProjectNameWithoutOperationalDetails()
    {
        SysUser staff = new SysUser(88L);
        when(userService.selectUserById(88L)).thenReturn(staff);
        Map<String, Object> own = responsibility(1L, 142L, "江澜", "OWNER", "0", "ACTIVE", 4, 2);
        Map<String, Object> foreign = responsibility(2L, 143L, "王赋章", "MEMBER", "0", "PLANNING", 3, 1);
        when(projectMapper.selectStaffResponsibilities(88L, 142L, false, true)).thenReturn(Arrays.asList(own, foreign));

        Map<String, Object> result = service.projectResponsibilities(88L, 142L, false, true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) result.get("projects");
        assertEquals(2, projects.size());
        assertEquals("王赋章", projects.get(1).get("initiatorName"));
        assertEquals(false, projects.get(1).get("canOpen"));
        assertEquals(null, projects.get(1).get("status"));
        assertEquals(null, projects.get(1).get("assignedTaskCount"));
        assertEquals(4, result.get("assignedTaskCount"));
        assertEquals(2, result.get("completedTaskCount"));
    }

    private Map<String, Object> responsibility(Long projectId, Long initiatorUserId, String initiatorName,
        String role, String responsibilityStatus, String projectStatus, int assigned, int completed)
    {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("projectId", projectId);
        row.put("projectName", "项目" + projectId);
        row.put("initiatorUserId", initiatorUserId);
        row.put("initiatorName", initiatorName);
        row.put("responsibilityRole", role);
        row.put("responsibilityStatus", responsibilityStatus);
        row.put("status", projectStatus);
        row.put("assignedTaskCount", assigned);
        row.put("completedTaskCount", completed);
        return row;
    }
}
