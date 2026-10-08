package com.ruoyi.business.support;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Budget scope is frozen with the existing versioned project baseline. */
public final class BusinessBudgetSnapshot
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private BusinessBudgetSnapshot() {}

    @SuppressWarnings("unchecked")
    public static Map<String,Object> read(String snapshot)
    {
        if (snapshot == null || snapshot.isEmpty()) return null;
        try { Object value=JSON.readValue(snapshot,Map.class).get("budget");return value instanceof Map?(Map<String,Object>)value:null; }
        catch (Exception ex) { return null; }
    }
}
