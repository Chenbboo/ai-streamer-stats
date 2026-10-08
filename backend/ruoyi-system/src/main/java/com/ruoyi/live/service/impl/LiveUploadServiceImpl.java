package com.ruoyi.live.service.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.live.domain.LiveDailySummary;
import com.ruoyi.live.domain.LiveUpload;
import com.ruoyi.live.mapper.LiveUploadMapper;
import com.ruoyi.live.service.ILiveRecognitionService;
import com.ruoyi.live.service.ILiveUploadService;

/**
 * 上传记录 服务实现
 */
@Service
public class LiveUploadServiceImpl implements ILiveUploadService
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_CONFIRM_BATCH_SIZE = 1000;

    @Autowired
    private LiveUploadMapper uploadMapper;

    @Autowired
    private ILiveRecognitionService recognitionService;

    @Override
    public List<LiveUpload> selectLiveUploadList(LiveUpload upload)
    {
        return uploadMapper.selectLiveUploadList(upload);
    }

    @Override
    public LiveUpload selectLiveUploadById(Long uploadId)
    {
        return uploadMapper.selectLiveUploadById(uploadId);
    }

    @Override
    public List<LiveDailySummary> selectDailySummary(LiveUpload upload)
    {
        return uploadMapper.selectDailySummary(upload);
    }

    @Override
    public int insertLiveUpload(LiveUpload upload)
    {
        return uploadMapper.insertLiveUpload(upload);
    }

    @Override
    @Transactional
    public int correctReportBizDate(Long uploadId, Date bizDate)
    {
        LiveUpload upload = uploadMapper.selectLiveUploadById(uploadId);
        if (upload == null)
        {
            throw new ServiceException("上传记录不存在");
        }
        if (!LiveUpload.TYPE_REPORT.equals(upload.getUploadType()))
        {
            throw new ServiceException("只有工作汇报可以修正日期");
        }
        if (uploadMapper.countDailyReportConflict(upload.getStreamerId(), uploadId, bizDate) > 0)
        {
            throw new ServiceException("目标日期已有该主播的日报，不能覆盖");
        }

        int reportRows = uploadMapper.updateDailyReportBizDateByUploadId(uploadId, bizDate);
        if ("2".equals(upload.getAiStatus()) && reportRows != 1)
        {
            throw new ServiceException("已确认汇报缺少对应日报，无法安全修正日期");
        }
        return uploadMapper.updateBizDateById(uploadId, bizDate);
    }

    @Override
    @Transactional
    public int deleteLiveUploadByIds(Long[] uploadIds)
    {
        // 先查出文件路径,删库成功后删磁盘文件
        List<LiveUpload> records = uploadMapper.selectLiveUploadByIds(uploadIds);
        uploadMapper.deleteDailyReportByUploadIds(uploadIds);
        uploadMapper.deleteFollowRecordByUploadIds(uploadIds);
        int rows = uploadMapper.deleteLiveUploadByIds(uploadIds);
        if (rows > 0)
        {
            for (LiveUpload record : records)
            {
                String filePath = record.getFilePath();
                if (StringUtils.isNotEmpty(filePath) && filePath.startsWith(Constants.RESOURCE_PREFIX))
                {
                    String absPath = RuoYiConfig.getProfile() + StringUtils.substringAfter(filePath, Constants.RESOURCE_PREFIX);
                    File file = new File(absPath);
                    if (file.exists())
                    {
                        file.delete();
                    }
                }
            }
        }
        return rows;
    }

    @Override
    public int mockRecognize(Long uploadId)
    {
        LiveUpload upload = uploadMapper.selectLiveUploadById(uploadId);
        if (upload == null)
        {
            throw new ServiceException("上传记录不存在");
        }
        return uploadMapper.updateAiResult(uploadId, "1", buildMockAiResult(upload));
    }

    @Override
    public int recognizeUpload(Long uploadId)
    {
        LiveUpload upload = uploadMapper.selectLiveUploadById(uploadId);
        if (upload == null)
        {
            throw new ServiceException("上传记录不存在");
        }
        if ("2".equals(upload.getAiStatus()))
        {
            throw new ServiceException("已确认入库的数据不能重新识别");
        }
        String previousResult = upload.getAiResult();
        uploadMapper.updateAiResult(uploadId, "4", previousResult);
        try
        {
            return uploadMapper.updateAiResult(uploadId, "1", recognitionService.recognize(upload));
        }
        catch (Exception e)
        {
            // 失败时保留原有识别/校正结果,不被错误信息覆盖
            String fallback = StringUtils.isEmpty(previousResult)
                    ? "{\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}"
                    : previousResult;
            uploadMapper.updateAiResult(uploadId, "3", fallback);
            throw new ServiceException("AI识别失败:" + e.getMessage());
        }
    }

    @Override
    public int saveRecognizeResult(Long uploadId, String aiResult)
    {
        LiveUpload upload = uploadMapper.selectLiveUploadById(uploadId);
        if (upload == null)
        {
            throw new ServiceException("上传记录不存在");
        }
        readAiResult(aiResult);
        return uploadMapper.updateAiResult(uploadId, "1", aiResult);
    }

    @Override
    @Transactional
    public int confirmRecognize(Long uploadId)
    {
        LiveUpload upload = uploadMapper.selectLiveUploadById(uploadId);
        if (upload == null)
        {
            throw new ServiceException("上传记录不存在");
        }
        upload.setUpdateBy(SecurityUtils.getUsername());
        return confirmRecognize(upload, new HashMap<String, Long>(), new HashSet<String>());
    }

    @Override
    @Transactional
    public int confirmRecognizeBatch(Long[] uploadIds)
    {
        if (uploadIds == null || uploadIds.length == 0)
        {
            throw new ServiceException("请选择需要确认入库的图片");
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<Long>();
        for (Long uploadId : uploadIds)
        {
            if (uploadId != null)
            {
                distinctIds.add(uploadId);
            }
        }
        if (distinctIds.isEmpty())
        {
            throw new ServiceException("请选择需要确认入库的图片");
        }
        if (distinctIds.size() > MAX_CONFIRM_BATCH_SIZE)
        {
            throw new ServiceException("单次最多确认" + MAX_CONFIRM_BATCH_SIZE + "张图片");
        }

        List<LiveUpload> records = uploadMapper.selectLiveUploadByIds(distinctIds.toArray(new Long[0]));
        Map<Long, LiveUpload> recordsById = new HashMap<Long, LiveUpload>();
        for (LiveUpload record : records)
        {
            recordsById.put(record.getUploadId(), record);
        }
        List<LiveUpload> orderedRecords = new ArrayList<LiveUpload>(distinctIds.size());
        for (Long uploadId : distinctIds)
        {
            LiveUpload record = recordsById.get(uploadId);
            if (record == null)
            {
                throw new ServiceException("上传记录不存在:" + uploadId);
            }
            orderedRecords.add(record);
        }

        String username = SecurityUtils.getUsername();
        Map<String, Long> customerIds = new HashMap<String, Long>();
        Set<String> customerTouches = new HashSet<String>();
        for (LiveUpload record : orderedRecords)
        {
            record.setUpdateBy(username);
            confirmRecognize(record, customerIds, customerTouches);
        }
        return orderedRecords.size();
    }

    private int confirmRecognize(LiveUpload upload, Map<String, Long> customerIds, Set<String> customerTouches)
    {
        if ("2".equals(upload.getAiStatus()))
        {
            return 1;
        }
        JsonNode result = readAiResult(upload.getAiResult());
        if (LiveUpload.TYPE_GIFT.equals(upload.getUploadType()))
        {
            confirmGift(upload, result, customerIds, customerTouches);
        }
        else if (LiveUpload.TYPE_CHAT.equals(upload.getUploadType()))
        {
            confirmChat(upload, result, customerIds, customerTouches);
        }
        else if (LiveUpload.TYPE_REPORT.equals(upload.getUploadType()))
        {
            Integer totalXu = result.has("totalXu") && !result.path("totalXu").isNull()
                    ? result.path("totalXu").asInt()
                    : parseTotalXu(upload.getRawText());
            String rawText = result.path("rawText").asText(upload.getRawText());
            uploadMapper.upsertDailyReport(upload, totalXu, rawText);
        }
        else if (LiveUpload.TYPE_FOLLOW.equals(upload.getUploadType()))
        {
            confirmFollow(upload, result, customerIds, customerTouches);
        }
        else
        {
            throw new ServiceException("上传类型不正确");
        }
        return uploadMapper.updateAiResult(upload.getUploadId(), "2", upload.getAiResult());
    }

    private void confirmGift(LiveUpload upload, JsonNode result, Map<String, Long> customerIds,
            Set<String> customerTouches)
    {
        JsonNode items = result.path("items");
        if (!items.isArray() || items.size() == 0)
        {
            throw new ServiceException("礼物榜识别结果不能为空");
        }
        int index = 1;
        int saved = 0;
        for (JsonNode item : items)
        {
            String nickname = item.path("nickname").asText("");
            if (StringUtils.isEmpty(nickname))
            {
                continue;
            }
            String badge = item.path("badge").asText("");
            Integer rankNo = item.path("rankNo").asInt(index);
            Integer xu = item.path("xu").asInt(0);
            confirmGift(upload, nickname, badge, rankNo, xu, customerIds, customerTouches);
            index++;
            saved++;
        }
        if (saved == 0)
        {
            throw new ServiceException("礼物榜识别结果没有可入库的客户昵称");
        }
    }

    private void confirmGift(LiveUpload upload, String nickname, String badge, Integer rankNo, Integer xu,
            Map<String, Long> customerIds, Set<String> customerTouches)
    {
        Long customerId = resolveCustomerId(upload, nickname, badge, customerIds, customerTouches);
        uploadMapper.upsertGiftRecord(upload, customerId, rankNo, xu);
    }

    private void confirmChat(LiveUpload upload, JsonNode result, Map<String, Long> customerIds,
            Set<String> customerTouches)
    {
        JsonNode items = result.path("items");
        if (!items.isArray() || items.size() == 0)
        {
            throw new ServiceException("聊天截图识别结果不能为空");
        }
        int saved = 0;
        for (JsonNode item : items)
        {
            String nickname = item.path("nickname").asText("");
            if (StringUtils.isEmpty(nickname))
            {
                continue;
            }
            String badge = item.path("badge").asText("");
            // 判断是否有互动：customer 至少回复了1条消息
            boolean hasInteraction = false;
            JsonNode messages = item.path("messages");
            if (messages.isArray())
            {
                for (JsonNode msg : messages)
                {
                    if ("customer".equals(msg.path("sender").asText("")))
                    {
                        hasInteraction = true;
                        break;
                    }
                }
            }
            confirmChat(upload, nickname, badge, hasInteraction, customerIds, customerTouches);
            saved++;
        }
        if (saved == 0)
        {
            throw new ServiceException("聊天截图识别结果没有可入库的客户昵称");
        }
    }

    private void confirmFollow(LiveUpload upload, JsonNode result, Map<String, Long> customerIds,
            Set<String> customerTouches)
    {
        JsonNode items = result.path("items");
        if (!items.isArray() || items.size() == 0)
        {
            throw new ServiceException("关注关系识别结果不能为空");
        }
        int saved = 0;
        for (JsonNode item : items)
        {
            String nickname = item.path("nickname").asText("").trim();
            if (StringUtils.isEmpty(nickname)) continue;
            String followStatus = item.path("followStatus").asText("pending");
            Long customerId = resolveCustomerId(upload, nickname, item.path("badge").asText(""),
                    customerIds, customerTouches);
            if (customerId != null)
            {
                uploadMapper.upsertFollowRecord(upload, customerId, followStatus);
                saved++;
            }
        }
        if (saved == 0)
        {
            throw new ServiceException("关注关系识别结果没有可匹配的客户昵称");
        }
    }

    private void confirmChat(LiveUpload upload, String nickname, String badge, boolean hasInteraction,
            Map<String, Long> customerIds, Set<String> customerTouches)
    {
        Long customerId = resolveCustomerId(upload, nickname, badge, customerIds, customerTouches);
        uploadMapper.upsertChatContact(upload, customerId, hasInteraction ? 1 : 0);
    }

    private Long resolveCustomerId(LiveUpload upload, String nickname, String badge,
            Map<String, Long> customerIds, Set<String> customerTouches)
    {
        String identityKey = upload.getStreamerId() + ":" + nickname.length() + ":" + nickname;
        String touchKey = identityKey + ":" + upload.getBizDate() + ":" + badge.length() + ":" + badge;
        if (customerTouches.add(touchKey))
        {
            uploadMapper.insertCustomerIfAbsent(nickname, badge, upload);
        }
        Long customerId = customerIds.get(identityKey);
        if (customerId == null)
        {
            customerId = uploadMapper.selectCustomerIdByNickname(nickname, upload.getStreamerId());
            if (customerId == null)
            {
                throw new ServiceException("客户入库失败:" + nickname);
            }
            customerIds.put(identityKey, customerId);
        }
        return customerId;
    }

    private String buildMockAiResult(LiveUpload upload)
    {
        if (LiveUpload.TYPE_GIFT.equals(upload.getUploadType()))
        {
            return "{\"type\":\"gift\",\"items\":[{\"rankNo\":1,\"nickname\":\"MockTopFan\",\"xu\":8888,\"badge\":\"MWRM\",\"confidence\":\"normal\"},{\"rankNo\":2,\"nickname\":\"DemoBuyer\",\"xu\":6666,\"badge\":\"\",\"confidence\":\"normal\"}]}";
        }
        if (LiveUpload.TYPE_CHAT.equals(upload.getUploadType()))
        {
            return "{\"type\":\"chat\",\"items\":[{\"nickname\":\"MockTopFan\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"mock reply\"}],\"confidence\":\"normal\"},{\"nickname\":\"DemoBuyer\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"demo reply\"}],\"confidence\":\"normal\"}]}";
        }
        return "{\"type\":\"report\",\"totalXu\":" + parseTotalXu(upload.getRawText()) + ",\"rawText\":\"" + escapeJson(upload.getRawText()) + "\"}";
    }

    private JsonNode readAiResult(String aiResult)
    {
        if (StringUtils.isEmpty(aiResult))
        {
            throw new ServiceException("请先生成或填写识别结果");
        }
        try
        {
            return OBJECT_MAPPER.readTree(aiResult);
        }
        catch (Exception e)
        {
            throw new ServiceException("识别结果格式不正确");
        }
    }

    private Integer parseTotalXu(String rawText)
    {
        if (StringUtils.isEmpty(rawText))
        {
            return 0;
        }
        String text = rawText.replace(",", "");
        // 优先取 Tổng/总 关键词后面的数字
        Matcher keyword = Pattern.compile("(?i)(?:tổng|tong|总计|总)\\D{0,5}(\\d+)").matcher(text);
        if (keyword.find())
        {
            return toIntSafely(keyword.group(1));
        }
        // 兜底:取文本中最大的数字,避免误取末尾的时长/百分比等
        Matcher matcher = Pattern.compile("(\\d{2,})").matcher(text);
        int max = 0;
        while (matcher.find())
        {
            max = Math.max(max, toIntSafely(matcher.group(1)));
        }
        return max;
    }

    private int toIntSafely(String s)
    {
        try
        {
            long val = Long.parseLong(s);
            return val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private String escapeJson(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
