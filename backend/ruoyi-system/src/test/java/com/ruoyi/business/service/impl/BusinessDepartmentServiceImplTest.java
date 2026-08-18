package com.ruoyi.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class BusinessDepartmentServiceImplTest
{
    @Mock private ISysDeptService deptService;
    @Mock private ISysUserService userService;
    @InjectMocks private BusinessDepartmentServiceImpl service;

    @Test
    void createsDepartmentUnderActiveParent()
    {
        SysDept parent = dept(100L, 0L, "公司", "0");
        parent.setAncestors("0");
        SysDept input = dept(null, 100L, "运营部", "0");
        input.setOrderNum(3);
        when(deptService.selectDeptById(100L)).thenReturn(parent);
        when(deptService.checkDeptNameUnique(input)).thenReturn(true);
        when(deptService.insertDept(input)).thenAnswer(invocation -> { input.setDeptId(210L); return 1; });
        when(deptService.selectDeptById(210L)).thenReturn(input);

        SysDept created = service.createDepartment(input, "jianglan");

        assertEquals(210L, created.getDeptId());
        assertEquals("jianglan", input.getCreateBy());
    }

    @Test
    void rootDepartmentCannotBeDeleted()
    {
        when(deptService.selectDeptById(100L)).thenReturn(dept(100L, 0L, "公司", "0"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.deleteDepartment(100L));

        assertTrue(error.getMessage().contains("受保护"));
        verify(deptService, never()).deleteDeptById(100L);
    }

    @Test
    void resolvesCreatedDepartmentWhenGeneratedKeyIsUnavailable()
    {
        SysDept parent = dept(100L, 0L, "公司", "0");
        parent.setAncestors("0");
        SysDept input = dept(null, 100L, "市场部", "0");
        input.setOrderNum(4);
        SysDept stored = dept(211L, 100L, "市场部", "0");
        stored.setOrderNum(4);
        when(deptService.selectDeptById(100L)).thenReturn(parent);
        when(deptService.checkDeptNameUnique(input)).thenReturn(true);
        when(deptService.insertDept(input)).thenReturn(1);
        when(deptService.selectDeptList(org.mockito.ArgumentMatchers.any(SysDept.class)))
            .thenReturn(Collections.singletonList(stored));

        SysDept created = service.createDepartment(input, "jianglan");

        assertEquals(211L, created.getDeptId());
    }

    @Test
    void departmentWithPeopleCannotBeDeleted()
    {
        when(deptService.selectDeptById(210L)).thenReturn(dept(210L, 120L, "运营部", "0"));
        when(deptService.selectDeptById(120L)).thenReturn(dept(120L, 100L, "业务公司", "0"));
        when(deptService.hasChildByDeptId(210L)).thenReturn(false);
        when(deptService.checkDeptExistUser(210L)).thenReturn(true);

        ServiceException error = assertThrows(ServiceException.class, () -> service.deleteDepartment(210L));

        assertTrue(error.getMessage().contains("仍有人员"));
        verify(deptService, never()).deleteDeptById(210L);
    }

    @Test
    void companyNodeCannotBeDeleted()
    {
        when(deptService.selectDeptById(110L)).thenReturn(dept(110L, 100L, "上海美丸文化公司", "0"));
        when(deptService.selectDeptById(100L)).thenReturn(dept(100L, 0L, "美丸集团", "0"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.deleteDepartment(110L));

        assertTrue(error.getMessage().contains("受保护"));
        verify(deptService, never()).deleteDeptById(110L);
    }

    @Test
    void bindsLeaderContactFromSelectedUser()
    {
        SysDept parent = dept(100L, 0L, "公司", "0");
        parent.setAncestors("0");
        SysDept input = dept(null, 100L, "运营部", "0");
        input.setOrderNum(3);
        input.setLeaderUserId(9L);
        input.setLeader("伪造姓名");
        input.setPhone("00000000000");
        SysUser leader = new SysUser();
        leader.setUserId(9L);
        leader.setUserName("operator9");
        leader.setNickName("负责人九");
        leader.setPhonenumber("13800138000");
        leader.setEmail("leader9@example.com");
        leader.setStatus("0");
        leader.setDelFlag("0");
        when(userService.selectUserById(9L)).thenReturn(leader);
        when(deptService.selectDeptById(100L)).thenReturn(parent);
        when(deptService.checkDeptNameUnique(input)).thenReturn(true);
        when(deptService.insertDept(input)).thenAnswer(invocation -> { input.setDeptId(212L); return 1; });
        when(deptService.selectDeptById(212L)).thenReturn(input);

        service.createDepartment(input, "jianglan");

        assertEquals("负责人九", input.getLeader());
        assertEquals("13800138000", input.getPhone());
        assertEquals("leader9@example.com", input.getEmail());
    }

    @Test
    void rejectsDisabledLeader()
    {
        SysDept input = dept(null, 100L, "运营部", "0");
        input.setLeaderUserId(9L);
        SysUser leader = new SysUser();
        leader.setUserId(9L);
        leader.setStatus("1");
        leader.setDelFlag("0");
        when(userService.selectUserById(9L)).thenReturn(leader);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.createDepartment(input, "jianglan"));

        assertTrue(error.getMessage().contains("已停用"));
        verify(deptService, never()).insertDept(input);
    }

    private SysDept dept(Long id, Long parentId, String name, String status)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setDeptName(name);
        dept.setStatus(status);
        dept.setOrderNum(0);
        return dept;
    }
}
