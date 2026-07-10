package com.ruoyi.live.service.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.live.domain.LiveUpload;
import com.ruoyi.live.service.ILiveRecognitionService;

@Service
public class MockLiveRecognitionServiceImpl implements ILiveRecognitionService
{
    @Override
    public String recognize(LiveUpload upload)
    {
        if (LiveUpload.TYPE_GIFT.equals(upload.getUploadType()))
        {
            return "{\"type\":\"gift\",\"provider\":\"mock\",\"items\":[{\"rankNo\":1,\"nickname\":\"MockTopFan\",\"xu\":8888,\"badge\":\"MWRM\",\"confidence\":\"normal\"},{\"rankNo\":2,\"nickname\":\"DemoBuyer\",\"xu\":6666,\"badge\":\"\",\"confidence\":\"normal\"}]}";
        }
        if (LiveUpload.TYPE_CHAT.equals(upload.getUploadType()))
        {
            return "{\"type\":\"chat\",\"provider\":\"mock\",\"items\":[{\"nickname\":\"MockTopFan\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"mock reply\"}],\"confidence\":\"normal\"},{\"nickname\":\"DemoBuyer\",\"messages\":[{\"sender\":\"customer\",\"messageType\":\"text\",\"content\":\"demo reply\"}],\"confidence\":\"normal\"}]}";
        }
        return "{\"type\":\"report\",\"provider\":\"mock\",\"totalXu\":" + parseTotalXu(upload.getRawText()) + ",\"rawText\":\"" + escapeJson(upload.getRawText()) + "\"}";
    }

    private Integer parseTotalXu(String rawText)
    {
        if (StringUtils.isEmpty(rawText))
        {
            return 0;
        }
        Matcher matcher = Pattern.compile("(\\d{2,})").matcher(rawText.replace(",", ""));
        Integer total = 0;
        while (matcher.find())
        {
            try
            {
                long val = Long.parseLong(matcher.group(1));
                total = val > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
            }
            catch (NumberFormatException e) { }
        }
        return total;
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
