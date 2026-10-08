package com.ruoyi.web.controller.business;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.business.domain.*;
import com.ruoyi.business.service.impl.BusinessBonusDistributionService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.exception.ServiceException;

@RestController
@RequestMapping("/business/incentive/distribution")
public class BusinessBonusDistributionController extends BaseController
{
    @Autowired private BusinessBonusDistributionService service;
    @PreAuthorize("@ss.hasPermi('business:incentive:list')")
    @GetMapping("/workspace")
    public AjaxResult workspace(@RequestParam(required=false) Long projectId)
    {return success(service.workspace(projectId,SecurityUtils.getUserId(),SecurityUtils.isAdmin(),SecurityUtils.hasPermi("business:incentive:pay")&&SecurityUtils.hasRole("bonus_payment_finance")));}
    @PreAuthorize("@ss.hasPermi('business:incentive:apply')")
    @Log(title="保存个人奖金分配",businessType=BusinessType.UPDATE)
    @PostMapping("/allocation")
    public AjaxResult save(@RequestBody BusinessBonusAllocation input)
    {return success(service.save(input,SecurityUtils.getUserId(),SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('business:incentive:apply')")
    @Log(title="提交或撤销奖金分配",businessType=BusinessType.UPDATE)
    @PostMapping("/allocation/{id}/submit")
    public AjaxResult submit(@PathVariable Long id,@RequestBody Map<String,Object> body)
    {String action=String.valueOf(body.get("action"));if(!"SUBMITTED".equals(action)&&!"CANCELED".equals(action))throw new ServiceException("操作不正确");return transition(id,body,action);}
    @PreAuthorize("@ss.hasPermi('business:incentive:approve')")
    @Log(title="核准个人奖金分配",businessType=BusinessType.UPDATE)
    @PostMapping("/allocation/{id}/review")
    public AjaxResult review(@PathVariable Long id,@RequestBody Map<String,Object> body)
    {String action=String.valueOf(body.get("action"));if(!"APPROVED".equals(action)&&!"RETURNED".equals(action))throw new ServiceException("操作不正确");return transition(id,body,action);}
    @PreAuthorize("@ss.hasPermi('business:incentive:pay')")
    @Log(title="登记个人奖金实付",businessType=BusinessType.INSERT)
    @PostMapping("/payment")
    public AjaxResult payment(@RequestBody BusinessBonusPayment input)
    {return success(service.pay(input,SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.hasRole("bonus_payment_finance")));}
    private AjaxResult transition(Long id,Map<String,Object> body,String action)
    {Integer version;try{version=Integer.valueOf(String.valueOf(body.get("version")));}catch(RuntimeException e){throw new ServiceException("版本号不正确");}
        return success(service.transition(id,version,action,body.get("reason")==null?null:String.valueOf(body.get("reason")),SecurityUtils.getUserId(),SecurityUtils.getUsername()));}
}
