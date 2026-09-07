package com.ruoyi.business.attendance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/**
 * Feishu attendance v1, verified against official larksuite/oapi-sdk-java on 2026-09-07.
 * These query APIs have no pagination cursor: the service records each user/date chunk.
 * employee_id is kept explicit; identity equivalence must be validated for the real tenant.
 */
@Component
public class FeishuAttendanceClient implements AttendanceProvider
{
    private static final String BASE = "https://open.feishu.cn/open-apis";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Value("${FEISHU_ATTENDANCE_ENABLED:false}") private boolean enabled;
    @Value("${FEISHU_APP_ID:}") private String appId = "";
    @Value("${FEISHU_APP_SECRET:}") private String appSecret = "";
    @Value("${FEISHU_TENANT_KEY:}") private String tenantKey = "";
    private final RestTemplate http;
    private final ObjectMapper json = new ObjectMapper();
    private volatile String token;
    private volatile long expiresAt;
    private volatile String verifiedTenantToken;

    public FeishuAttendanceClient()
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(15000);
        http = new RestTemplate(factory);
    }

    @Override public boolean isConfigured(String tenant)
    {
        return enabled && !appId.trim().isEmpty() && !appSecret.trim().isEmpty()
            && !tenantKey.trim().isEmpty() && tenantKey.equals(tenant);
    }

    @Override public Map<String, Object> configurationStatus()
    {
        return map("enabled", enabled, "appIdPresent", !appId.isEmpty(),
            "appSecretPresent", !appSecret.isEmpty(), "tenantKeyPresent", !tenantKey.isEmpty(),
            "provider", "FEISHU", "adapterVersion", "FEISHU_ATTENDANCE_V1_20260907",
            "identityType", "employee_id", "rawPunchDetailsStored", false);
    }

    @Override public List<Map<String, Object>> query(String tenant, String timezone, String resource,
        List<String> ids, LocalDate date)
    {
        if (!isConfigured(tenant)) throw new ServiceException("FEISHU_NOT_CONFIGURED");
        if (ids == null || ids.isEmpty() || ids.size() > 10) throw new ServiceException("FEISHU_INVALID_SCOPE");
        verifyTenant(tenant);
        ZoneId.of(timezone);
        Map<String, Object> body = map("user_ids", ids, "check_date_from", Integer.parseInt(date.format(DAY)),
            "check_date_to", Integer.parseInt(date.format(DAY)));
        if ("APPROVAL".equals(resource))
        {
            List<Map<String, Object>> result = new ArrayList<>();
            // Explicit status signals; missing rows are never interpreted as cancellation.
            for (int status : new int[] {2, 3})
            {
                body.put("status", status); body.put("check_date_type", "PeriodTime");
                JsonNode data = request("/attendance/v1/user_approvals/query?employee_type=employee_id", body, true);
                result.addAll(normalizeApprovals(data, status, timezone, ids, date));
            }
            rejectAmbiguousKeys(result);
            return result;
        }
        if ("TASK".equals(resource))
        {
            body.put("need_overtime_result", false);
            JsonNode data = request("/attendance/v1/user_tasks/query?employee_type=employee_id", body, true);
            if (data.path("invalid_user_ids").size() > 0 || data.path("unauthorized_user_ids").size() > 0)
                throw new ServiceException("FEISHU_SCOPE_REJECTED");
            return normalizeTasks(data, timezone, ids, date);
        }
        if ("SHIFT".equals(resource))
        {
            JsonNode data = request("/attendance/v1/user_daily_shifts/query?employee_type=employee_id", body, true);
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, JsonNode> shifts = new HashMap<>();
            for (JsonNode row : requiredArray(data, "user_daily_shifts"))
            {
                String user = required(row, "user_id");
                LocalDate businessDate = LocalDate.parse(String.format("%06d%02d", row.path("month").asInt(), row.path("day_no").asInt()), DAY);
                Map<String, Object> out = base(user, "SHIFT:" + user + ":" + businessDate, "SHIFT", businessDate, timezone);
                String shiftId = row.path("shift_id").asText("");
                out.put("sourceStatus", row.path("is_clear_schedule").asBoolean() ? "CLEARED" : "SCHEDULED");
                out.put("sourceDetails", map("shiftId", shiftId, "groupId", row.path("group_id").asText("")));
                if (row.path("is_clear_schedule").asBoolean() || shiftId.isEmpty())
                { out.put("normalizedStatus", "UNKNOWN"); out.put("quality", "UNKNOWN"); }
                else
                {
                    JsonNode shift = shifts.get(shiftId);
                    if (shift == null)
                    {
                        if (!shiftId.matches("[A-Za-z0-9_-]{1,100}")) throw new ServiceException("FEISHU_INVALID_SHIFT_ID");
                        shift = request("/attendance/v1/shifts/" + shiftId, null, true); shifts.put(shiftId, shift);
                    }
                    List<long[]> intervals = normalizeShift(shift, businessDate, timezone);
                    out.put("intervals", intervals);
                    out.put("normalizedStatus", intervals == null ? "UNKNOWN" : "CONFIRMED");
                    out.put("quality", intervals == null ? "UNKNOWN" : "KNOWN");
                }
                checkScope(out, ids, date); result.add(out);
            }
            rejectAmbiguousKeys(result); return result;
        }
        throw new ServiceException("FEISHU_UNSUPPORTED_RESOURCE");
    }

    public List<Map<String, Object>> normalizeApprovals(JsonNode data, int status, String timezone,
        List<String> ids, LocalDate date)
    {
        if (status != 2 && status != 3) throw new ServiceException("FEISHU_UNKNOWN_APPROVAL_STATUS");
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode user : requiredArray(data, "user_approvals"))
        {
            String externalId = required(user, "user_id");
            String zone = user.path("time_zone").asText(timezone); ZoneId.of(zone);
            LocalDate businessDate = LocalDate.parse(required(user, "date"), DAY);
            // Only leave is unavailable time. Travel/out/overtime are not absence.
            for (JsonNode leave : user.path("leaves"))
            {
                // Approval instance is stable across changed type/times/status. Do not include mutable
                // leave type or interval in its key. Multiple rows per instance/day are quarantined.
                String key = required(leave, "approval_id");
                Map<String, Object> out = base(externalId, "LEAVE:" + externalId + ":" + key + ":" + businessDate,
                    "LEAVE", businessDate, zone);
                long start = localInstant(required(leave, "start_time"), zone);
                long end = localInstant(required(leave, "end_time"), zone);
                if (end <= start) throw new ServiceException("FEISHU_INVALID_INTERVAL");
                out.put("intervals", Collections.singletonList(new long[] {start, end}));
                out.put("sourceStatus", String.valueOf(status));
                out.put("normalizedStatus", status == 2 ? "CONFIRMED" : "CANCELED");
                out.put("sourceDurationSeconds", leave.has("interval") ? leave.path("interval").asLong() : null);
                out.put("sourceDetails", map("approvalId", required(leave, "approval_id"),
                    "unit", leave.path("unit").asInt(), "sourceLocalStart", leave.path("start_time").asText(),
                    "sourceLocalEnd", leave.path("end_time").asText()));
                // Reasons, names, location, photo and device fields are deliberately not copied.
                checkScope(out, ids, date); result.add(out);
            }
        }
        rejectAmbiguousKeys(result); return result;
    }

    public List<Map<String, Object>> normalizeTasks(JsonNode data, String timezone, List<String> ids, LocalDate date)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode row : requiredArray(data, "user_task_results"))
        {
            LocalDate businessDate = LocalDate.parse(required(row, "day"), DAY);
            Map<String, Object> out = base(required(row, "user_id"), "TASK:" + required(row, "result_id"),
                "ATTENDANCE", businessDate, timezone);
            List<Map<String, Object>> statuses = new ArrayList<>();
            for (JsonNode record : requiredArray(row, "records"))
                statuses.add(map("checkInResult", record.path("check_in_result").asText("UNKNOWN"),
                    "checkOutResult", record.path("check_out_result").asText("UNKNOWN")));
            out.put("sourceDetails", map("results", statuses));
            // Preserve source enum meanings; do not collapse an unknown code into absent/normal.
            out.put("sourceStatus", "SOURCE_RESULTS"); out.put("normalizedStatus", "OBSERVED");
            Set<String> known=new HashSet<>(Arrays.asList("NoNeedCheck","SystemCheck","Normal","Early","Late","Lack","Todo"));
            if(statuses.isEmpty())out.put("quality","UNKNOWN");
            for(Map<String,Object> status:statuses)if(!known.contains(status.get("checkInResult"))||!known.contains(status.get("checkOutResult")))out.put("quality","UNKNOWN");
            out.put("intervals", Collections.emptyList());
            checkScope(out, ids, date); result.add(out);
        }
        rejectAmbiguousKeys(result); return result;
    }

    /** Fixed shifts only. Flexible/special rules stay UNKNOWN until a validated adapter exists. */
    public static List<long[]> normalizeShift(JsonNode shift, LocalDate date, String timezone)
    {
        if (shift.path("is_flexible").asBoolean() || shift.path("late_off_late_on_rule").size() > 0
            || shift.path("late_off_late_on_setting").size() > 0 || shift.path("rest_time_flexible_configs").size() > 0)
            return null;
        if (shift.path("day_type").asInt() == 2) return Collections.emptyList();
        if (shift.path("day_type").asInt() != 1 || !shift.path("punch_time_rule").isArray()
            || shift.path("punch_time_rule").size() == 0) return null;
        List<long[]> work = new ArrayList<>(), rest = new ArrayList<>();
        for (JsonNode p : shift.path("punch_time_rule"))
            work.add(new long[] {shiftInstant(date, required(p, "on_time"), timezone), shiftInstant(date, required(p, "off_time"), timezone)});
        for (JsonNode p : shift.path("rest_time_rule"))
            rest.add(new long[] {shiftInstant(date, required(p, "rest_begin_time"), timezone), shiftInstant(date, required(p, "rest_end_time"), timezone)});
        return AttendanceIntervals.subtract(work, rest);
    }

    private JsonNode request(String path, Object body, boolean authenticated)
    {
        for (int attempt = 0; attempt < 3; attempt++)
        {
            try
            {
                HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
                if (authenticated) headers.setBearerAuth(accessToken());
                String response = http.exchange(BASE + path, body == null ? HttpMethod.GET : HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class).getBody();
                JsonNode root = json.readTree(response);
                if (!root.has("code")) throw new ServiceException("FEISHU_INVALID_RESPONSE");
                int code = root.path("code").asInt(-1);
                if (code == 0) return authenticated ? root.path("data") : root;
                if (code == 99991663 || code == 99991664 || code == 99991668)
                { token = null; expiresAt = 0; if (attempt < 2 && authenticated) continue; }
                if (code == 99991400 && attempt < 2) { backoff(attempt); continue; }
                // Never include raw provider message/body: it can contain employee details or secrets.
                throw new ServiceException("FEISHU_API_" + code);
            }
            catch (HttpStatusCodeException ex)
            {
                int status = ex.getRawStatusCode();
                if ((status == 429 || status >= 500) && attempt < 2) { backoff(attempt); continue; }
                throw new ServiceException("FEISHU_HTTP_" + status);
            }
            catch (ResourceAccessException ex)
            { if (attempt < 2) { backoff(attempt); continue; } throw new ServiceException("FEISHU_NETWORK_ERROR"); }
            catch (ServiceException ex) { throw ex; }
            catch (Exception ex) { throw new ServiceException("FEISHU_INVALID_RESPONSE"); }
        }
        throw new ServiceException("FEISHU_RETRY_EXHAUSTED");
    }

    private synchronized String accessToken()
    {
        if (token != null && expiresAt > System.currentTimeMillis()) return token;
        JsonNode response = request("/auth/v3/tenant_access_token/internal", map("app_id", appId, "app_secret", appSecret), false);
        token = required(response, "tenant_access_token");
        expiresAt = System.currentTimeMillis() + Math.max(1, response.path("expire").asLong(0) - 120) * 1000;
        return token;
    }

    private synchronized void verifyTenant(String expected)
    {
        String current=accessToken();
        if(current.equals(verifiedTenantToken))return;
        JsonNode tenant=request("/tenant/v2/tenant/query",null,true).path("tenant");
        if(!expected.equals(required(tenant,"tenant_key")))throw new ServiceException("FEISHU_TENANT_MISMATCH");
        verifiedTenantToken=token;
    }

    private static void backoff(int attempt)
    { try { Thread.sleep(300L * (attempt + 1)); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ServiceException("FEISHU_INTERRUPTED"); } }
    private static JsonNode requiredArray(JsonNode node, String field)
    { if (!node.path(field).isArray()) throw new ServiceException("FEISHU_MISSING_" + field.toUpperCase(Locale.ROOT)); return node.path(field); }
    private static String required(JsonNode node, String field)
    { String value = node.path(field).asText(""); if (value.isEmpty()) throw new ServiceException("FEISHU_MISSING_" + field.toUpperCase(Locale.ROOT)); return value; }
    private static void checkScope(Map<String, Object> out, List<String> ids, LocalDate date)
    { if (!ids.contains(out.get("externalUserId")) || !date.toString().equals(out.get("businessDate"))) throw new ServiceException("FEISHU_SCOPE_MISMATCH"); }
    private static void rejectAmbiguousKeys(List<Map<String, Object>> rows)
    { Set<Object> keys = new HashSet<>(); for (Map<String, Object> r : rows) if (!keys.add(r.get("sourceRecordKey"))) throw new ServiceException("FEISHU_AMBIGUOUS_SOURCE_KEY"); }
    private static long localInstant(String value, String zone)
    { LocalDateTime local = LocalDateTime.parse(value, LOCAL); ZoneId id = ZoneId.of(zone); if (id.getRules().getValidOffsets(local).size() != 1) throw new ServiceException("FEISHU_AMBIGUOUS_LOCAL_TIME"); return local.atZone(id).toEpochSecond(); }
    private static long shiftInstant(LocalDate date, String time, String zone)
    { String[] parts = time.split(":"); int hour = Integer.parseInt(parts[0]), minute = Integer.parseInt(parts[1]); if (hour < 0 || hour > 47 || minute < 0 || minute > 59) throw new ServiceException("FEISHU_INVALID_SHIFT_TIME"); return localInstant(date.plusDays(hour / 24).atTime(hour % 24, minute).format(LOCAL), zone); }
    private static Map<String, Object> base(String user, String key, String kind, LocalDate date, String timezone)
    { return map("externalUserId", user, "sourceRecordKey", key, "kind", kind, "businessDate", date.toString(), "sourceTimezone", timezone, "quality", "KNOWN", "adapterVersion", "FEISHU_ATTENDANCE_V1_20260907"); }
    public static Map<String, Object> map(Object... values)
    { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
    public static String sha256(String value)
    { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); for (byte v : digest) b.append(String.format("%02x", v & 255)); return b.toString(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
