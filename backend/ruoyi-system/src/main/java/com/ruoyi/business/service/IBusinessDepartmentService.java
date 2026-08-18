package com.ruoyi.business.service;

import java.util.List;
import com.ruoyi.common.core.domain.entity.SysDept;

public interface IBusinessDepartmentService
{
    List<SysDept> listDepartments(SysDept query);
    SysDept createDepartment(SysDept input, String operatorName);
    SysDept updateDepartment(SysDept input, String operatorName);
    void updateSort(String[] deptIds, String[] orderNums);
    void deleteDepartment(Long deptId);
}
