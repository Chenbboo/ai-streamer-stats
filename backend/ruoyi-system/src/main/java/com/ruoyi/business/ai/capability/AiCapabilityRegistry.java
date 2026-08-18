package com.ruoyi.business.ai.capability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Single source of truth for model-visible system capabilities. */
@Service
public class AiCapabilityRegistry
{
    private final Map<String, AiCapability> capabilities;

    @Autowired
    public AiCapabilityRegistry(List<AiCapability> discovered)
    {
        LinkedHashMap<String, AiCapability> registered = new LinkedHashMap<String, AiCapability>();
        if (discovered != null)
            for (AiCapability capability : discovered)
            {
                if (capability == null || capability.code() == null || capability.code().trim().isEmpty())
                    throw new IllegalStateException("AI capability code must not be blank");
                if (registered.put(capability.code(), capability) != null)
                    throw new IllegalStateException("Duplicate AI capability: " + capability.code());
            }
        this.capabilities = Collections.unmodifiableMap(registered);
    }

    public AiCapability require(String code)
    {
        AiCapability capability = capabilities.get(code);
        if (capability == null) throw new IllegalArgumentException("Unknown AI capability: " + code);
        return capability;
    }

    public Collection<AiCapability> all()
    {
        return capabilities.values();
    }

    public List<AiCapability> allowed(AiExecutionContext context)
    {
        List<AiCapability> allowed = new ArrayList<AiCapability>();
        for (AiCapability capability : capabilities.values())
            if (context != null && context.hasPermission(capability.requiredPermission())) allowed.add(capability);
        return Collections.unmodifiableList(allowed);
    }
}
