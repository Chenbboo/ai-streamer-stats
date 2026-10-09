package com.ruoyi.business.support;

import java.util.*;
import com.ruoyi.business.domain.BusinessProjectKpi;
import com.ruoyi.business.domain.BusinessProjectKpiPlanItem;

/** Pausing a target applies to its published snapshots without rewriting their history. */
public final class BusinessKpiPause
{
    private BusinessKpiPause() { }
    public static Set<String> pausedCodes(List<BusinessProjectKpi> targets)
    {
        Set<String> codes=new HashSet<>();
        if(targets!=null) for(BusinessProjectKpi target:targets)
            if("PAUSED".equals(target.getStatus())) codes.add(target.getKpiCode());
        return codes;
    }
    public static boolean allPaused(List<BusinessProjectKpiPlanItem> items,Set<String> codes)
    {
        return items!=null&&!items.isEmpty()&&items.stream().allMatch(item->codes.contains(item.getKpiCode()));
    }
}
