package com.ruoyi.business.ai.capability;

import java.util.Map;

/** One business action exposed to the model. Natural-language variations share the same capability. */
public interface AiCapability
{
    String code();
    default String toolName() { return "capability_" + code().replace('.', '_'); }
    String description();
    String requiredPermission();
    AiCapabilityRisk risk();
    Map<String, Object> inputSchema();
    Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input);
}
