package com.ruoyi.web.controller.live;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.live.domain.LiveStreamer;
import com.ruoyi.live.domain.LiveUpload;
import com.ruoyi.live.service.ILiveStreamerService;
import com.ruoyi.live.service.ILiveUploadService;

/**
 * 直播数据每日上传
 */
@RestController
@RequestMapping("/live/upload")
public class LiveUploadController extends BaseController
{
    @Autowired
    private ILiveUploadService uploadService;

    @Autowired
    private ILiveStreamerService streamerService;

    /**
     * 明细列表
     */
    @PreAuthorize("@ss.hasPermi('live:upload:list')")
    @GetMapping("/list")
    public TableDataInfo list(LiveUpload query)
    {
        restrictToOwnStreamer(query);
        startPage();
        return getDataTable(uploadService.selectLiveUploadList(query));
    }

    /**
     * 按 主播×日期 的完整性汇总
     */
    @PreAuthorize("@ss.hasPermi('live:upload:list')")
    @GetMapping("/daily")
    public TableDataInfo daily(LiveUpload query)
    {
        restrictToOwnStreamer(query);
        startPage();
        return getDataTable(uploadService.selectDailySummary(query));
    }

    /**
     * 批量上传截图(打赏榜/聊天)
     */
    @PreAuthorize("@ss.hasPermi('live:upload:add')")
    @Log(title = "直播数据上传", businessType = BusinessType.INSERT)
    @PostMapping("/img")
    public AjaxResult uploadImg(@RequestParam("bizDate") String bizDate,
                                @RequestParam("streamerId") Long streamerId,
                                @RequestParam("uploadType") String uploadType,
                                @RequestParam("files") MultipartFile[] files) throws Exception
    {
        if (!LiveUpload.TYPE_GIFT.equals(uploadType) && !LiveUpload.TYPE_CHAT.equals(uploadType))
        {
            return error("截图类型不正确");
        }
        if (files == null || files.length == 0)
        {
            return error("请选择要上传的图片");
        }
        Long effectiveStreamerId = checkStreamerForWrite(streamerId);

        // 存储目录:{profile}/live/{日期}/{主播ID}
        String baseDir = RuoYiConfig.getProfile() + "/live/" + bizDate + "/" + effectiveStreamerId;
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files)
        {
            String url = FileUploadUtils.upload(baseDir, file, MimeTypeUtils.IMAGE_EXTENSION);
            LiveUpload record = new LiveUpload();
            record.setBizDate(DateUtils.parseDate(bizDate));
            record.setStreamerId(effectiveStreamerId);
            record.setUploadType(uploadType);
            record.setFilePath(url);
            record.setUploadBy(SecurityUtils.getUserId());
            uploadService.insertLiveUpload(record);
            urls.add(url);
        }
        AjaxResult ajax = AjaxResult.success("成功上传 " + urls.size() + " 张");
        ajax.put("urls", urls);
        return ajax;
    }

    /**
     * 提交工作汇报文本
     */
    @PreAuthorize("@ss.hasPermi('live:upload:add')")
    @Log(title = "工作汇报提交", businessType = BusinessType.INSERT)
    @PostMapping("/report")
    public AjaxResult uploadReport(@RequestBody LiveUpload upload)
    {
        if (upload.getBizDate() == null || StringUtils.isEmpty(upload.getRawText()))
        {
            return error("日期和汇报内容不能为空");
        }
        Long effectiveStreamerId = checkStreamerForWrite(upload.getStreamerId());
        LiveUpload record = new LiveUpload();
        record.setBizDate(upload.getBizDate());
        record.setStreamerId(effectiveStreamerId);
        record.setUploadType(LiveUpload.TYPE_REPORT);
        record.setRawText(upload.getRawText());
        record.setUploadBy(SecurityUtils.getUserId());
        uploadService.insertLiveUpload(record);
        return success();
    }

    /**
     * 删除上传记录(含磁盘文件)
     */
    @PreAuthorize("@ss.hasPermi('live:upload:remove')")
    @Log(title = "直播数据上传", businessType = BusinessType.DELETE)
    @DeleteMapping("/{uploadIds}")
    public AjaxResult remove(@PathVariable Long[] uploadIds)
    {
        return toAjax(uploadService.deleteLiveUploadByIds(uploadIds));
    }

    /**
     * 查询限制:纯主播账号只能看自己的数据
     */
    private void restrictToOwnStreamer(LiveUpload query)
    {
        LiveStreamer own = getOwnStreamerIfRestricted();
        if (own != null)
        {
            query.setStreamerId(own.getStreamerId());
        }
    }

    /**
     * 写入校验:纯主播账号只能以自己身份上传;运营/管理员可代传
     */
    private Long checkStreamerForWrite(Long streamerId)
    {
        LiveStreamer own = getOwnStreamerIfRestricted();
        if (own != null)
        {
            return own.getStreamerId();
        }
        if (streamerId == null || streamerService.selectLiveStreamerById(streamerId) == null)
        {
            throw new ServiceException("请选择主播");
        }
        return streamerId;
    }

    /**
     * 若当前用户是"仅主播"角色,返回其绑定的主播记录;否则返回 null(不受限)
     */
    private LiveStreamer getOwnStreamerIfRestricted()
    {
        SysUser user = SecurityUtils.getLoginUser().getUser();
        if (user.isAdmin())
        {
            return null;
        }
        boolean hasElevated = false;
        boolean isStreamer = false;
        for (SysRole role : user.getRoles())
        {
            String key = role.getRoleKey();
            if ("operator".equals(key) || "live_admin".equals(key))
            {
                hasElevated = true;
            }
            if ("streamer".equals(key))
            {
                isStreamer = true;
            }
        }
        if (hasElevated || !isStreamer)
        {
            return null;
        }
        LiveStreamer own = streamerService.selectLiveStreamerByUserId(user.getUserId());
        if (own == null)
        {
            throw new ServiceException("当前账号未绑定主播信息,请联系管理员");
        }
        return own;
    }
}
