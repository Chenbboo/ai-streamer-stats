package com.ruoyi.business.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.business.attendance.AttendanceIntervals;
import com.ruoyi.common.exception.ServiceException;

/** Applies approved leave to automatic day costs, retaining the original pricing basis. */
public final class BusinessMemberDayLeaveCost {
    public static final LocalDate EFFECTIVE_FROM = LocalDate.of(2026, 9, 28);
    public static final String RULE = "FEISHU_APPROVED_LEAVE_V1";
    private BusinessMemberDayLeaveCost() { }

    public static boolean hasBase(Map<String,Object> row, ObjectMapper json) {
        String raw = String.valueOf(row.get("basisJson"));
        if (!raw.contains("attendanceBaseAmount")) return false;
        return basis(row, json).get("attendanceBaseAmount") != null;
    }

    public static List<Map<String,Object>> apply(List<Map<String,Object>> costs,
        List<Map<String,Object>> observations, ObjectMapper json) {
        Map<String,List<Map<String,Object>>> byDay = new HashMap<>();
        for (Map<String,Object> observation : observations)
            byDay.computeIfAbsent(key(observation, "businessDate"), k -> new ArrayList<>()).add(observation);
        for (Map<String,Object> cost : costs) {
            if (LocalDate.parse(String.valueOf(cost.get("bizDate")).substring(0,10)).isBefore(EFFECTIVE_FROM)) continue;
            List<Map<String,Object>> sources = byDay.getOrDefault(key(cost, "bizDate"), Collections.emptyList());
            boolean confirmedLeave = sources.stream().anyMatch(o -> "LEAVE".equals(o.get("kind")) && "CONFIRMED".equals(o.get("normalizedStatus")));
            boolean adjusted = hasBase(cost, json);
            if (!confirmedLeave && !adjusted) continue;
            if (!adjusted && (!"PRICED".equals(cost.get("pricingStatus")) || cost.get("amount") == null)) continue;
            Map<String,Object> basis = basis(cost, json);
            BigDecimal base = new BigDecimal(String.valueOf(adjusted ? basis.get("attendanceBaseAmount") : cost.get("amount"))).setScale(2, RoundingMode.HALF_UP);
            List<long[]> shifts = new ArrayList<>(), leaves = new ArrayList<>();
            List<Long> sourceIds = new ArrayList<>();
            String issue = null;
            try {
                for (Map<String,Object> source : sources) {
                    if ("LEAVE".equals(source.get("kind"))) {
                        sourceIds.add(((Number)source.get("observationId")).longValue());
                        if (!"CONFIRMED".equals(source.get("normalizedStatus"))) continue;
                        if (!"KNOWN".equals(source.get("quality"))) throw new IllegalArgumentException();
                        List<long[]> intervals = intervals(source, json);
                        if (intervals.isEmpty()) throw new IllegalArgumentException();
                        leaves.addAll(intervals);
                    } else if (confirmedLeave && "SHIFT".equals(source.get("kind")) && "CONFIRMED".equals(source.get("normalizedStatus"))) {
                        if (!"KNOWN".equals(source.get("quality"))) throw new IllegalArgumentException();
                        shifts.addAll(intervals(source, json));
                        sourceIds.add(((Number)source.get("observationId")).longValue());
                    }
                }
                AttendanceIntervals.union(leaves);
                AttendanceIntervals.union(shifts);
            } catch (Exception ex) {
                issue = "飞书请假或排班区间待核实，人员成本待计价";
            }
            long workSeconds = 0, availableSeconds = 0;
            if (issue == null && !leaves.isEmpty()) {
                workSeconds = seconds(shifts);
                if (workSeconds == 0) issue = "已批准请假缺少有效飞书排班，人员成本待计价";
                else availableSeconds = seconds(AttendanceIntervals.subtract(shifts, leaves));
            }
            BigDecimal amount = issue != null ? null : leaves.isEmpty() ? base
                : base.multiply(BigDecimal.valueOf(availableSeconds)).divide(BigDecimal.valueOf(workSeconds), 2, RoundingMode.HALF_UP);
            Collections.sort(sourceIds);
            basis.put("attendanceBaseAmount", base);
            Map<String,Object> adjustment = new LinkedHashMap<>();
            adjustment.put("rule", RULE);
            adjustment.put("effectiveFrom", EFFECTIVE_FROM.toString());
            adjustment.put("sourceObservationIds", sourceIds);
            adjustment.put("scheduledSeconds", workSeconds);
            adjustment.put("approvedLeaveSeconds", workSeconds - availableSeconds);
            adjustment.put("issue", issue);
            basis.put("attendanceAdjustment", adjustment);
            basis.put("dailyCost", amount);
            basis.put("issue", issue);
            cost.put("amount", amount);
            cost.put("issue", issue);
            cost.put("pricingStatus", issue == null ? "PRICED" : "PENDING");
            try {
                cost.put("basisJson", json.writeValueAsString(basis));
                cost.put("calculationDetail", cost.get("basisJson"));
            } catch (Exception ex) { throw new ServiceException("请假成本依据无法保存"); }
        }
        return costs;
    }

    private static String key(Map<String,Object> row, String dateField) {
        return row.get("userId") + ":" + String.valueOf(row.get(dateField)).substring(0,10);
    }
    private static Map<String,Object> basis(Map<String,Object> row, ObjectMapper json) {
        try { return json.readerFor(new TypeReference<Map<String,Object>>() { }).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .readValue(String.valueOf(row.get("basisJson"))); }
        catch (Exception ex) { throw new ServiceException("人员成本原始依据无法读取，不能扣减请假"); }
    }
    private static List<long[]> intervals(Map<String,Object> row, ObjectMapper json) throws Exception {
        List<long[]> values = json.readValue(String.valueOf(row.get("intervalsJson")), new TypeReference<List<long[]>>() { });
        return AttendanceIntervals.union(values);
    }
    private static long seconds(List<long[]> intervals) {
        long seconds = 0;
        for (long[] interval : AttendanceIntervals.union(intervals)) seconds += interval[1] - interval[0];
        return seconds;
    }
}
