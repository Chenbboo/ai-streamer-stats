package com.ruoyi.business.ai.capability;

import java.util.LinkedHashMap;
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
    /** Redacted representation written to the tool audit log. */
    default Map<String, Object> auditInput(Map<String, Object> input)
    { return input == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(input); }
    Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input);
}
