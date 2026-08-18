package com.ruoyi.business.ai.capability;

import java.util.Map;

/** A high-risk capability that the model may prepare but only the user may confirm. */
public interface AiConfirmableCapability extends AiCapability
{
    @Override default AiCapabilityRisk risk() { return AiCapabilityRisk.CONFIRM_REQUIRED; }
    String confirmationSummary(AiCapabilityInvocation invocation, Map<String, Object> input);
    default Map<String, Object> confirmationDetails(AiCapabilityInvocation invocation, Map<String, Object> input)
    { return input; }
    Map<String, Object> executeConfirmed(AiCapabilityInvocation invocation, Map<String, Object> input);
    @Override default Map<String, Object> execute(AiCapabilityInvocation invocation, Map<String, Object> input)
    { throw new UnsupportedOperationException("确认类能力不能直接执行"); }
}
