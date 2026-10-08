package com.ruoyi.business.service;

import java.util.List;
import java.util.Map;

/** AI 模型只负责选择白名单工具和组织答案，不直接访问业务数据库。 */
public interface IBusinessAiModelClient
{
    boolean isEnabled();
    String providerCode();
    String modelName();
    Map<String, Object> plan(String question, List<Map<String, Object>> history);
    default Map<String, Object> plan(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> tools)
    {
        return plan(question, history);
    }
    /**
     * 在已经取得一批工具结果后继续推理。turns 中保存本轮模型消息和对应工具结果，
     * 模型可以继续选择只读工具，也可以在信息充分时直接给出最终答案。
     */
    Map<String, Object> continueWithTools(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> turns);
    default Map<String, Object> continueWithTools(String question, List<Map<String, Object>> history,
        List<Map<String, Object>> turns, List<Map<String, Object>> tools)
    {
        return continueWithTools(question, history, turns);
    }
    Map<String, Object> complete(String question, Map<String, Object> plan,
        List<Map<String, Object>> toolMessages);

    /** Rewrites a rejected final answer using only the supplied, already-authorized capability results. */
    default String rewriteGroundedAnswer(String question, String rejectedAnswer,
        List<Map<String, Object>> capabilityResults, List<String> violations)
    {
        return null;
    }
}
