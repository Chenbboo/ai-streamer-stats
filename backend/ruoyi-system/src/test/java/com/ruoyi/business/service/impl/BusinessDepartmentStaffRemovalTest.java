package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.OnlineUserPermissionService;

@ExtendWith(MockitoExtension.class)
class BusinessDepartmentStaffRemovalTest
{
    @org.mockito.Mock private com.ruoyi.business.service.BusinessCompanyAccessService companyAccess;

    @Mock ISysDeptService deptService;
    @Mock ISysUserService userService;
    @Mock OnlineUserPermissionService onlinePermissions;
    @InjectMocks BusinessDepartmentServiceImpl service;

    void dept(long id, long parentId)
    {
        SysDept dept = new SysDept(); dept.setDeptId(id); dept.setParentId(parentId); dept.setStatus("0");
        when(deptService.selectDeptById(id)).thenReturn(dept);
    }
    void hierarchy() { dept(200,110); dept(110,100); dept(100,0); }
    SysUser employee(long id, long deptId)
    {
        SysUser user = new SysUser(id); user.setDeptId(deptId); user.setDelFlag("0"); user.setStatus("0");
        when(userService.selectUserById(id)).thenReturn(user); return user;
    }
    @Test void returnsEmployeeToCompanyPreservingAccountAndRoles()
    {
        hierarchy(); employee(125,200).setStatus("1"); // Disabled accounts can still be removed from a department.
        when(userService.updateUserProfile(any())).thenReturn(1);
        service.removeStaff(200L,125L,"admin");
        ArgumentCaptor<SysUser> patch=ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(patch.capture());
        assertEquals(110L,patch.getValue().getDeptId()); assertEquals(125L,patch.getValue().getUserId());
        assertNull(patch.getValue().getPassword());assertNull(patch.getValue().getRoleIds());
        assertNull(patch.getValue().getStatus());assertNull(patch.getValue().getDelFlag());
        assertNull(patch.getValue().getPhonenumber());assertNull(patch.getValue().getNickName());
        verify(companyAccess,org.mockito.Mockito.atLeastOnce()).requireDepartment(200L);verify(companyAccess,org.mockito.Mockito.atLeastOnce()).requireDepartment(110L);
        verify(companyAccess).requireStaff(125L);verify(onlinePermissions).forceReloginAfterCommit(125L);
    }
    @Test void nestedDepartmentReturnsToCompanyRatherThanParentDepartment()
    {
        hierarchy();dept(210,200);employee(125,210);when(userService.updateUserProfile(any())).thenReturn(1);
        service.removeStaff(210L,125L,"admin");
        verify(userService).updateUserProfile(argThat(user->Long.valueOf(110L).equals(user.getDeptId())));
    }
    @Test void rejectsStaleMembership()
    {
        hierarchy();employee(125,201);
        assertThrows(ServiceException.class,()->service.removeStaff(200L,125L,"admin"));
        verify(userService,never()).updateUserProfile(any());verifyNoInteractions(onlinePermissions);
    }
    @Test void rejectsCompanyLevelRemoval()
    {
        dept(110,100);dept(100,0);
        assertThrows(ServiceException.class,()->service.removeStaff(110L,125L,"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void rejectsProtectedAccounts()
    {
        hierarchy();SysRole role=new SysRole();role.setRoleKey("company_owner");
        employee(125,200).setRoles(Collections.singletonList(role));
        assertThrows(ServiceException.class,()->service.removeStaff(200L,125L,"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void rejectsOutOfScopeUser()
    {
        doThrow(new ServiceException("无权访问")).when(companyAccess).requireStaff(125L);
        assertThrows(ServiceException.class,()->service.removeStaff(200L,125L,"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void rejectsOutOfScopeCompany()
    {
        dept(200,110);doNothing().when(companyAccess).requireDepartment(200L);
        doThrow(new ServiceException("无权访问")).when(companyAccess).requireDepartment(110L);
        assertThrows(ServiceException.class,()->service.removeStaff(200L,125L,"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void failedUpdateDoesNotInvalidateSession()
    {
        hierarchy();employee(125,200);
        assertThrows(ServiceException.class,()->service.removeStaff(200L,125L,"admin"));
        verifyNoInteractions(onlinePermissions);
    }
}
