package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoyi.business.service.BusinessFileService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

/** 公司经营模块统一附件接口。 */
@RestController
@RequestMapping("/business/file")
public class BusinessFileController
{
    @Autowired
    private BusinessFileService businessFileService;

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public AjaxResult upload(MultipartFile file, @RequestParam Long projectId)
    {
        try
        {
            Long userId = SecurityUtils.getUserId();
            Map<String, Object> uploaded = businessFileService.upload(file, projectId, userId,
                SecurityUtils.hasRole("company_owner"), SecurityUtils.isAdmin(userId));
            AjaxResult result = AjaxResult.success("上传成功");
            result.putAll(uploaded);
            return result;
        }
        catch (Exception e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }
}
