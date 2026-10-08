package com.ruoyi.business.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import com.ruoyi.common.exception.ServiceException;

/** Replays dated allocations and project endings without changing confirmed source versions. */
public final class BusinessAllocationWeights {
    private BusinessAllocationWeights() {}
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    public static Map<Long,Map<String,Object>> at(List<Map<String,Object>> periods, LocalDate date) {
        Map<Long,Map<String,Object>> state = new TreeMap<>();
        if (periods == null || periods.isEmpty()) return state;
        SortedSet<LocalDate> events = new TreeSet<>();
        for (Map<String,Object> row : periods) {
            events.add(start(row));
            LocalDate end = end(row);
            if (end != null) events.add(end.plusDays(1));
        }
        for (LocalDate event : events) {
            if (event.isAfter(date)) break;
            Map<Long,Map<String,Object>> next = new TreeMap<>();
            for (Map<String,Object> row : periods) {
                if (event.isBefore(start(row)) || end(row) != null && event.isAfter(end(row))) continue;
                Long projectId = id(row.get("projectId"));
                Map<String,Object> previous = state.get(projectId);
                Map<String,Object> effective = new LinkedHashMap<>(row);
                effective.put("baseAllocationValue", row.get("allocationValue"));
                if (previous != null && Objects.equals(previous.get("allocationId"), row.get("allocationId"))) {
                    effective.put("allocationValue", previous.get("allocationValue"));
                    effective.put("autoRedistributed", previous.get("autoRedistributed"));
                }
                if (next.put(projectId, effective) != null)
                    throw new ServiceException("该人员存在重叠的投入权重版本，请先处理后再计算自动分配");
            }
            BigDecimal released = BigDecimal.ZERO;
            for (Map.Entry<Long,Map<String,Object>> entry : state.entrySet()) {
                Map<String,Object> row = entry.getValue();
                LocalDate projectEnd = day(row.get("projectEndDate"));
                if (!next.containsKey(entry.getKey()) && projectEnd != null && event.equals(projectEnd.plusDays(1))
                    && !"PENDING".equals(row.get("confirmationStatus")))
                    released = released.add(number(row.get("allocationValue")));
            }
            // Explicit new versions may already include the released share. Never allocate it twice,
            // or use automatic redistribution to approve an unfinished manual allocation.
            boolean confirmed = next.values().stream().noneMatch(row -> "PENDING".equals(row.get("confirmationStatus")));
            BigDecimal total = next.values().stream().map(row -> number(row.get("allocationValue"))).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal addition = released.min(HUNDRED.subtract(total));
            if (!next.isEmpty() && confirmed && addition.signum() > 0) {
                int points = addition.movePointRight(2).setScale(0,RoundingMode.HALF_UP).intValueExact();
                int share = points / next.size(), remainder = points % next.size();
                for (Map<String,Object> row : next.values()) {
                    int extra = share + (remainder-- > 0 ? 1 : 0);
                    row.put("allocationValue", number(row.get("allocationValue")).add(BigDecimal.valueOf(extra,2)));
                    row.put("autoRedistributed", true);
                }
            }
            state = next;
        }
        return state;
    }

    private static LocalDate start(Map<String,Object> row) {
        LocalDate from = day(row.get("effectiveFrom")), project = day(row.get("projectStartDate"));
        if (from == null) from = project == null ? LocalDate.of(1900,1,1) : project;
        return project != null && project.isAfter(from) ? project : from;
    }
    private static LocalDate end(Map<String,Object> row) {
        LocalDate to = day(row.get("effectiveTo")), project = day(row.get("projectEndDate"));
        return to == null ? project : project == null || to.isBefore(project) ? to : project;
    }
    private static LocalDate day(Object value) { return value == null ? null : LocalDate.parse(String.valueOf(value).substring(0,10)); }
    private static Long id(Object value) { return Long.valueOf(String.valueOf(value)); }
    private static BigDecimal number(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
