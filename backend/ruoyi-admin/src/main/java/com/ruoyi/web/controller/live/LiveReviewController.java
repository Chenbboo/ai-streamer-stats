package com.ruoyi.web.controller.live;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.live.domain.LiveUpload;
import com.ruoyi.live.service.ILiveUploadService;

/**
 * AI 识别校正
 */
@RestController
@RequestMapping("/live/review")
public class LiveReviewController extends BaseController
{
    @Autowired
    private ILiveUploadService uploadService;

    /**
     * 待校正/已识别列表
     */
    @PreAuthorize("@ss.hasPermi('live:review:list')")
    @GetMapping("/list")
    public TableDataInfo list(LiveUpload query)
    {
        startPage();
        return getDataTable(uploadService.selectLiveUploadList(query));
    }

    /**
     * 模拟 AI 识别
     */
    @PreAuthorize("@ss.hasPermi('live:review:edit')")
    @Log(title = "模拟AI识别", businessType = BusinessType.UPDATE)
    @PostMapping("/mock/{uploadId}")
    public AjaxResult mock(@PathVariable Long uploadId)
    {
        return toAjax(uploadService.mockRecognize(uploadId));
    }

    @PreAuthorize("@ss.hasPermi('live:review:edit')")
    @Log(title = "AI识别", businessType = BusinessType.UPDATE)
    @PostMapping("/recognize/{uploadId}")
    public AjaxResult recognize(@PathVariable Long uploadId)
    {
        return toAjax(uploadService.recognizeUpload(uploadId));
    }

    @PreAuthorize("@ss.hasPermi('live:review:edit')")
    @Log(title = "AI识别结果校正", businessType = BusinessType.UPDATE)
    @PutMapping("/result/{uploadId}")
    public AjaxResult saveResult(@PathVariable Long uploadId, @RequestBody Map<String, String> body)
    {
        return toAjax(uploadService.saveRecognizeResult(uploadId, body.get("aiResult")));
    }

    /**
     * 确认入库
     */
    @PreAuthorize("@ss.hasPermi('live:review:confirm')")
    @Log(title = "识别结果确认入库", businessType = BusinessType.UPDATE)
    @PostMapping("/confirm/{uploadId}")
    public AjaxResult confirm(@PathVariable Long uploadId)
    {
        return toAjax(uploadService.confirmRecognize(uploadId));
    }
}
