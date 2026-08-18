package com.ruoyi.business.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface BusinessAiMapper
{
    int insertConversation(Map<String, Object> row);
    Map<String, Object> selectConversation(@Param("conversationId") Long conversationId,
        @Param("userId") Long userId, @Param("roleCode") String roleCode);
    int touchConversation(@Param("conversationId") Long conversationId);
    int insertMessage(Map<String, Object> row);
    List<Map<String, Object>> selectMessages(@Param("conversationId") Long conversationId,
        @Param("userId") Long userId, @Param("limit") Integer limit);
    int insertRun(Map<String, Object> row);
    int updateRunMode(@Param("runId") Long runId, @Param("executionMode") String executionMode);
    int finishRun(@Param("runId") Long runId, @Param("responseMessageId") Long responseMessageId,
        @Param("status") String status, @Param("errorMessage") String errorMessage);
    int insertToolCall(Map<String, Object> row);
    int insertActionRequest(Map<String, Object> row);
    Map<String, Object> selectActionRequest(@Param("actionRequestId") Long actionRequestId,
        @Param("userId") Long userId);
    List<Map<String, Object>> selectConversationActionRequests(@Param("conversationId") Long conversationId,
        @Param("userId") Long userId);
    Map<String, Object> selectLatestActionRequest(@Param("conversationId") Long conversationId,
        @Param("userId") Long userId, @Param("actionCode") String actionCode);
    int confirmActionRequest(@Param("actionRequestId") Long actionRequestId, @Param("userId") Long userId);
    int rejectActionRequest(@Param("actionRequestId") Long actionRequestId, @Param("userId") Long userId);
    int supersedeActionRequest(@Param("actionRequestId") Long actionRequestId, @Param("userId") Long userId);
    int finishActionRequest(@Param("actionRequestId") Long actionRequestId,
        @Param("resultJson") String resultJson);
    int insertAudit(Map<String, Object> row);
    int insertWorkflow(Map<String, Object> row);
    Map<String, Object> selectActiveWorkflow(@Param("conversationId") Long conversationId,
        @Param("userId") Long userId);
    int updateWorkflow(Map<String, Object> row);
    int finishWorkflow(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
        @Param("workflowCode") String workflowCode, @Param("workflowStatus") String workflowStatus,
        @Param("actionRequestId") Long actionRequestId);
    int insertWorkflowEvent(Map<String, Object> row);
}
