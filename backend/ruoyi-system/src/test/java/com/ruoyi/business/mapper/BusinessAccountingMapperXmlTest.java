package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BusinessAccountingMapperXmlTest
{
    @Test
    void personnelCostOverviewIncludesClosedProjectOnlyForAnExistingDailySnapshot()
    {
        InputStream input=getClass().getResourceAsStream("/mapper/business/BusinessAccountingMapper.xml");
        assertNotNull(input);
        String xml=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start=xml.indexOf("<select id=\"selectPersonnelCostOverview\"");
        int end=xml.indexOf("</select>",start);
        assertTrue(start>=0&&end>start);
        String query=xml.substring(start,end);

        assertTrue(query.contains("p.status&lt;&gt;'CANCELED'"));
        assertTrue(query.contains("p.status&lt;&gt;'CLOSED' or exists"));
        assertTrue(query.contains("closed_result.project_id=p.project_id"));
        assertTrue(query.contains("closed_result.biz_date=#{bizDate}"));
        assertTrue(query.contains("closed_result.is_current='1'"));
        assertFalse(query.contains("p.status not in('CLOSED','CANCELED')"));
    }

    @Test
    void returnedAccountingFactCanBeCorrectedAndResubmitted()
    {
        InputStream input=getClass().getResourceAsStream("/mapper/business/BusinessAccountingMapper.xml");
        assertNotNull(input);
        String xml=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start=xml.indexOf("<update id=\"updateDraftFact\"");
        int end=xml.indexOf("</update>",start);
        assertTrue(start>=0&&end>start);
        String update=xml.substring(start,end);

        assertTrue(update.contains("status='DRAFT'"));
        assertTrue(update.contains("status in('DRAFT','RETURNED')"));
        assertTrue(update.contains("return_reason=null"));
        assertTrue(xml.contains("<update id=\"returnFact\""));
        assertTrue(xml.contains("status='RETURNED'"));
    }

    @Test
    void returnedAccountingFactBlocksProjectClosure()
    {
        String xml=mapperXml();
        int start=xml.indexOf("<select id=\"countProjectUnsettledFacts\"");
        int end=xml.indexOf("</select>",start);
        assertTrue(start>=0&&end>start);
        assertTrue(xml.substring(start,end).contains("status in('DRAFT','RETURNED')"));
    }

    @Test
    void missingDailyResultIncludesProjectsWithFactsOrPersonnelAllocation()
    {
        String xml=mapperXml();
        int start=xml.indexOf("<select id=\"countProjectsMissingDailyResult\"");
        int end=xml.indexOf("</select>",start);
        assertTrue(start>=0&&end>start);
        String query=xml.substring(start,end);
        assertTrue(query.contains("p.status in('ACTIVE','ACCEPTANCE')"));
        assertTrue(query.contains("exists(select 1 from biz_operating_fact"));
        assertTrue(query.contains("or exists(select 1 from biz_project_staff_allocation"));
        assertTrue(query.contains("not exists(select 1 from biz_project_daily_result"));
        assertFalse(query.contains("join biz_project_staff_allocation a on"));
    }

    private String mapperXml()
    {
        InputStream input=getClass().getResourceAsStream("/mapper/business/BusinessAccountingMapper.xml");
        assertNotNull(input);
        return new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
    }
}
