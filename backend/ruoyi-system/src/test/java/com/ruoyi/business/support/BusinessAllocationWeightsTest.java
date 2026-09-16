package com.ruoyi.business.support;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessAllocationWeightsTest {
    private Map<String,Object> period(long project,long version,String value,String from,String end) {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("projectId",project);row.put("allocationId",version);row.put("allocationValue",new BigDecimal(value));
        row.put("effectiveFrom",from);row.put("projectEndDate",end);row.put("confirmationStatus","CONFIRMED");return row;
    }
    private Map<Long,Map<String,Object>> at(List<Map<String,Object>> rows,String date){return BusinessAllocationWeights.at(rows,LocalDate.parse(date));}
    private void weight(Map<Long,Map<String,Object>> rows,long id,String expected){assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(rows.get(id).get("allocationValue").toString())));}
    private void total(Map<Long,Map<String,Object>> rows){assertEquals(0,new BigDecimal("100").compareTo(rows.values().stream().map(r->new BigDecimal(r.get("allocationValue").toString())).reduce(BigDecimal.ZERO,BigDecimal::add)));}

    @Test void endingShareIsAddedEquallyAndSubsequentEndReleasesTheAdjustedShare(){
        List<Map<String,Object>> rows=Arrays.asList(period(1,1,"10","2026-09-01","2026-09-16"),period(2,2,"30","2026-09-01","2026-09-18"),period(3,3,"60","2026-09-01",null));
        Map<Long,Map<String,Object>> before=at(rows,"2026-09-16");weight(before,1,"10");weight(before,2,"30");total(before);
        Map<Long,Map<String,Object>> after=at(rows,"2026-09-17");assertEquals(2,after.size());weight(after,2,"35");weight(after,3,"65");total(after);
        assertEquals(true,after.get(2L).get("autoRedistributed"));
        Map<Long,Map<String,Object>> last=at(rows,"2026-09-19");assertEquals(1,last.size());weight(last,3,"100");total(last);
        assertEquals(new BigDecimal("30"),rows.get(1).get("allocationValue"));
    }
    @Test void simultaneousEndingsAndLastProjectEnding(){
        List<Map<String,Object>> rows=Arrays.asList(period(1,1,"10","2026-09-01","2026-09-16"),period(2,2,"30","2026-09-01","2026-09-16"),period(3,3,"60","2026-09-01","2026-09-20"));
        weight(at(rows,"2026-09-17"),3,"100");assertTrue(at(rows,"2026-09-21").isEmpty());
    }
    @Test void roundingIsStableAndAlwaysSumsToOneHundred(){
        List<Map<String,Object>> rows=Arrays.asList(period(1,1,"10","2026-09-01","2026-09-16"),period(2,2,"30","2026-09-01",null),period(3,3,"30","2026-09-01",null),period(4,4,"30","2026-09-01",null));
        Map<Long,Map<String,Object>> result=at(rows,"2026-09-17");weight(result,2,"33.34");weight(result,3,"33.33");weight(result,4,"33.33");total(result);
        Collections.reverse(rows);assertEquals(result,at(rows,"2026-09-17"));
    }
    @Test void newManualDistributionAfterAutoTransferIsNotCountedTwice(){
        Map<String,Object> oldB=period(2,2,"30","2026-09-01",null);oldB.put("effectiveTo","2026-09-17");
        Map<String,Object> oldC=period(3,3,"60","2026-09-01",null);oldC.put("effectiveTo","2026-09-17");
        List<Map<String,Object>> rows=Arrays.asList(period(1,1,"10","2026-09-01","2026-09-16"),oldB,oldC,period(2,4,"40","2026-09-18",null),period(3,5,"60","2026-09-18",null));
        weight(at(rows,"2026-09-17"),2,"35");Map<Long,Map<String,Object>> later=at(rows,"2026-09-18");weight(later,2,"40");weight(later,3,"60");total(later);
    }
    @Test void manualDistributionOnEndingBoundaryTakesPrecedence(){
        Map<String,Object> oldB=period(2,2,"30","2026-09-01",null);oldB.put("effectiveTo","2026-09-16");
        Map<String,Object> oldC=period(3,3,"60","2026-09-01",null);oldC.put("effectiveTo","2026-09-16");
        List<Map<String,Object>> rows=Arrays.asList(period(1,1,"10","2026-09-01","2026-09-16"),oldB,oldC,period(2,4,"40","2026-09-17",null),period(3,5,"60","2026-09-17",null));
        Map<Long,Map<String,Object>> later=at(rows,"2026-09-17");weight(later,2,"40");weight(later,3,"60");total(later);
    }
    @Test void pendingRecordsAreNotAutomaticallyConfirmed(){
        Map<String,Object> pending=period(2,2,"30","2026-09-01",null);pending.put("confirmationStatus","PENDING");
        List<Map<String,Object>> rows=Arrays.asList(period(1,1,"10","2026-09-01","2026-09-16"),pending,period(3,3,"60","2026-09-01",null));
        Map<Long,Map<String,Object>> later=at(rows,"2026-09-17");weight(later,2,"30");assertEquals("PENDING",later.get(2L).get("confirmationStatus"));
    }
    @Test void closedAllocationEndingWithProjectStillReleasesItsShare(){
        Map<String,Object> closed=period(1,1,"10","2026-09-01","2026-09-16");closed.put("effectiveTo","2026-09-16");
        weight(at(Arrays.asList(closed,period(2,2,"90","2026-09-01",null)),"2026-09-17"),2,"100");
    }
}
