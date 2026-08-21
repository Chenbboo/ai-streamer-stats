package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.business.service.IBusinessStaffService;
import com.ruoyi.business.domain.BusinessStaffProfile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping("/business/staff")
public class BusinessStaffController extends BaseController
{
    @Autowired
    private IBusinessStaffService staffService;

    @PreAuthorize("@ss.hasPermi('business:staff:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser query)
    {
        startPage();
        return staffService.listStaff(query, SecurityUtils.getUserId(), SecurityUtils.isAdmin());
    }

    @PreAuthorize("@ss.hasPermi('business:staff:list')")
    @GetMapping("/departments")
    public AjaxResult departments()
    {
        return success(staffService.departmentOptions());
    }

    @PreAuthorize("@ss.hasPermi('business:staff:list')")
    @GetMapping("/options")
    public AjaxResult options()
    {
        return success(staffService.listOptions());
    }

    @PreAuthorize("@ss.hasPermi('business:staff:list')")
    @GetMapping("/{userId}/projects")
    public AjaxResult projects(@PathVariable Long userId)
    {
        boolean administrator = SecurityUtils.isAdmin();
        boolean boss = administrator || SecurityUtils.hasPermi("business:boss:view");
        return success(staffService.projectResponsibilities(userId, SecurityUtils.getUserId(), administrator, boss));
    }

    @PreAuthorize("@ss.hasPermi('business:staff:manage')")
    @Log(title = "公司人员", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BusinessStaffProfile input)
    {
        return success(staffService.createStaff(input, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('business:staff:manage')")
    @Log(title = "公司人员", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BusinessStaffProfile input)
    {
        return success(staffService.updateStaff(input, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('business:staff:manage')")
    @Log(title = "公司人员状态", businessType = BusinessType.UPDATE)
    @PutMapping("/status")
    public AjaxResult status(@RequestBody Map<String, Object> body)
    {
        staffService.changeStatus(longValue(body.get("userId")), text(body.get("status")), getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('business:staff:manage')")
    @Log(title = "公司人员密码", businessType = BusinessType.UPDATE)
    @PutMapping("/password")
    public AjaxResult password(@RequestBody Map<String, Object> body)
    {
        staffService.resetPassword(longValue(body.get("userId")), text(body.get("password")), getUsername());
        return success();
    }

    private Long longValue(Object value)
    {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }
}
