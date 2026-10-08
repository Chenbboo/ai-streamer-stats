package com.ruoyi.business.attendance;

import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class FeishuAttendanceClientTest
{
    private final ObjectMapper json=new ObjectMapper();
    private final FeishuAttendanceClient client=new FeishuAttendanceClient();
    private final LocalDate day=LocalDate.of(2026,9,7);

    @Test void unconfiguredProviderNeverCallsNetworkAndNeverExposesCredentials()
    {
        assertFalse(client.isConfigured("tenant"));
        assertThrows(ServiceException.class,()->client.query("tenant","Asia/Shanghai","APPROVAL",Arrays.asList("u1"),day));
        assertFalse(client.configurationStatus().containsKey("appSecret"));
    }

    @Test void leaveAndExplicitCancellationShareIdentityButKeepSourceDurationSeparate() throws Exception
    {
        String fixture="{\"user_approvals\":[{\"user_id\":\"u1\",\"date\":\"20260907\",\"time_zone\":\"Asia/Shanghai\",\"leaves\":[{\"approval_id\":\"a1\",\"idempotent_id\":\"stable1\",\"uniq_id\":\"leave1\",\"unit\":3,\"interval\":7200,\"start_time\":\"2026-09-07 11:00:00\",\"end_time\":\"2026-09-07 14:00:00\",\"reason\":\"private medical details\"}]}]}";
        Map<String,Object> approved=client.normalizeApprovals(json.readTree(fixture),2,"Asia/Shanghai",Arrays.asList("u1"),day).get(0);
        Map<String,Object> canceled=client.normalizeApprovals(json.readTree(fixture),3,"Asia/Shanghai",Arrays.asList("u1"),day).get(0);
        assertEquals(approved.get("sourceRecordKey"),canceled.get("sourceRecordKey"));
        assertEquals("CONFIRMED",approved.get("normalizedStatus")); assertEquals("CANCELED",canceled.get("normalizedStatus"));
        assertEquals(7200L,approved.get("sourceDurationSeconds"));
        assertEquals(180L,AttendanceIntervals.minutes((List<long[]>)approved.get("intervals")));
        assertFalse(json.writeValueAsString(approved).contains("private medical"));
    }

    @Test void unknownIdentityAndAmbiguousKeysFailInsteadOfMergingByPersonDay() throws Exception
    {
        String leave="{\"approval_id\":\"a1\",\"uniq_id\":\"type\",\"start_time\":\"2026-09-07 09:00:00\",\"end_time\":\"2026-09-07 10:00:00\"}";
        String duplicate="{\"user_approvals\":[{\"user_id\":\"u1\",\"date\":\"20260907\",\"leaves\":["+leave+","+leave+"]}]}";
        assertThrows(ServiceException.class,()->client.normalizeApprovals(json.readTree(duplicate),2,"Asia/Shanghai",Arrays.asList("u1"),day));
        String unknown="{\"user_approvals\":[{\"user_id\":\"other-company\",\"date\":\"20260907\",\"leaves\":["+leave+"]}]}";
        assertThrows(ServiceException.class,()->client.normalizeApprovals(json.readTree(unknown),2,"Asia/Shanghai",Arrays.asList("u1"),day));
    }

    @Test void fixedShiftSubtractsLunchAndMergesOverlappingAbsence() throws Exception
    {
        List<long[]> work=FeishuAttendanceClient.normalizeShift(json.readTree("{\"day_type\":1,\"punch_time_rule\":[{\"on_time\":\"9:00\",\"off_time\":\"18:00\"}],\"rest_time_rule\":[{\"rest_begin_time\":\"12:00\",\"rest_end_time\":\"13:00\"}]}"),day,"Asia/Shanghai");
        long start=day.atTime(11,0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond();
        List<long[]> leave=Arrays.asList(new long[]{start,start+3*3600},new long[]{start+3600,start+3*3600});
        assertEquals(480,AttendanceIntervals.minutes(work));
        assertEquals(360,AttendanceIntervals.minutes(AttendanceIntervals.subtract(work,leave)));
    }

    @Test void flexibleMissingAndCrossMidnightSchedulesAreNotAssumedEightHours() throws Exception
    {
        assertNull(FeishuAttendanceClient.normalizeShift(json.readTree("{\"is_flexible\":true,\"day_type\":1}"),day,"Asia/Shanghai"));
        assertNull(FeishuAttendanceClient.normalizeShift(json.readTree("{}"),day,"Asia/Shanghai"));
        List<long[]> work=FeishuAttendanceClient.normalizeShift(json.readTree("{\"day_type\":1,\"punch_time_rule\":[{\"on_time\":\"22:00\",\"off_time\":\"26:00\"}]}"),day,"Asia/Shanghai");
        assertEquals(240,AttendanceIntervals.minutes(work));
    }

    @Test void attendancePayloadDoesNotRetainPunchLocationsPhotosOrNames() throws Exception
    {
        Map<String,Object> row=client.normalizeTasks(json.readTree("{\"user_task_results\":[{\"result_id\":\"r1\",\"user_id\":\"u1\",\"day\":20260907,\"employee_name\":\"private name\",\"records\":[{\"check_in_result\":\"NewUnknownEnum\",\"check_out_result\":\"Normal\",\"check_in_record\":{\"location_name\":\"private location\",\"photo_urls\":[\"secret\"]}}]}]}"),"Asia/Shanghai",Arrays.asList("u1"),day).get(0);
        String serialized=json.writeValueAsString(row);
        assertTrue(serialized.contains("NewUnknownEnum")); assertFalse(serialized.contains("private"));
        assertEquals("OBSERVED",row.get("normalizedStatus"));
    }

    @Test void actualPunchTimesStaySeparateFromScheduleAndMissingPunches() throws Exception
    {
        String payload="{\"user_task_results\":[{\"result_id\":\"r1\",\"user_id\":\"u1\",\"day\":20260907,\"records\":[{\"check_in_result\":\"Normal\",\"check_out_result\":\"Lack\",\"check_in_shift_time\":\"1788483600\",\"check_in_record\":{\"user_id\":\"u1\",\"check_time\":\"1788483207\",\"location_name\":\"private\"}}]}]}";
        Map<String,Object> row=client.normalizeTasks(json.readTree(payload),"Asia/Shanghai",Arrays.asList("u1"),day).get(0);
        JsonNode result=json.valueToTree(row).path("sourceDetails").path("results").get(0);
        assertEquals(1788483207L,result.path("checkInTime").asLong());
        assertEquals(1788483600L,result.path("scheduledIn").asLong());
        assertTrue(result.path("checkOutTime").isNull());
        assertFalse(json.writeValueAsString(row).contains("private"));
        assertThrows(ServiceException.class,()->client.normalizeTasks(json.readTree(payload.replace("1788483207","invalid")),"Asia/Shanghai",Arrays.asList("u1"),day));
    }

    @Test void clientRequestsApprovedAndWithdrawnStatusesWithEmployeeIdAndNoWriteEndpoint()
    {
        configure();
        RestTemplate http=(RestTemplate)ReflectionTestUtils.getField(client,"http");
        MockRestServiceServer server=MockRestServiceServer.createServer(http);
        server.expect(requestTo("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
            .andExpect(method(HttpMethod.POST)).andRespond(withSuccess("{\"code\":0,\"tenant_access_token\":\"test-token\",\"expire\":7200}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/tenant/v2/tenant/query")).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"tenant\":{\"tenant_key\":\"tenant\"}}}",MediaType.APPLICATION_JSON));
        for(int status:new int[]{2,3}) server.expect(requestTo("https://open.feishu.cn/open-apis/attendance/v1/user_approvals/query?employee_type=employee_id"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer test-token"))
            .andExpect(content().json("{\"status\":"+status+",\"user_ids\":[\"u1\"],\"check_date_from\":20260907,\"check_date_to\":20260907,\"check_date_type\":\"PeriodTime\"}"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"user_approvals\":[]}}",MediaType.APPLICATION_JSON));
        assertTrue(client.query("tenant","Asia/Shanghai","APPROVAL",Arrays.asList("u1"),day).isEmpty()); server.verify();
    }

    @Test void partialAuthorizationCannotAppearAsSuccessfulEmptyAttendance()
    {
        configure(); ReflectionTestUtils.setField(client,"token","test-token"); ReflectionTestUtils.setField(client,"expiresAt",Long.MAX_VALUE);
        ReflectionTestUtils.setField(client,"verifiedTenantToken","test-token");
        MockRestServiceServer server=MockRestServiceServer.createServer((RestTemplate)ReflectionTestUtils.getField(client,"http"));
        server.expect(anything()).andRespond(withSuccess("{\"code\":0,\"data\":{\"user_task_results\":[],\"unauthorized_user_ids\":[\"u1\"]}}",MediaType.APPLICATION_JSON));
        ServiceException ex=assertThrows(ServiceException.class,()->client.query("tenant","Asia/Shanghai","TASK",Arrays.asList("u1"),day));
        assertEquals("FEISHU_SCOPE_REJECTED",ex.getMessage()); server.verify();
    }
    @Test void taskSyncIncludesApprovedRemediesWithoutSavingReasons()
    {
        configure(); ReflectionTestUtils.setField(client,"token","test-token"); ReflectionTestUtils.setField(client,"expiresAt",Long.MAX_VALUE);
        ReflectionTestUtils.setField(client,"verifiedTenantToken","test-token");
        MockRestServiceServer server=MockRestServiceServer.createServer((RestTemplate)ReflectionTestUtils.getField(client,"http"));
        server.expect(requestTo("https://open.feishu.cn/open-apis/attendance/v1/user_tasks/query?employee_type=employee_id"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"user_task_results\":[]}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open.feishu.cn/open-apis/attendance/v1/user_task_remedys/query?employee_type=employee_id"))
            .andExpect(content().json("{\"status\":2,\"user_ids\":[\"u1\"],\"check_date_type\":\"PeriodTime\"}"))
            .andRespond(withSuccess("{\"code\":0,\"data\":{\"user_remedys\":[{\"user_id\":\"u1\",\"approval_id\":\"a1\",\"remedy_date\":20260907,\"remedy_time\":\"2026-09-07 09:00\",\"status\":2,\"reason\":\"private\"}]}}",MediaType.APPLICATION_JSON));
        Map<String,Object> remedy=client.query("tenant","Asia/Shanghai","TASK",Arrays.asList("u1"),day).get(0);
        assertEquals("REMEDY",remedy.get("kind")); assertFalse(remedy.toString().contains("private"));server.verify();
    }
    private void configure()
    { ReflectionTestUtils.setField(client,"enabled",true); ReflectionTestUtils.setField(client,"appId","test-id"); ReflectionTestUtils.setField(client,"appSecret","test-secret"); ReflectionTestUtils.setField(client,"tenantKey","tenant"); }
}
