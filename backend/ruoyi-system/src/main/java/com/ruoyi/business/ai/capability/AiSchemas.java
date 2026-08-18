package com.ruoyi.business.ai.capability;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small JSON-schema helpers shared by capability declarations. */
public final class AiSchemas
{
    private AiSchemas() { }

    public static Map<String, Object> object()
    {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("additionalProperties", false);
        return schema;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> property(Map<String, Object> schema, String name, String type,
        String description)
    {
        Map<String, Object> property = new LinkedHashMap<String, Object>();
        property.put("type", type);
        property.put("description", description);
        ((Map<String, Object>) schema.get("properties")).put(name, property);
        return property;
    }

    public static Map<String, Object> required(Map<String, Object> schema, String... names)
    {
        schema.put("required", Arrays.asList(names));
        return schema;
    }
}
