package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessFeishuMapper
{
    List<Map<String, Object>> companies();
    Map<String, Object> company(Long companyDeptId);
    Map<String, Object> lockCompany(Long companyDeptId);
    String lockTenant(String tenantKey);
    int ensureTenant(String tenantKey);
    List<Map<String, Object>> connections();
    Map<String, Object> connection(Long connectionId);
    Map<String, Object> lockConnection(Long connectionId);
    Map<String, Object> connectionForCompany(Long companyDeptId);
    int insertConnection(Map<String, Object> row);
    int bumpVersion(Long connectionId);
    int activate(Map<String, Object> row);
    Map<String, Object> staff(Long userId);
    List<Map<String, Object>> people(Long companyDeptId);
    List<Map<String, Object>> mappings(Long connectionId);
    Map<String, Object> mapping(Long mappingId);
    int mappingConflicts(Map<String, Object> row);
    int insertMapping(Map<String, Object> row);
    int retireMapping(Map<String, Object> row);
    String lastMappedDate(Long mappingId);
    int unmappedStaff(Map<String, Object> row);
    int pendingLocalLeave(Long companyDeptId);
    int crossingLocalLeave(Map<String, Object> row);
    int insertRun(Map<String, Object> row);
    int acquireRun(Map<String, Object> row);
    int finishRun(Map<String, Object> row);
    int releaseRun(Map<String, Object> row);
    List<Map<String, Object>> runs(Long connectionId);
    Map<String, Object> run(Long runId);
    int insertChunk(Map<String, Object> row);
    int expectedChunks(Map<String, Object> row);
    int advanceProgress(Map<String, Object> row);
    Map<String, Object> currentObservation(Map<String, Object> row);
    Map<String, Object> observation(Long observationId);
    int supersedeObservation(Long observationId);
    int touchObservation(Map<String, Object> row);
    int insertObservation(Map<String, Object> row);
    List<Map<String, Object>> records(Map<String, Object> query);
    int insertIssue(Map<String, Object> row);
    List<Map<String, Object>> issues(Long connectionId);
    Map<String, Object> issue(Long issueId);
    int resolveIssue(Map<String, Object> row);
    int openIssues(Long connectionId);
    int insertValidation(Map<String, Object> row);
    List<Map<String, Object>> validations(Long connectionId);
    int validatedCases(Map<String, Object> row);
    int insertAudit(Map<String, Object> row);
    int readerAllowed(@Param("companyDeptId") Long companyDeptId, @Param("userId") Long userId);
    int saveReader(Map<String, Object> row);
    List<Map<String, Object>> readers(Long companyDeptId);
}
