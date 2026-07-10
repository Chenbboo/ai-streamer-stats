package com.ruoyi.web.controller.live;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.live.domain.LiveCustomer;
import com.ruoyi.live.service.ILiveCustomerService;

@RestController
@RequestMapping("/live/customer")
public class LiveCustomerController extends BaseController
{
    @Autowired
    private ILiveCustomerService customerService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('live:review:list')")
    public TableDataInfo list(LiveCustomer customer)
    {
        startPage();
        List<LiveCustomer> list = customerService.selectCustomerList(customer);
        return getDataTable(list);
    }

    @PostMapping("/merge")
    @PreAuthorize("@ss.hasPermi('live:review:edit')")
    public AjaxResult merge(@RequestParam("primaryId") Long primaryId,
                            @RequestParam("secondaryId") Long secondaryId)
    {
        customerService.mergeCustomers(primaryId, secondaryId);
        return success("合并成功");
    }
}
