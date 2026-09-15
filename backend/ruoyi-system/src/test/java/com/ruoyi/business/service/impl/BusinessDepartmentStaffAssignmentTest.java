package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
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
import com.ruoyi.business.mapper.BusinessStaffProfileMapper;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.OnlineUserPermissionService;

@ExtendWith(MockitoExtension.class)
class BusinessDepartmentStaffAssignmentTest
{
    @Mock ISysDeptService deptService;
    @Mock ISysUserService userService;
    @Mock OnlineUserPermissionService onlinePermissions;
    @Mock BusinessStaffProfileMapper profileMapper;
    @InjectMocks BusinessDepartmentServiceImpl service;

    void target()
    {
        SysDept department = new SysDept();
        department.setDeptId(200L); department.setParentId(110L); department.setStatus("0");
        SysDept company = new SysDept(); company.setDeptId(110L); company.setParentId(100L);
        when(deptService.selectDeptById(200L)).thenReturn(department);
        when(deptService.selectDeptById(110L)).thenReturn(company);
    }
    SysUser user(long id)
    {
        SysUser user = new SysUser(id); user.setDeptId(110L); user.setStatus("0"); user.setDelFlag("0");
        when(userService.selectUserById(id)).thenReturn(user);
        return user;
    }
    @Test void changesOnlyMembershipAndDeduplicatesAccounts()
    {
        target(); SysUser user=user(125L); user.setPhonenumber("shared-phone");
        SysRole role = new SysRole(); role.setRoleKey("jewelry_admin"); user.setRoles(Collections.singletonList(role));
        when(userService.updateUserProfile(any())).thenReturn(1);
        assertEquals(1,service.assignStaff(200L,Arrays.asList(125L,125L),"admin"));
        ArgumentCaptor<SysUser> patch=ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(patch.capture());
        assertEquals(200L,patch.getValue().getDeptId()); assertEquals(125L,patch.getValue().getUserId());
        assertNull(patch.getValue().getPhonenumber()); assertNull(patch.getValue().getPassword());
        assertNull(patch.getValue().getRoleIds()); assertNull(patch.getValue().getStatus());
        verify(deptService).checkDeptDataScope(200L); verify(userService).checkUserDataScope(125L);
        verify(onlinePermissions).forceReloginAfterCommit(125L);
    }
    @Test void validatesEntireBatchBeforeWriting()
    {
        target(); user(125L); user(126L).setStatus("1");
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Arrays.asList(125L,126L),"admin"));
        verify(userService,never()).updateUserProfile(any()); verifyNoInteractions(onlinePermissions);
    }
    @Test void ignoresExistingMembers()
    {
        target(); user(125L).setDeptId(200L);
        assertEquals(0,service.assignStaff(200L,Collections.singletonList(125L),"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void rejectsProtectedOwner()
    {
        target(); SysRole role=new SysRole(); role.setRoleKey("company_owner");
        user(125L).setRoles(Collections.singletonList(role));
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Collections.singletonList(125L),"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void rejectsSystemAdmin()
    {
        target(); user(1L);
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Collections.singletonList(1L),"admin"));
    }
    @Test void rejectsDepartedEmployee()
    {
        target(); user(125L); BusinessStaffProfile profile=new BusinessStaffProfile(); profile.setEmploymentStatus("LEFT");
        when(profileMapper.selectByUserId(125L)).thenReturn(profile);
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Collections.singletonList(125L),"admin"));
    }
    @Test void enforcesUserScope()
    {
        target(); doThrow(new ServiceException("无权访问")).when(userService).checkUserDataScope(125L);
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Collections.singletonList(125L),"admin"));
        verify(userService,never()).updateUserProfile(any());
    }
    @Test void enforcesDepartmentScope()
    {
        doThrow(new ServiceException("无权访问")).when(deptService).checkDeptDataScope(200L);
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Collections.singletonList(125L),"admin"));
        verifyNoInteractions(userService);
    }
    @Test void rejectsEmptySelection()
    {
        assertThrows(ServiceException.class,()->service.assignStaff(200L,Collections.emptyList(),"admin"));
        verifyNoInteractions(userService);
    }
    @Test void rejectsCompanyAsTarget()
    {
        SysDept company=new SysDept(); company.setDeptId(110L);company.setParentId(100L);company.setStatus("0");
        SysDept group=new SysDept();group.setDeptId(100L);group.setParentId(0L);
        when(deptService.selectDeptById(110L)).thenReturn(company);when(deptService.selectDeptById(100L)).thenReturn(group);
        assertThrows(ServiceException.class,()->service.assignStaff(110L,Collections.singletonList(125L),"admin"));
        verifyNoInteractions(userService);
    }
}
