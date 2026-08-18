package com.ruoyi.business.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.business.service.IBusinessDepartmentService;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysUserService;

@Service
public class BusinessDepartmentServiceImpl implements IBusinessDepartmentService
{
    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysUserService userService;

    @Override
    public List<SysDept> listDepartments(SysDept query)
    {
        return deptService.buildDeptTree(deptService.selectDeptList(query == null ? new SysDept() : query));
    }

    @Override
    @Transactional
    public SysDept createDepartment(SysDept input, String operatorName)
    {
        bindLeader(input);
        validateCommon(input);
        if (input.getParentId() == null) throw new ServiceException("请选择上级部门");
        requireActiveParent(input.getParentId());
        if (!deptService.checkDeptNameUnique(input)) throw new ServiceException("同一上级部门下已存在同名部门");
        input.setStatus(StringUtils.isBlank(input.getStatus()) ? "0" : input.getStatus());
        input.setCreateBy(operatorName);
        if (deptService.insertDept(input) != 1) throw new ServiceException("新增部门失败");
        if (input.getDeptId() != null)
        {
            return deptService.selectDeptById(input.getDeptId());
        }

        // Some existing installations do not return generated keys for sys_dept.
        // The sibling name is unique, so resolve the inserted row deterministically.
        SysDept lookup = new SysDept();
        lookup.setParentId(input.getParentId());
        lookup.setDeptName(input.getDeptName());
        for (SysDept candidate : deptService.selectDeptList(lookup))
        {
            if (input.getParentId().equals(candidate.getParentId())
                && input.getDeptName().equals(candidate.getDeptName()))
            {
                return candidate;
            }
        }
        throw new ServiceException("新增部门后未能读取部门信息");
    }

    @Override
    @Transactional
    public SysDept updateDepartment(SysDept input, String operatorName)
    {
        if (input == null || input.getDeptId() == null) throw new ServiceException("部门ID不能为空");
        SysDept existing = requireDepartment(input.getDeptId());
        ensureNonRoot(existing);
        bindLeader(input);
        validateCommon(input);
        if (input.getParentId() == null) throw new ServiceException("请选择上级部门");
        SysDept existingParent = requireDepartment(existing.getParentId());
        boolean companyNode = Long.valueOf(0L).equals(existingParent.getParentId());
        if (companyNode && !existing.getParentId().equals(input.getParentId()))
            throw new ServiceException("公司节点不能移动到其他部门");
        if (companyNode && "1".equals(input.getStatus()))
            throw new ServiceException("公司节点不能停用");
        if (input.getDeptId().equals(input.getParentId())) throw new ServiceException("上级部门不能是当前部门");
        SysDept parent = requireActiveParent(input.getParentId());
        if (containsAncestor(parent.getAncestors(), input.getDeptId())) throw new ServiceException("不能选择当前部门的下级作为上级部门");
        if (!deptService.checkDeptNameUnique(input)) throw new ServiceException("同一上级部门下已存在同名部门");
        if ("1".equals(input.getStatus()) && deptService.selectNormalChildrenDeptById(input.getDeptId()) > 0)
            throw new ServiceException("请先停用下级部门");
        input.getParams().put("syncLeaderUser", Boolean.TRUE);
        input.setUpdateBy(operatorName);
        if (deptService.updateDept(input) != 1) throw new ServiceException("修改部门失败");
        return deptService.selectDeptById(input.getDeptId());
    }

    @Override
    public void updateSort(String[] deptIds, String[] orderNums)
    {
        if (deptIds == null || orderNums == null || deptIds.length == 0 || deptIds.length != orderNums.length)
            throw new ServiceException("部门排序数据不正确");
        for (int i = 0; i < deptIds.length; i++)
        {
            Long.valueOf(deptIds[i]);
            int order = Integer.parseInt(orderNums[i]);
            if (order < 0) throw new ServiceException("部门排序不能为负数");
        }
        deptService.updateDeptSort(deptIds, orderNums);
    }

    @Override
    @Transactional
    public void deleteDepartment(Long deptId)
    {
        SysDept dept = requireDepartment(deptId);
        ensureNonRoot(dept);
        SysDept parent = requireDepartment(dept.getParentId());
        if (Long.valueOf(0L).equals(parent.getParentId())) throw new ServiceException("公司节点为受保护节点");
        if (deptService.hasChildByDeptId(deptId)) throw new ServiceException("存在下级部门，不允许删除");
        if (deptService.checkDeptExistUser(deptId)) throw new ServiceException("部门仍有人员，不允许删除");
        if (deptService.deleteDeptById(deptId) != 1) throw new ServiceException("删除部门失败");
    }

    private void validateCommon(SysDept input)
    {
        if (input == null || StringUtils.isBlank(input.getDeptName())) throw new ServiceException("部门名称不能为空");
        if (input.getDeptName().length() > 30) throw new ServiceException("部门名称不能超过30个字符");
        if (input.getOrderNum() == null || input.getOrderNum() < 0) throw new ServiceException("显示顺序不正确");
        if (!StringUtils.isBlank(input.getStatus()) && !"0".equals(input.getStatus()) && !"1".equals(input.getStatus()))
            throw new ServiceException("部门状态不正确");
    }

    private void bindLeader(SysDept input)
    {
        if (input == null) return;
        if (input.getLeaderUserId() == null)
        {
            input.setLeader("");
            input.setPhone("");
            input.setEmail("");
            return;
        }
        SysUser leader = userService.selectUserById(input.getLeaderUserId());
        if (leader == null || !"0".equals(leader.getDelFlag())) throw new ServiceException("所选负责人不存在");
        if (!"0".equals(leader.getStatus())) throw new ServiceException("所选负责人账号已停用");
        input.setLeader(StringUtils.isBlank(leader.getNickName()) ? leader.getUserName() : leader.getNickName());
        input.setPhone(StringUtils.defaultString(leader.getPhonenumber()));
        input.setEmail(StringUtils.defaultString(leader.getEmail()));
    }

    private SysDept requireDepartment(Long deptId)
    {
        if (deptId == null) throw new ServiceException("部门ID不能为空");
        SysDept dept = deptService.selectDeptById(deptId);
        if (dept == null || "2".equals(dept.getDelFlag())) throw new ServiceException("部门不存在");
        return dept;
    }

    private SysDept requireActiveParent(Long parentId)
    {
        SysDept parent = requireDepartment(parentId);
        if (!"0".equals(parent.getStatus())) throw new ServiceException("上级部门已停用");
        return parent;
    }

    private void ensureNonRoot(SysDept dept)
    {
        if (Long.valueOf(0L).equals(dept.getParentId())) throw new ServiceException("公司根部门为受保护部门");
    }

    private boolean containsAncestor(String ancestors, Long deptId)
    {
        if (StringUtils.isBlank(ancestors)) return false;
        for (String ancestor : ancestors.split(",")) if (String.valueOf(deptId).equals(ancestor)) return true;
        return false;
    }
}
