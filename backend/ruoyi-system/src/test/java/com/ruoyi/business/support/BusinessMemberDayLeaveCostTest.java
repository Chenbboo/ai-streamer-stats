package com.ruoyi.business.support;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BusinessMemberDayLeaveCostTest {
    private final ObjectMapper json = new ObjectMapper();
    private Map<String,Object> row(Object... values) {
        Map<String,Object> row=new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2)row.put(String.valueOf(values[i]),values[i+1]);
        return row;
    }
    private Map<String,Object> cost() {
        return row("userId",137L,"bizDate","2026-09-28","pricingStatus","PRICED","amount",new BigDecimal("100.00"),
            "basisJson","{\"costPolicyVersion\":\"MEMBER_DAYS_V1\",\"ratePolicyId\":29,\"dailyCost\":100}");
    }
    private long at(String value) { return LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond(); }
    private Map<String,Object> source(long id,String kind,String intervals) {
        return row("observationId",id,"userId",137L,"businessDate","2026-09-28","kind",kind,
            "normalizedStatus","CONFIRMED","quality","KNOWN","intervalsJson",intervals);
    }
    private String interval(String start,String end) { return "[["+at(start)+","+at(end)+"]]"; }
    private Map<String,Object> shift() {
        return source(626L,"SHIFT","[["+at("2026-09-28T09:00")+","+at("2026-09-28T12:00")+"],["
            +at("2026-09-28T13:00")+","+at("2026-09-28T18:00")+"]]");
    }
    private Map<String,Object> leave(String start,String end) { return source(610L,"LEAVE",interval(start,end)); }
    private void apply(Map<String,Object> cost,Map<String,Object>... sources) {
        BusinessMemberDayLeaveCost.apply(Collections.singletonList(cost),Arrays.asList(sources),json);
    }
    @Test void realFullDaySampleDeducts51136From96590() {
        Map<String,Object> cost=cost();cost.put("amount",new BigDecimal("511.36"));
        apply(cost,source(626,"SHIFT",interval("2026-09-28T09:00","2026-09-28T18:00")),
            leave("2026-09-28T09:00","2026-09-30T18:00"));
        assertEquals(new BigDecimal("0.00"),cost.get("amount"));
        assertEquals(new BigDecimal("454.54"),new BigDecimal("170.45").add(new BigDecimal("284.09")).add((BigDecimal)cost.get("amount")));
        assertTrue(String.valueOf(cost.get("basisJson")).contains("\"attendanceBaseAmount\":511.36"));
    }
    @Test void lunchIsExcludedAndOverlappingApprovalsCountOnce() {
        Map<String,Object> cost=cost();
        apply(cost,shift(),leave("2026-09-28T11:00","2026-09-28T14:00"),
            source(611,"LEAVE",interval("2026-09-28T11:30","2026-09-28T13:30")));
        assertEquals(new BigDecimal("75.00"),cost.get("amount"));
    }
    @Test void onlyBusinessDateShiftIsUsedForMultiDayLeave() {
        Map<String,Object> cost=cost();
        apply(cost,shift(),leave("2026-09-27T11:00","2026-09-28T11:00"));
        assertEquals(new BigDecimal("75.00"),cost.get("amount"));
    }
    @Test void overnightShiftUsesEpochOverlap() {
        Map<String,Object> cost=cost();
        apply(cost,source(626,"SHIFT",interval("2026-09-28T21:00","2026-09-29T05:00")),
            leave("2026-09-29T00:00","2026-09-29T04:00"));
        assertEquals(new BigDecimal("50.00"),cost.get("amount"));
    }
    @Test void repeatedRefreshDoesNotDeductAgain() {
        Map<String,Object> cost=cost();Map<String,Object> leave=leave("2026-09-28T09:00","2026-09-28T12:00");
        apply(cost,shift(),leave);String basis=String.valueOf(cost.get("basisJson"));
        apply(cost,shift(),leave);
        assertEquals(new BigDecimal("62.50"),cost.get("amount"));assertEquals(basis,cost.get("basisJson"));
    }
    @Test void cancellationRestoresOriginalAmountAndKeepsOriginalRateBasis() {
        Map<String,Object> cost=cost();Map<String,Object> leave=leave("2026-09-28T09:00","2026-09-28T18:00");
        apply(cost,shift(),leave);
        leave.put("normalizedStatus","CANCELED");leave.put("observationId",700L);
        apply(cost,leave);
        assertEquals(new BigDecimal("100.00"),cost.get("amount"));
        assertTrue(String.valueOf(cost.get("basisJson")).contains("\"ratePolicyId\":29"));
        assertTrue(String.valueOf(cost.get("basisJson")).contains("700"));
    }
    @Test void missingShiftDoesNotInventZeroCostAndCanRecover() {
        Map<String,Object> cost=cost();Map<String,Object> leave=leave("2026-09-28T09:00","2026-09-28T18:00");
        apply(cost,leave);assertNull(cost.get("amount"));assertEquals("PENDING",cost.get("pricingStatus"));
        assertTrue(BusinessMemberDayLeaveCost.hasBase(cost,json));
        apply(cost,shift(),leave);assertEquals(new BigDecimal("0.00"),cost.get("amount"));assertEquals("PRICED",cost.get("pricingStatus"));
    }
    @Test void unknownLeaveOrMalformedIntervalsStayPending() {
        for(String invalid:Arrays.asList("unknown","empty","malformed")) {
            Map<String,Object> cost=cost(),leave=leave("2026-09-28T09:00","2026-09-28T18:00");
            if("unknown".equals(invalid))leave.put("quality","UNKNOWN");
            else leave.put("intervalsJson","empty".equals(invalid)?"[]":"[[10,9]]");
            apply(cost,shift(),leave);assertNull(cost.get("amount"));assertEquals("PENDING",cost.get("pricingStatus"));
        }
    }
    @Test void noApprovedLeaveDoesNotInterpretMissingAttendanceAsAbsence() {
        Map<String,Object> cost=cost(),leave=leave("2026-09-28T09:00","2026-09-28T18:00");leave.put("normalizedStatus","CANCELED");
        apply(cost,leave);assertEquals(new BigDecimal("100.00"),cost.get("amount"));
        assertFalse(String.valueOf(cost.get("basisJson")).contains("attendanceAdjustment"));
    }
    @Test void approvedOutingDoesNotReduceCostOrExtendApprovedLeave() {
        Map<String,Object> outing=source(800L,"OUT",interval("2026-09-28T09:00","2026-09-28T18:00"));
        Map<String,Object> cost=cost();
        apply(cost,shift(),outing);
        assertEquals(new BigDecimal("100.00"),cost.get("amount"));
        assertFalse(String.valueOf(cost.get("basisJson")).contains("attendanceAdjustment"));
        apply(cost,shift(),outing,leave("2026-09-28T09:00","2026-09-28T12:00"));
        assertEquals(new BigDecimal("62.50"),cost.get("amount"));
    }
    @Test void otherPeoplesLeaveDoesNotReduceThisPerson() {
        Map<String,Object> cost=cost(),leave=leave("2026-09-28T09:00","2026-09-28T18:00");leave.put("userId",132L);
        apply(cost,shift(),leave);assertEquals(new BigDecimal("100.00"),cost.get("amount"));
    }
    @Test void policyDoesNotChangeEarlierCostsOrMissingRates() {
        Map<String,Object> cost=cost();cost.put("bizDate","2026-09-25");
        Map<String,Object> leave=leave("2026-09-25T09:00","2026-09-25T18:00");leave.put("businessDate","2026-09-25");
        apply(cost,leave);assertEquals(new BigDecimal("100.00"),cost.get("amount"));
        cost=cost();cost.put("pricingStatus","PENDING");cost.put("amount",null);
        apply(cost,shift(),leave("2026-09-28T09:00","2026-09-28T18:00"));assertNull(cost.get("amount"));
    }
}
