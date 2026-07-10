package com.ruoyi.web.controller.live;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.live.domain.LiveStreamer;
import com.ruoyi.live.service.ILiveStreamerService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 主播信息
 */
@RestController
@RequestMapping("/live/streamer")
public class LiveStreamerController extends BaseController
{
    @Autowired
    private ILiveStreamerService streamerService;

    @Autowired
    private ISysUserService userService;

    /**
     * 主播列表（分页）
     */
    @PreAuthorize("@ss.hasPermi('live:streamer:list')")
    @GetMapping("/list")
    public TableDataInfo list(LiveStreamer streamer)
    {
        startPage();
        List<LiveStreamer> list = streamerService.selectLiveStreamerList(streamer);
        return getDataTable(list);
    }

    /**
     * 在职主播下拉列表（主播角色只返回自己）
     */
    @PreAuthorize("@ss.hasPermi('live:upload:list')")
    @GetMapping("/listAll")
    public AjaxResult listAll()
    {
        LiveStreamer own = getOwnStreamerIfRestricted();
        LiveStreamer query = new LiveStreamer();
        query.setStatus("0");
        if (own != null)
        {
            query.setStreamerId(own.getStreamerId());
        }
        List<LiveStreamer> list = streamerService.selectLiveStreamerList(query);
        return success(list);
    }

    private LiveStreamer getOwnStreamerIfRestricted()
    {
        com.ruoyi.common.core.domain.entity.SysUser user = com.ruoyi.common.utils.SecurityUtils.getLoginUser().getUser();
        if (user.isAdmin())
        {
            return null;
        }
        boolean hasElevated = false;
        boolean isStreamer = false;
        for (com.ruoyi.common.core.domain.entity.SysRole role : user.getRoles())
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
        return own;
    }

    /**
     * 获取主播详情
     */
    @PreAuthorize("@ss.hasPermi('live:streamer:list')")
    @GetMapping("/{streamerId}")
    public AjaxResult getInfo(@PathVariable Long streamerId)
    {
        return success(streamerService.selectLiveStreamerById(streamerId));
    }

    /**
     * 新增主播（自动创建登录账号+绑定主播角色）
     * 前端传: stageName, tiktokHandle, password, remark
     */
    @PreAuthorize("@ss.hasPermi('live:streamer:add')")
    @PostMapping
    @Transactional
    public AjaxResult add(@RequestBody java.util.Map<String, String> body)
    {
        String stageName = body.get("stageName");
        String tiktokHandle = body.get("tiktokHandle");
        String password = body.get("password");
        String remark = body.get("remark");

        if (stageName == null || stageName.isEmpty())
        {
            return error("主播名称不能为空");
        }
        if (password == null || password.length() < 6)
        {
            return error("密码至少6位");
        }

        // 1. 创建系统用户
        com.ruoyi.common.core.domain.entity.SysUser user = new com.ruoyi.common.core.domain.entity.SysUser();
        String userName = (tiktokHandle != null && !tiktokHandle.isEmpty()) ? tiktokHandle.replace("@", "") : stageName.replaceAll("\\s+", "").toLowerCase();
        if (userName.isEmpty())
        {
            userName = "streamer_" + System.currentTimeMillis() % 100000;
        }
        user.setUserName(userName);
        user.setNickName(stageName);
        user.setPassword(SecurityUtils.encryptPassword(password));
        user.setStatus("0");
        user.setCreateBy(SecurityUtils.getUsername());

        // 插入用户
        int userResult = userService.insertUser(user);
        if (userResult == 0)
        {
            return error("创建用户失败，用户名可能已存在");
        }

        // 2. 绑定主播角色 (role_id=3)
        Long userId = user.getUserId();
        userService.insertUserAuth(userId, new Long[]{3L});

        // 3. 创建主播记录
        LiveStreamer streamer = new LiveStreamer();
        streamer.setUserId(userId);
        streamer.setStageName(stageName);
        streamer.setTiktokHandle(tiktokHandle);
        streamer.setStatus("0");
        streamer.setRemark(remark);
        streamer.setCreateBy(SecurityUtils.getUsername());

        int streamerResult = streamerService.insertLiveStreamer(streamer);
        if (streamerResult == 0)
        {
            return error("创建主播记录失败");
        }

        return success("主播创建成功，登录账号: " + userName);
    }

    /**
     * 修改主播
     */
    @PreAuthorize("@ss.hasPermi('live:streamer:edit')")
    @PutMapping
    public AjaxResult edit(@RequestBody LiveStreamer streamer)
    {
        return toAjax(streamerService.updateLiveStreamer(streamer));
    }

    /**
     * 删除主播（软删除，status改为1）
     */
    @PreAuthorize("@ss.hasPermi('live:streamer:remove')")
    @DeleteMapping("/{streamerIds}")
    public AjaxResult remove(@PathVariable Long[] streamerIds)
    {
        return toAjax(streamerService.deleteLiveStreamerByIds(streamerIds));
    }
}
