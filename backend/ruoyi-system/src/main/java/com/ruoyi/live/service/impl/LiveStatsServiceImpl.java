package com.ruoyi.live.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.live.mapper.LiveStatsMapper;
import com.ruoyi.live.service.ILiveStatsService;

@Service
public class LiveStatsServiceImpl implements ILiveStatsService
{
    @Autowired
    private LiveStatsMapper statsMapper;

    @Override
    public Map<String, Object> getWeeklyStats(String beginDate, String endDate, Long streamerId)
    {
        LocalDate begin = LocalDate.parse(beginDate);
        LocalDate end = LocalDate.parse(endDate);
        long days = ChronoUnit.DAYS.between(begin, end) + 1;
        String previousEnd = begin.minusDays(1).toString();
        String previousBegin = begin.minusDays(days).toString();

        List<Map<String, Object>> cards = statsMapper.selectStreamerCards(beginDate, endDate, streamerId);
        Map<String, BigDecimal> previousTotals = statsMapper.selectPreviousTotals(previousBegin, previousEnd, streamerId)
                .stream()
                .collect(Collectors.toMap(row -> stringValue(row.get("streamerId")), row -> decimalValue(row.get("previousTotalXu"))));

        BigDecimal totalXu = BigDecimal.ZERO;
        BigDecimal totalGiftXu = BigDecimal.ZERO;
        int totalGiftCustomers = 0;
        int totalChatCustomers = 0;
        for (Map<String, Object> card : cards)
        {
            BigDecimal current = decimalValue(card.get("totalXu"));
            BigDecimal previous = previousTotals.getOrDefault(stringValue(card.get("streamerId")), BigDecimal.ZERO);
            card.put("previousTotalXu", previous);
            card.put("changeRate", changeRate(current, previous));
            card.put("health", health(current, previous));
            totalXu = totalXu.add(current);
            totalGiftXu = totalGiftXu.add(decimalValue(card.get("giftXu")));
            totalGiftCustomers += intValue(card.get("giftCustomers"));
            totalChatCustomers += intValue(card.get("chatCustomers"));
        }

        Map<String, Object> overview = new HashMap<>();
        overview.put("streamerCount", cards.size());
        overview.put("totalXu", totalXu);
        overview.put("totalGiftXu", totalGiftXu);
        overview.put("giftCustomers", totalGiftCustomers);
        overview.put("chatCustomers", totalChatCustomers);
        overview.put("beginDate", beginDate);
        overview.put("endDate", endDate);

        Map<String, Object> result = new HashMap<>();
        result.put("overview", overview);
        result.put("cards", cards);
        result.put("trend", statsMapper.selectTrend(beginDate, endDate, streamerId));
        result.put("customerCards", statsMapper.selectCustomerCards(beginDate, endDate, streamerId));
        return result;
    }

    private String health(BigDecimal current, BigDecimal previous)
    {
        if (current.compareTo(previous) >= 0)
        {
            return "good";
        }
        if (previous.compareTo(BigDecimal.ZERO) == 0)
        {
            return "quiet";
        }
        BigDecimal rate = current.subtract(previous).multiply(new BigDecimal("100")).divide(previous, 1, BigDecimal.ROUND_HALF_UP);
        return rate.compareTo(new BigDecimal("-30")) <= 0 ? "risk" : "watch";
    }

    private BigDecimal changeRate(BigDecimal current, BigDecimal previous)
    {
        if (previous.compareTo(BigDecimal.ZERO) == 0)
        {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : new BigDecimal("100");
        }
        return current.subtract(previous).multiply(new BigDecimal("100")).divide(previous, 1, BigDecimal.ROUND_HALF_UP);
    }

    private BigDecimal decimalValue(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private int intValue(Object value)
    {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private String stringValue(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public List<Map<String, Object>> getStreamerCardDetail(Long streamerId, String beginDate, String endDate)
    {
        return statsMapper.selectStreamerCardDetail(streamerId, beginDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getHighValueUsers(Long streamerId, String beginDate, String endDate)
    {
        return statsMapper.selectHighValueUsers(streamerId, beginDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getNewTippers(Long streamerId, String beginDate, String endDate)
    {
        return statsMapper.selectNewTippers(streamerId, beginDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getWeijiStats(String statDate, Long streamerId)
    {
        return statsMapper.selectWeijiStats(statDate, streamerId);
    }

    @Override
    public List<Map<String, Object>> getWeijiMonthStats(String beginDate, String endDate, Long streamerId)
    {
        return statsMapper.selectWeijiMonthStats(beginDate, endDate, streamerId);
    }

    @Override
    public List<Map<String, Object>> getWeijiDetail(Long streamerId, String beginDate, String endDate)
    {
        return statsMapper.selectWeijiDetail(streamerId, beginDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getRecentChatRecords(Long streamerId, int limit)
    {
        return statsMapper.selectRecentChatRecords(streamerId, limit);
    }

    @Override
    public List<Map<String, Object>> getRecentTipRecords(Long streamerId, int limit)
    {
        return statsMapper.selectRecentTipRecords(streamerId, limit);
    }

    @Override
    public List<Map<String, Object>> getChatContent(Long streamerId, int limit)
    {
        return statsMapper.selectChatContent(streamerId, limit);
    }

    @Override
    public List<Map<String, Object>> getAdviceData(String beginDate, String endDate, Long streamerId)
    {
        List<Map<String, Object>> dataList = statsMapper.selectAdviceData(beginDate, endDate, streamerId);
        for (Map<String, Object> item : dataList)
        {
            long monthlyXu = longValue(item.get("monthlyXu"));
            int tipCustomers = intValue(item.get("tipCustomers"));
            int chatCustomers = intValue(item.get("chatCustomers"));
            int silentCount = intValue(item.get("silentCount"));
            int weijiTotal = intValue(item.get("weijiTotal"));

            // 计算沉默率
            int silentPct = weijiTotal > 0 ? Math.round(silentCount * 100f / weijiTotal) : 0;

            // 构建状态文本
            String status = "月流水 " + formatNumber(monthlyXu) + " · 送礼客户 " + tipCustomers + " · 聊天客户 " + chatCustomers + " · 沉默率 " + silentPct + "%";
            item.put("status", status);

            // 构建建议
            java.util.List<String> tips = new java.util.ArrayList<>();
            if (silentPct > 50)
            {
                tips.add("沉默率过高(" + silentPct + "%)，需要加强粉丝互动");
            }
            if (tipCustomers > 0 && chatCustomers < tipCustomers / 2)
            {
                tips.add("送礼客户(" + tipCustomers + ")远多于聊天客户(" + chatCustomers + ")，建议主动私信维护");
            }
            if (monthlyXu > 0 && tipCustomers < 5)
            {
                tips.add("打赏客户较少(" + tipCustomers + "人)，建议扩大粉丝基础");
            }
            if (tips.isEmpty())
            {
                tips.add("数据正常，继续保持");
            }
            item.put("tips", tips);

            // 构建告警
            String alert = "";
            if (silentPct > 70)
            {
                alert = "⚠ 沉默率 " + silentPct + "%，超过70%警戒线";
            }
            item.put("alert", alert);
        }
        return dataList;
    }

    @Override
    public List<Map<String, Object>> getCustomerMaintenanceMatrix(String beginDate, String endDate, Long streamerId)
    {
        return statsMapper.selectCustomerMaintenanceMatrix(beginDate, endDate, streamerId);
    }

    @Override
    public List<Map<String, Object>> getStreamerDailyList(String beginDate, String endDate, Long streamerId)
    {
        LocalDate begin = LocalDate.parse(beginDate);
        LocalDate end = LocalDate.parse(endDate);
        if (begin.isAfter(end))
        {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        if (!begin.withDayOfMonth(1).equals(end.withDayOfMonth(1)))
        {
            throw new ServiceException("主播数据列表一次只能查看一个自然月");
        }
        if (ChronoUnit.DAYS.between(begin, end) >= 31)
        {
            throw new ServiceException("查询日期不能超过31天");
        }

        String monthBegin = begin.withDayOfMonth(1).toString();
        List<Map<String, Object>> maintenance = statsMapper.selectCustomerMaintenanceMatrix(monthBegin, endDate, streamerId);
        maintenance.sort(Comparator.comparing(row -> LocalDate.parse(stringValue(row.get("bizDate")))));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1))
        {
            String dateText = date.toString();
            List<Map<String, Object>> rows = statsMapper.selectStreamerCardDetail(streamerId, dateText, dateText);
            for (Map<String, Object> row : rows)
            {
                Long rowStreamerId = Long.valueOf(stringValue(row.get("streamerId")));
                row.putAll(calculateMaintenance(maintenance, rowStreamerId, date));
            }

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("bizDate", dateText);
            day.put("rows", rows);
            result.add(day);
        }
        return result;
    }

    private Map<String, Object> calculateMaintenance(List<Map<String, Object>> records, Long streamerId, LocalDate cutoff)
    {
        Map<String, MaintenanceState> customers = new HashMap<>();
        for (Map<String, Object> record : records)
        {
            if (!streamerId.equals(Long.valueOf(stringValue(record.get("streamerId")))))
            {
                continue;
            }
            LocalDate bizDate = LocalDate.parse(stringValue(record.get("bizDate")));
            if (bizDate.isAfter(cutoff))
            {
                break;
            }

            String customerId = stringValue(record.get("customerId"));
            MaintenanceState state = customers.computeIfAbsent(customerId, key -> new MaintenanceState());
            long giftXu = longValue(record.get("giftXu"));
            boolean hasInteraction = intValue(record.get("hasInteraction")) == 1;
            boolean hasContact = intValue(record.get("hasContact")) == 1;
            boolean hasPendingFollow = intValue(record.get("hasPendingFollow")) == 1;

            state.totalXu += giftXu;
            if (giftXu > 0)
            {
                state.lastGiftDate = bizDate;
                state.lastGiftHasInteraction = hasInteraction;
                if (bizDate.equals(cutoff))
                {
                    state.giftToday = true;
                    state.todayHasInteraction = hasInteraction;
                    state.todayHasPendingFollow = hasPendingFollow;
                }
            }
            if (hasContact)
            {
                state.lastContactDate = bizDate;
            }
            if (hasPendingFollow)
            {
                state.hasPendingFollow = true;
            }
        }

        int monthHighValue = 0;
        int monthNoInteraction = 0;
        int monthMaintained = 0;
        int dailyNoInteraction = 0;
        int dailyMaintained = 0;
        for (MaintenanceState state : customers.values())
        {
            if (state.totalXu < 1000)
            {
                continue;
            }
            monthHighValue++;
            boolean contactedAfterLastGift = state.lastContactDate != null && state.lastGiftDate != null
                    && state.lastContactDate.isAfter(state.lastGiftDate);
            boolean unmaintained = !state.lastGiftHasInteraction && !contactedAfterLastGift && !state.hasPendingFollow;
            if (unmaintained)
            {
                monthNoInteraction++;
            }
            else
            {
                monthMaintained++;
            }

            if (state.giftToday)
            {
                if (!state.todayHasInteraction && !state.todayHasPendingFollow)
                {
                    dailyNoInteraction++;
                }
                else
                {
                    dailyMaintained++;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("monthHighValue", monthHighValue);
        result.put("monthNoInteraction", monthNoInteraction);
        result.put("monthMaintained", monthMaintained);
        result.put("dailyNoInteraction", dailyNoInteraction);
        result.put("dailyMaintained", dailyMaintained);
        return result;
    }

    private static class MaintenanceState
    {
        private long totalXu;
        private LocalDate lastGiftDate;
        private LocalDate lastContactDate;
        private boolean lastGiftHasInteraction;
        private boolean hasPendingFollow;
        private boolean giftToday;
        private boolean todayHasInteraction;
        private boolean todayHasPendingFollow;
    }

    private long longValue(Object value)
    {
        if (value == null) return 0;
        return Long.parseLong(String.valueOf(value));
    }

    private String formatNumber(long n)
    {
        return String.format("%,d", n);
    }
}
