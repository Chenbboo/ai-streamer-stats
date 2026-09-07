package com.ruoyi.business.support;

import java.util.Map;
import com.ruoyi.business.domain.BusinessProject;
import com.ruoyi.common.exception.ServiceException;

/** Versioned delivery and management-accounting boundaries. Unknown versions fail closed. */
public final class BusinessProjectLifecycle
{
    public static final String LEGACY = "LEGACY_V1";
    public static final String SEPARATED = "SEPARATED_V1";

    private BusinessProjectLifecycle() { }

    public static boolean isSeparated(BusinessProject project)
    {
        return project != null && SEPARATED.equals(project.getDeliveryPolicyVersion());
    }

    public static boolean isSeparated(Map<String, Object> project)
    {
        return project != null && SEPARATED.equals(project.get("deliveryPolicyVersion"));
    }

    public static boolean isTerminal(String status)
    {
        return "CLOSED".equals(status) || "CANCELED".equals(status);
    }

    public static boolean isAccountingClosed(BusinessProject project)
    {
        return project == null || !knownVersion(project.getDeliveryPolicyVersion())
            || closed(isSeparated(project), project.getStatus(), project.getAccountingState());
    }

    public static boolean isAccountingClosed(Map<String, Object> project)
    {
        return project == null || !knownVersion(string(project.get("deliveryPolicyVersion")))
            || closed(isSeparated(project), string(project.get("status")), string(project.get("accountingState")));
    }

    private static boolean knownVersion(String version)
    {
        return version == null || LEGACY.equals(version) || SEPARATED.equals(version);
    }

    private static boolean closed(boolean separated, String status, String state)
    {
        if (!separated && isTerminal(status)) return true;
        if (state == null) return separated;
        return !"OPEN".equals(state);
    }

    public static void requireAccountingOpen(BusinessProject project)
    {
        if (isAccountingClosed(project)) throw new ServiceException("项目核算已关闭，不能修改结算或成本记录");
    }

    public static void requireAccountingOpen(Map<String, Object> project)
    {
        if (isAccountingClosed(project)) throw new ServiceException("项目核算已关闭，不能修改结算或成本记录");
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
}
