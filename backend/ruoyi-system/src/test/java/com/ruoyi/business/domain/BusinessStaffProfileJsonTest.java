package com.ruoyi.business.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BusinessStaffProfileJsonTest
{
    @Test
    void ignoresServerManagedLoginDateWhenClientResubmitsAStaffListRow() throws Exception
    {
        String json = "{\"userId\":127,\"nickName\":\"A\","
            + "\"loginDate\":\"2026-08-05T14:51:07.000+08:00\"}";

        BusinessStaffProfile profile = new ObjectMapper().readValue(json, BusinessStaffProfile.class);

        assertEquals(127L, profile.getUserId());
        assertEquals("A", profile.getNickName());
        assertNull(profile.getLoginDate());
    }
}
