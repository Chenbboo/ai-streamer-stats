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
    @Value("${FEISHU_ATTENDANCE_POLL_ENABLED:false}") private boolean pollEnabled;
    @Value("${FEISHU_ATTENDANCE_POLL_DELAY_MS:900000}") private long pollDelayMs = 900000;
    @Value("${FEISHU_ATTENDANCE_POLL_LOOKBACK_DAYS:3}") private int pollLookbackDays = 3;
    private final Object rateLock = new Object();
    private long nextRequestNanos;
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
            "provider", "FEISHU", "adapterVersion", "FEISHU_ATTENDANCE_V2_20260907",
            "identityType", "employee_id", "rawPunchDetailsStored", false,
            "pollEnabled", pollEnabled, "pollDelayMs", pollDelayMs,
            "pollLookbackDays", Math.max(1, Math.min(7, pollLookbackDays)), "requestLimitPerSecond", 5);
    }

    /** Complete authorized directory only. Never return a truncated list as a full scope. */
    @Override public List<Map<String, Object>> directory(String tenant)
    {
        if (!isConfigured(tenant)) throw new ServiceException("FEISHU_NOT_CONFIGURED");
        verifyTenant(tenant);
        long deadline = System.nanoTime() + 120_000_000_000L;
        Set<String> users = new LinkedHashSet<>(), departments = new LinkedHashSet<>();
        for (JsonNode page : contactPages("/contact/v3/scopes?user_id_type=user_id&department_id_type=open_department_id&page_size=100", deadline))
        {
            for (JsonNode id : optionalArray(page, "user_ids")) users.add(contactId(id.asText()));
            for (JsonNode id : optionalArray(page, "department_ids")) departments.add(contactId(id.asText()));
        }
        // Scope departments include their children. Query only explicitly granted roots.
        for (String root : new ArrayList<>(departments))
            for (JsonNode page : contactPages("/contact/v3/departments/" + root + "/children?user_id_type=user_id&department_id_type=open_department_id&fetch_child=true&page_size=50", deadline))
                for (JsonNode d : optionalArray(page, "items")) departments.add(contactId(required(d,"open_department_id")));
        if (departments.size() > 200) throw new ServiceException("FEISHU_DIRECTORY_TOO_LARGE");
        Map<String,Map<String,Object>> result = new LinkedHashMap<>();
        for (String department : departments)
            for (JsonNode page : contactPages("/contact/v3/users/find_by_department?user_id_type=user_id&department_id_type=open_department_id&department_id=" + department + "&page_size=50", deadline))
                for (JsonNode user : optionalArray(page, "items")) addDirectoryUser(result, user);
        for (String id : users)
        {
            if (result.containsKey(id)) continue;
            checkDirectoryDeadline(deadline);
            JsonNode user = request("/contact/v3/users/" + id + "?user_id_type=user_id", null, true).path("user");
            if (!id.equals(required(user,"user_id"))) throw new ServiceException("FEISHU_SCOPE_MISMATCH");
            addDirectoryUser(result, user);
        }
        return new ArrayList<>(result.values());
    }

    private List<JsonNode> contactPages(String path, long deadline)
    {
        List<JsonNode> pages = new ArrayList<>(); Set<String> tokens = new HashSet<>(); String cursor = "";
        for (int page = 0; page < 100; page++)
        {
            checkDirectoryDeadline(deadline);
            JsonNode data = request(path + (cursor.isEmpty() ? "" : "&page_token=" + org.springframework.web.util.UriUtils.encode(cursor, StandardCharsets.UTF_8)), null, true);
            if (!data.path("has_more").isBoolean()) throw new ServiceException("FEISHU_DIRECTORY_INVALID_PAGE");
            pages.add(data);
            if (!data.path("has_more").asBoolean()) return pages;
            cursor = required(data,"page_token");
            if (!tokens.add(cursor)) throw new ServiceException("FEISHU_DIRECTORY_REPEATED_CURSOR");
        }
        throw new ServiceException("FEISHU_DIRECTORY_TOO_LARGE");
    }
    private static Iterable<JsonNode> optionalArray(JsonNode node, String field)
    {
        if (!node.has(field)) return Collections.emptyList();
        return requiredArray(node,field);
    }
    private static String contactId(String id)
    {
        if (!id.matches("[A-Za-z0-9_-]{1,128}")) throw new ServiceException("FEISHU_INVALID_CONTACT_ID");
        return id;
    }
    private static void checkDirectoryDeadline(long deadline)
    { if (System.nanoTime() > deadline) throw new ServiceException("FEISHU_DIRECTORY_TIMEOUT"); }
    private static void addDirectoryUser(Map<String,Map<String,Object>> result, JsonNode user)
    {
        String id = contactId(required(user,"user_id"));
        // Keep only identity and a review label; do not copy mobile, email, avatar or other profile fields.
        Map<String,Object> row = map("externalUserId",id,"name",required(user,"name"),
            "unavailable",user.path("status").path("is_resigned").asBoolean() || user.path("status").path("is_frozen").asBoolean());
        Map<String,Object> previous = result.put(id,row);
        if (previous != null && !previous.equals(row)) throw new ServiceException("FEISHU_SOURCE_CHANGED_DURING_READ");
        if (result.size() > 2000) throw new ServiceException("FEISHU_DIRECTORY_TOO_LARGE");
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
            List<Map<String, Object>> result = normalizeTasks(data, timezone, ids, date);
            Map<String, Object> remedyBody = map("user_ids", ids, "status", 2, "check_date_type", "PeriodTime",
                "check_time_from", String.valueOf(date.atStartOfDay(ZoneId.of(timezone)).toEpochSecond()),
                "check_time_to", String.valueOf(date.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toEpochSecond() - 1));
            JsonNode remedies = request("/attendance/v1/user_task_remedys/query?employee_type=employee_id", remedyBody, true);
            for (JsonNode remedy : requiredArray(remedies, "user_remedys"))
            {
                Map<String,Object> out = base(required(remedy,"user_id"), "REMEDY:" + required(remedy,"approval_id")
                    + ":" + remedy.path("punch_no").asInt() + ":" + remedy.path("work_type").asInt(), "REMEDY", date, timezone);
                if (!date.format(DAY).equals(required(remedy,"remedy_date"))) throw new ServiceException("FEISHU_SCOPE_MISMATCH");
                out.put("sourceStatus", required(remedy,"status"));
                out.put("normalizedStatus", remedy.path("status").asInt() == 2 ? "CONFIRMED" : "UNKNOWN");
                out.put("sourceDetails", map("remedyTime",remedy.path("remedy_time").asText("")));
                out.put("intervals", Collections.emptyList());
                checkScope(out, ids, date); result.add(out);
            }
            rejectAmbiguousKeys(result);
            return result;
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
            {
                statuses.add(map("checkInResult", record.path("check_in_result").asText("UNKNOWN"),
                    "checkOutResult", record.path("check_out_result").asText("UNKNOWN"),
                    "checkInTime", punchTime(record.path("check_in_record"), required(row,"user_id")),
                    "checkOutTime", punchTime(record.path("check_out_record"), required(row,"user_id")),
                    "scheduledIn", optionalEpoch(record.path("check_in_shift_time")),
                    "scheduledOut", optionalEpoch(record.path("check_out_shift_time"))));
            }
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

    private static Long optionalEpoch(JsonNode value)
    {
        if (value.isMissingNode() || value.isNull() || value.asText().isEmpty() || "0".equals(value.asText())) return null;
        try { long seconds = Long.parseLong(value.asText()); if (seconds <= 0 || seconds > 253402300799L) throw new NumberFormatException(); return seconds; }
        catch (NumberFormatException ex) { throw new ServiceException("FEISHU_INVALID_PUNCH_TIME"); }
    }

    private static Long punchTime(JsonNode record, String user)
    {
        if (record.hasNonNull("user_id") && !record.path("user_id").asText().isEmpty() && !user.equals(record.path("user_id").asText()))
            throw new ServiceException("FEISHU_SCOPE_MISMATCH");
        return optionalEpoch(record.path("check_time"));
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
                acquireRequestSlot();
                String response = http.exchange(java.net.URI.create(BASE + path), body == null ? HttpMethod.GET : HttpMethod.POST,
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

    /** Shared by contact, attendance and retries within this backend instance. */
    private void acquireRequestSlot()
    {
        synchronized (rateLock)
        {
            long remaining = nextRequestNanos - System.nanoTime();
            if (remaining > 0) try { java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ServiceException("FEISHU_INTERRUPTED"); }
            nextRequestNanos = System.nanoTime() + 200_000_000L;
        }
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
    { return map("externalUserId", user, "sourceRecordKey", key, "kind", kind, "businessDate", date.toString(), "sourceTimezone", timezone, "quality", "KNOWN", "adapterVersion", "FEISHU_ATTENDANCE_V2_20260907"); }
    public static Map<String, Object> map(Object... values)
    { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
    public static String sha256(String value)
    { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); for (byte v : digest) b.append(String.format("%02x", v & 255)); return b.toString(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
