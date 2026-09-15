package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessPublicExpenseMapper
{
    List<Long> selectDailyBills();
    List<Map<String,Object>> selectDailyRows(Long billId);
    int upsertDailyRow(Map<String,Object> row);
    int deleteDailyRow(Map<String,Object> row);
    List<Map<String,Object>> selectUnrecognizedDailyDates(Long billId);
    Map<String,Object> sumDailyCost(@Param("projectId") Long projectId,@Param("bizDate") java.util.Date bizDate);
    Map<String,Object> dailyProjectSummary(@Param("projectId") Long projectId,@Param("month") String month);
    List<Map<String,Object>> selectProjectDailyCosts(@Param("projectId") Long projectId,@Param("month") String month);
    List<Map<String,Object>> selectCompanies(Long userId);
    Map<String,Object> selectCompanyForUpdate(Long companyDeptId);
    List<Map<String,Object>> selectOwners(Long companyDeptId);
    List<Map<String,Object>> selectDepartments(Long companyDeptId);
    List<Map<String,Object>> selectProjects(@Param("companyDeptId") Long companyDeptId,@Param("ownerUserId") Long ownerUserId,@Param("month") String month,@Param("currency") String currency);
    List<Map<String,Object>> selectPolicies(Long companyDeptId);
    Map<String,Object> selectPolicyForUpdate(Long policyId);
    int insertPolicy(Map<String,Object> row);
    int updatePolicy(Map<String,Object> row);
    Map<String,Object> selectMonth(@Param("companyDeptId") Long companyDeptId,@Param("month") String month,@Param("currency") String currency);
    Map<String,Object> selectBill(Long billId);
    Map<String,Object> selectBillForUpdate(Long billId);
    List<Map<String,Object>> selectHistory(Long companyDeptId);
    int insertMonth(Map<String,Object> row);
    int updateBill(Map<String,Object> row);
    List<Map<String,Object>> selectEntries(Long billId);
    int insertEntry(Map<String,Object> row);
    int updateEntry(Map<String,Object> row);
    List<Map<String,Object>> selectOwnerAllocations(Long billId);
    List<Map<String,Object>> selectOwnerBills(@Param("userId") Long userId,@Param("month") String month);
    Map<String,Object> selectOwner(Long allocationId);
    int deleteOwners(Long billId);
    int insertOwner(Map<String,Object> row);
    int updateOwner(Map<String,Object> row);
    List<Map<String,Object>> selectProjectAllocations(Long allocationId);
    int deleteBillProjects(Long billId);
    int deleteOwnerProjects(Long allocationId);
    int insertProject(Map<String,Object> row);
    int attachFact(Map<String,Object> row);
    int insertAdjustment(Map<String,Object> row);
    Map<String,Object> selectAdjustmentByRequest(@Param("billId") Long billId,@Param("requestKey") String requestKey);
    List<Map<String,Object>> selectAdjustments(@Param("billId") Long billId,@Param("projectId") Long projectId);
    int attachAdjustmentFact(Map<String,Object> row);
    int insertEvent(Map<String,Object> row);
    List<Map<String,Object>> selectEvents(@Param("companyDeptId") Long companyDeptId,@Param("billId") Long billId);
    Map<String,Object> readProjectCosts(@Param("projectId") Long projectId,@Param("month") String month);
    List<Map<String,Object>> selectProjectHistory(Long projectId);
    int countProjectPending(Long projectId);
    int countProjectUnsubmitted(Long projectId);
}
