package com.ruoyi.live.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.live.domain.LiveCustomer;
import com.ruoyi.live.mapper.LiveCustomerMapper;
import com.ruoyi.live.service.ILiveCustomerService;

@Service
public class LiveCustomerServiceImpl implements ILiveCustomerService
{
    @Autowired
    private LiveCustomerMapper customerMapper;

    @Override
    public List<LiveCustomer> selectCustomerList(LiveCustomer customer)
    {
        return customerMapper.selectCustomerList(customer);
    }

    @Override
    public LiveCustomer selectCustomerById(Long customerId)
    {
        return customerMapper.selectCustomerById(customerId);
    }

    @Override
    @Transactional
    public void mergeCustomers(Long primaryId, Long secondaryId)
    {
        if (primaryId.equals(secondaryId))
        {
            throw new ServiceException("不能合并同一个客户");
        }
        LiveCustomer primary = customerMapper.selectCustomerById(primaryId);
        LiveCustomer secondary = customerMapper.selectCustomerById(secondaryId);
        if (primary == null || secondary == null)
        {
            throw new ServiceException("客户不存在");
        }
        if (primary.getMergedIntoId() != null || secondary.getMergedIntoId() != null)
        {
            throw new ServiceException("已合并的客户不能再次参与合并");
        }
        if (primary.getStreamerId() == null || !primary.getStreamerId().equals(secondary.getStreamerId()))
        {
            throw new ServiceException("只能合并同一主播名下的客户");
        }

        customerMapper.mergeGiftRecords(secondaryId, primaryId);
        customerMapper.deleteGiftRecords(secondaryId);
        customerMapper.mergeChatContacts(secondaryId, primaryId);
        customerMapper.deleteChatContacts(secondaryId);
        customerMapper.mergeFollowRecords(secondaryId, primaryId);
        customerMapper.deleteFollowRecords(secondaryId);
        customerMapper.updateChatMessageCustomerId(secondaryId, primaryId);
        customerMapper.moveAliases(secondaryId, primaryId, primary.getStreamerId());
        customerMapper.insertAlias(primaryId, primary.getStreamerId(), secondary.getNickname(), "merge",
            secondary.getFirstSeenDate(), secondary.getLastSeenDate());
        customerMapper.mergeCustomerMetadata(primaryId, secondaryId);
        customerMapper.markCustomerMerged(secondaryId, primaryId);
    }
}
