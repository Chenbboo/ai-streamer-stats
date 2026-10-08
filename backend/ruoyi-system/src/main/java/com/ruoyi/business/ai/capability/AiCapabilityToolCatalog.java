package com.ruoyi.business.ai.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Converts permission-filtered capabilities into model tool definitions. */
@Service
public class AiCapabilityToolCatalog
{
    private final AiCapabilityRegistry registry;

    @Autowired
    public AiCapabilityToolCatalog(AiCapabilityRegistry registry)
    {
        this.registry = registry;
    }

    public List<Map<String, Object>> capabilityDefinitions(AiExecutionContext context)
    {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (AiCapability capability : registry.allowed(context))
        {
            Map<String, Object> function = new LinkedHashMap<String, Object>();
            function.put("name", capability.toolName());
            function.put("description", capability.description());
            function.put("parameters", capability.inputSchema());
            Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
            wrapper.put("type", "function");
            wrapper.put("function", function);
            result.add(wrapper);
        }
        return Collections.unmodifiableList(result);
    }

    public List<Map<String, Object>> definitions(AiExecutionContext context)
    {
        return capabilityDefinitions(context);
    }

    public AiCapability findAllowedByToolName(String toolName, AiExecutionContext context)
    {
        for (AiCapability capability : registry.allowed(context))
            if (capability.toolName().equals(toolName)) return capability;
        return null;
    }

    public boolean isAllowedToolName(String toolName, AiExecutionContext context)
    {
        return findAllowedByToolName(toolName, context) != null;
    }
}
