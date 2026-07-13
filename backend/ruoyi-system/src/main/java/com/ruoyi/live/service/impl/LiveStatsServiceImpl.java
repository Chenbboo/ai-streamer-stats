package com.ruoyi.live.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    public List<Map<String, Object>> getWeijiStats(String statDate)
    {
        return statsMapper.selectWeijiStats(statDate);
    }

    @Override
    public List<Map<String, Object>> getWeijiMonthStats(String beginDate, String endDate)
    {
        return statsMapper.selectWeijiMonthStats(beginDate, endDate);
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
    public List<Map<String, Object>> getAdviceData(String beginDate, String endDate)
    {
        List<Map<String, Object>> dataList = statsMapper.selectAdviceData(beginDate, endDate);
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
