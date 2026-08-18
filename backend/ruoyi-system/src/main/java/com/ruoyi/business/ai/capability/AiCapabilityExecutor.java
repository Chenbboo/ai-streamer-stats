package com.ruoyi.business.ai.capability;

import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;

/** Mandatory permission gate in front of every model-selected capability. */
@Service
public class AiCapabilityExecutor
{
    private final AiCapabilityRegistry registry;
    private final AiCapabilityActionService actionService;

    @Autowired
    public AiCapabilityExecutor(AiCapabilityRegistry registry, AiCapabilityActionService actionService)
    {
        this.registry = registry; this.actionService = actionService;
    }

    public AiCapabilityExecutor(AiCapabilityRegistry registry) { this(registry, null); }

    public Map<String, Object> execute(String code, AiCapabilityInvocation invocation,
        Map<String, Object> input)
    {
        if (invocation == null) throw new ServiceException("AI执行上下文不存在");
        AiCapability capability;
        try { capability = registry.require(code); }
        catch (IllegalArgumentException ex) { throw new ServiceException("AI请求了未注册的系统能力"); }
        if (!invocation.getActor().hasPermission(capability.requiredPermission()))
            throw new ServiceException("当前账号没有执行该操作的权限");
        Map<String, Object> safeInput = input == null ? Collections.<String, Object>emptyMap() : input;
        if (capability.risk().isConfirmationRequired())
        {
            if (!(capability instanceof AiConfirmableCapability) || actionService == null)
                throw new ServiceException("该操作必须先生成确认单，不能由模型直接执行");
            return actionService.prepare((AiConfirmableCapability) capability, invocation, safeInput);
        }
        return capability.execute(invocation, safeInput);
    }
}
