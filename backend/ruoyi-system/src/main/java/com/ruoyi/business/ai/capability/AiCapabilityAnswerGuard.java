package com.ruoyi.business.ai.capability;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.ruoyi.common.utils.StringUtils;

/**
 * Validates protected facts in a model answer against the results returned by the
 * capabilities used during the current turn. The model may explain facts, but it
 * may not introduce a new amount, quantity, date or named business entity.
 */
public class AiCapabilityAnswerGuard
{
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final Pattern CN_DATE = Pattern.compile("(?<!\\d)(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern BUSINESS_NUMBER = Pattern.compile(
        "(?<![\\d-])(-?\\d[\\d,]*(?:\\.\\d+)?)\\s*(%|万元|万|元|CNY|人民币|条|人|个|项|天|笔)(?![A-Za-z])",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern LABELED_NAME = Pattern.compile(
        "(?:项目(?:名称)?|负责人|主负责人|成员|人员|归属公司|公司|部门)\\s*(?:[：:]|是|为)\\s*([^，。；;\\n]{1,80})");

    private static final Set<String> NAME_KEYS = setOf(
        "projectname", "companyname", "mainownername", "initiatorname", "username", "nickname",
        "staffname", "assigneename", "ownername", "usernamesnapshot", "submittedusername",
        "reviewedusername", "deptname", "departmentname", "personname", "membername");

    public Validation validate(String answer, List<Map<String, Object>> toolResults)
    {
        if (StringUtils.isBlank(answer)) return Validation.invalid("模型没有生成可显示的回答");
        if (toolResults == null || toolResults.isEmpty()) return Validation.valid();

        Facts facts = new Facts();
        collect(toolResults, null, facts);
        List<String> violations = new ArrayList<String>();
        validateDates(answer, facts, violations);
        validateNumbers(answer, facts, violations);
        validateNames(answer, facts, violations);
        return violations.isEmpty() ? Validation.valid() : Validation.invalid(violations);
    }

    private void collect(Object value, String key, Facts facts)
    {
        if (value == null) return;
        if (value instanceof Map)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                collect(entry.getValue(), String.valueOf(entry.getKey()), facts);
            return;
        }
        if (value instanceof Collection)
        {
            Collection<?> collection = (Collection<?>) value;
            facts.numbers.add(decimal(collection.size()));
            for (Object item : collection) collect(item, key, facts);
            return;
        }
        if (value.getClass().isArray())
        {
            Object[] array = (Object[]) value;
            facts.numbers.add(decimal(array.length));
            for (Object item : array) collect(item, key, facts);
            return;
        }

        String text = String.valueOf(value).trim();
        if (StringUtils.isBlank(text)) return;
        BigDecimal numeric = number(text);
        if (numeric != null) facts.numbers.add(decimal(numeric));
        LocalDate date = date(text);
        if (date != null) facts.dates.add(date);
        Matcher embeddedNumber = BUSINESS_NUMBER.matcher(text);
        while (embeddedNumber.find())
        {
            BigDecimal found = number(embeddedNumber.group(1));
            if (found != null) facts.numbers.add(decimal(found));
        }
        Matcher embeddedDate = ISO_DATE.matcher(text);
        while (embeddedDate.find())
        {
            LocalDate found = date(embeddedDate.group());
            if (found != null) facts.dates.add(found);
        }
        if (key != null && NAME_KEYS.contains(key.toLowerCase(Locale.ROOT))) facts.names.add(normalizeName(text));
    }

    private void validateDates(String answer, Facts facts, List<String> violations)
    {
        Matcher iso = ISO_DATE.matcher(answer);
        while (iso.find())
        {
            LocalDate claimed = date(iso.group());
            if (claimed != null && !facts.dates.contains(claimed))
                add(violations, "日期“" + iso.group() + "”不在本轮系统结果中");
        }
        Matcher chinese = CN_DATE.matcher(answer);
        while (chinese.find())
        {
            Integer year = chinese.group(1) == null ? null : Integer.valueOf(chinese.group(1));
            int month = Integer.parseInt(chinese.group(2));
            int day = Integer.parseInt(chinese.group(3));
            boolean found = false;
            for (LocalDate allowed : facts.dates)
                if ((year == null || allowed.getYear() == year.intValue())
                    && allowed.getMonthValue() == month && allowed.getDayOfMonth() == day)
                    found = true;
            if (!found) add(violations, "日期“" + chinese.group() + "”不在本轮系统结果中");
        }
    }

    private void validateNumbers(String answer, Facts facts, List<String> violations)
    {
        Matcher matcher = BUSINESS_NUMBER.matcher(answer);
        while (matcher.find())
        {
            if (insideIsoDate(answer, matcher.start())) continue;
            BigDecimal claimed = number(matcher.group(1));
            if (claimed == null) continue;
            String unit = matcher.group(2);
            boolean allowed = facts.numbers.contains(decimal(claimed));
            if (!allowed && ("万".equals(unit) || "万元".equals(unit)))
                allowed = facts.numbers.contains(decimal(claimed.multiply(new BigDecimal("10000"))));
            if (!allowed)
                add(violations, "数值“" + matcher.group().trim() + "”不在本轮系统结果中");
        }
    }

    private void validateNames(String answer, Facts facts, List<String> violations)
    {
        Matcher matcher = LABELED_NAME.matcher(answer);
        while (matcher.find())
        {
            String claimed = normalizeName(matcher.group(1));
            if (StringUtils.isBlank(claimed) || isGenericNamePhrase(claimed)) continue;
            boolean allowed = false;
            for (String name : facts.names)
                if (claimed.equals(name) || claimed.startsWith(name + "（") || claimed.startsWith(name + "("))
                    allowed = true;
            if (!allowed && !facts.names.isEmpty())
                add(violations, "名称“" + matcher.group(1).trim() + "”不在本轮系统结果中");
        }
    }

    private boolean insideIsoDate(String text, int position)
    {
        Matcher matcher = ISO_DATE.matcher(text);
        while (matcher.find()) if (position >= matcher.start() && position < matcher.end()) return true;
        return false;
    }

    private boolean isGenericNamePhrase(String value)
    {
        return value.startsWith("如下") || value.startsWith("以下") || value.startsWith("本项目")
            || value.startsWith("该项目") || value.startsWith("当前项目") || value.startsWith("系统");
    }

    private LocalDate date(String value)
    {
        if (value == null) return null;
        String text = value.trim();
        if (!text.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) return null;
        try { return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-M-d")); }
        catch (DateTimeParseException ex) { return null; }
    }

    private BigDecimal number(String value)
    {
        if (value == null) return null;
        String text = value.replace(",", "").trim();
        if (!text.matches("-?\\d+(?:\\.\\d+)?")) return null;
        try { return new BigDecimal(text); }
        catch (NumberFormatException ex) { return null; }
    }

    private String decimal(Number value) { return decimal(new BigDecimal(String.valueOf(value))); }
    private String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private String normalizeName(String value) { return value == null ? "" : value.trim().replaceAll("[。；;]+$", ""); }

    private void add(List<String> violations, String message)
    {
        if (!violations.contains(message) && violations.size() < 8) violations.add(message);
    }

    private static Set<String> setOf(String... values)
    {
        Set<String> result = new HashSet<String>();
        for (String value : values) result.add(value);
        return result;
    }

    private static class Facts
    {
        private final Set<String> numbers = new HashSet<String>();
        private final Set<LocalDate> dates = new HashSet<LocalDate>();
        private final Set<String> names = new HashSet<String>();
    }

    public static class Validation
    {
        private final boolean valid;
        private final List<String> violations;

        private Validation(boolean valid, List<String> violations)
        {
            this.valid = valid;
            this.violations = violations;
        }

        public boolean isValid() { return valid; }
        public List<String> getViolations() { return new ArrayList<String>(violations); }

        public static Validation valid() { return new Validation(true, new ArrayList<String>()); }
        public static Validation invalid(String violation)
        {
            List<String> result = new ArrayList<String>(); result.add(violation);
            return new Validation(false, result);
        }
        public static Validation invalid(List<String> violations)
        {
            return new Validation(false, new ArrayList<String>(violations));
        }

        public Map<String, Object> toMap(String status)
        {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("status", status);
            result.put("violations", getViolations());
            return result;
        }
    }
}
