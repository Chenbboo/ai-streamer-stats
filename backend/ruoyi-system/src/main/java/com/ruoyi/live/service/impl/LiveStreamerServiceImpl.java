package com.ruoyi.live.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.live.domain.LiveStreamer;
import com.ruoyi.live.mapper.LiveStreamerMapper;
import com.ruoyi.live.service.ILiveStreamerService;

/**
 * 主播 服务实现
 */
@Service
public class LiveStreamerServiceImpl implements ILiveStreamerService
{
    @Autowired
    private LiveStreamerMapper streamerMapper;

    @Override
    public List<LiveStreamer> selectLiveStreamerList(LiveStreamer streamer)
    {
        return streamerMapper.selectLiveStreamerList(streamer);
    }

    @Override
    public LiveStreamer selectLiveStreamerById(Long streamerId)
    {
        return streamerMapper.selectLiveStreamerById(streamerId);
    }

    @Override
    public LiveStreamer selectLiveStreamerByUserId(Long userId)
    {
        return streamerMapper.selectLiveStreamerByUserId(userId);
    }

    @Override
    public int insertLiveStreamer(LiveStreamer streamer)
    {
        return streamerMapper.insertLiveStreamer(streamer);
    }

    @Override
    public int updateLiveStreamer(LiveStreamer streamer)
    {
        return streamerMapper.updateLiveStreamer(streamer);
    }

    @Override
    public int deleteLiveStreamerByIds(Long[] streamerIds)
    {
        return streamerMapper.deleteLiveStreamerByIds(streamerIds);
    }
}
