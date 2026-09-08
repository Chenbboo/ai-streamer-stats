package com.ruoyi.business.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BusinessProjectKpiMapperXmlTest
{
    @Test
    void employeeBonusQueryOnlyTotalsConfirmedSettlementsForParticipatingProjects()
    {
        InputStream input = getClass().getResourceAsStream("/mapper/business/BusinessProjectKpiMapper.xml");
        assertNotNull(input);
        String xml = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
            .lines().collect(Collectors.joining("\n"));
        int start = xml.indexOf("<select id=\"selectMemberProjectBonusTotals\"");
        int end = xml.indexOf("</select>", start);
        assertTrue(start >= 0 && end > start);
        String query = xml.substring(start, end);

        assertTrue(query.contains("settlement.status='CONFIRMED'"));
        assertTrue(query.contains("member_scope.user_id=#{userId}"));
        assertTrue(query.contains("member_scope.status='0'"));
        assertTrue(!query.contains("member_scope.member_role"));
        assertTrue(query.contains("sum(settlement.bonus_amount)"));
    }
}
