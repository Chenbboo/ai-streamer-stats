package com.ruoyi.business.support;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Date;
import org.junit.jupiter.api.Test;

class BusinessProposalPlanDatesTest {
    @Test void finiteProjectAllowsRevenueFromSixMonthsBeforeThroughSixMonthsAfter() {
        Date start=Date.valueOf("2026-09-18"),end=Date.valueOf("2026-12-10");
        assertNull(BusinessProposalPlanDates.revenueIssue("2026-03-01",start,end,"收入测算",1));
        assertNull(BusinessProposalPlanDates.revenueIssue("2027-06-30",start,end,"收入测算",1));
        assertNotNull(BusinessProposalPlanDates.revenueIssue("2026-02-28",start,end,"收入测算",1));
        assertNotNull(BusinessProposalPlanDates.revenueIssue("2027-07-01",start,end,"收入测算",1));
    }

    @Test void openEndedProjectHasNoRevenueUpperMonthLimit() {
        Date start=Date.valueOf("2026-09-18");
        assertNull(BusinessProposalPlanDates.revenueIssue("2026-03-01",start,null,"收入测算",1));
        assertNull(BusinessProposalPlanDates.revenueIssue("2036-09-01",start,null,"收入测算",1));
        assertNotNull(BusinessProposalPlanDates.revenueIssue("2026-02-28",start,null,"收入测算",1));
    }

    @Test void expenseMonthsUseTheSameExtendedWindow() {
        Date start=Date.valueOf("2026-09-18"),end=Date.valueOf("2026-12-10");
        assertNull(BusinessProposalPlanDates.expenseIssue("2026-03-01",start,end,"支出计划",1));
        assertNull(BusinessProposalPlanDates.expenseIssue("2027-06-30",start,end,"支出计划",1));
        assertNotNull(BusinessProposalPlanDates.expenseIssue("2026-02-28",start,end,"支出计划",1));
        assertNotNull(BusinessProposalPlanDates.expenseIssue("2027-07-01",start,end,"支出计划",1));
    }
}
