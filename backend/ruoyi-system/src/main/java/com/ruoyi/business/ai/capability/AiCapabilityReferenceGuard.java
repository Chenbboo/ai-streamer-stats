package com.ruoyi.business.ai.capability;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates stable IDs selected by the model against facts returned earlier in the same tool run.
 * The rule is data-driven: once a directory/result tool returns candidates for an ID kind, later
 * calls must reference one of those candidates instead of inventing an ID.
 */
public final class AiCapabilityReferenceGuard
{
    public Map<String, Object> validate(Map<String, Object> input, List<Map<String, Object>> toolResults)
    {
        if (input == null || input.isEmpty() || toolResults == null || toolResults.isEmpty()) return null;
        Map<String, Set<String>> candidates = new LinkedHashMap<String, Set<String>>();
        for (Map<String, Object> result : toolResults)
            if (result != null) collect(result.get("data"), candidates);
        for (Map.Entry<String, Object> entry : input.entrySet())
        {
            String kind = referenceKind(entry.getKey());
            String actual = scalar(entry.getValue());
            if (kind == null || actual == null) continue;
            Set<String> allowed = candidates.get(kind);
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(actual))
                return error(entry.getKey(), entry.getValue(), allowed);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void collect(Object value, Map<String, Set<String>> candidates)
    {
        if (value instanceof Map)
        {
            Map<String, Object> map = (Map<String, Object>) value;
            if (Boolean.FALSE.equals(map.get("canOpen"))) return;
            for (Map.Entry<String, Object> entry : map.entrySet())
            {
                String kind = referenceKind(entry.getKey());
                String scalar = scalar(entry.getValue());
                if (kind != null && scalar != null)
                {
                    Set<String> values = candidates.get(kind);
                    if (values == null)
                    {
                        values = new LinkedHashSet<String>();
                        candidates.put(kind, values);
                    }
                    values.add(scalar);
                }
                collect(entry.getValue(), candidates);
            }
        }
        else if (value instanceof Collection)
            for (Object item : (Collection<Object>) value) collect(item, candidates);
    }

    private String referenceKind(String field)
    {
        if (field == null) return null;
        String key = field.trim().toLowerCase();
        if (!key.endsWith("id") || "id".equals(key)) return null;
        if ("userid".equals(key) || "staffuserid".equals(key) || "mainowneruserid".equals(key)
            || "assigneeuserid".equals(key) || "owneruserid".equals(key) || "manageruserid".equals(key)
            || "submitteduserid".equals(key) || "revieweduserid".equals(key)) return "userId";
        if ("deptid".equals(key) || "companydeptid".equals(key)) return "deptId";
        return key;
    }

    private String scalar(Object value)
    {
        if (value == null || value instanceof Map || value instanceof Collection) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try { return new BigDecimal(text).stripTrailingZeros().toPlainString(); }
        catch (Exception ignored) { return text; }
    }

    private Map<String, Object> error(String field, Object rejected, Set<String> allowed)
    {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("_retryableToolError", true);
        result.put("errorCode", "INVALID_REFERENCE");
        result.put("field", field);
        result.put("rejectedValue", rejected);
        result.put("allowedValues", new ArrayList<String>(allowed));
        result.put("message", "参数 " + field + " 没有引用前序工具返回的稳定ID，未执行本次调用。");
        result.put("instruction", "请从 allowedValues 中选择与用户所指对象匹配的真实ID后重新调用工具；不得使用列表序号代替ID。");
        return result;
    }
}
