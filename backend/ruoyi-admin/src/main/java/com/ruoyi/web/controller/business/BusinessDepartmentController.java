package com.ruoyi.web.controller.business;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/department")
public class BusinessDepartmentController extends BaseController
{
    @Autowired
    private IBusinessDepartmentService departmentService;

    @Autowired
    private IBusinessStaffService staffService;

    @PreAuthorize("@ss.hasPermi('business:department:list')")
    @GetMapping("/list")
    public AjaxResult list(SysDept query)
    {
        List<SysDept> departments = departmentService.listDepartments(query);
        return success(departments);
    }

    @PreAuthorize("@ss.hasPermi('business:department:list')")
    @GetMapping("/staff")
    public AjaxResult staff()
    {
        return success(staffService.listStaff(new com.ruoyi.common.core.domain.entity.SysUser(),
            SecurityUtils.getUserId(), SecurityUtils.isAdmin()).getRows());
    }

    @PreAuthorize("@ss.hasPermi('business:department:manage')")
    @Log(title = "公司部门", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysDept input)
    {
        return success(departmentService.createDepartment(input, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('business:department:manage')")
    @Log(title = "公司部门", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SysDept input)
    {
        return success(departmentService.updateDepartment(input, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('business:department:manage')")
    @Log(title = "公司部门排序", businessType = BusinessType.UPDATE)
    @PutMapping("/sort")
    public AjaxResult sort(@RequestBody Map<String, String> body)
    {
        String deptIds = body.get("deptIds");
        String orderNums = body.get("orderNums");
        departmentService.updateSort(deptIds == null ? null : deptIds.split(","),
            orderNums == null ? null : orderNums.split(","));
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:department:manage')")
    @Log(title = "公司部门", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    public AjaxResult remove(@PathVariable Long deptId)
    {
        departmentService.deleteDepartment(deptId);
        return success();
    }
}
