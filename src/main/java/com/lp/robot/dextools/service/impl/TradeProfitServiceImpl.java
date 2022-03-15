package com.lp.robot.dextools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lp.robot.dextools.dao.TradeProfitDao;
import com.lp.robot.dextools.entity.TradeProfit;
import com.lp.robot.dextools.service.TradeProfitService;
import com.lp.robot.gate.event.TradeProfitCreateEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-10 17:42<br/>
 * @since JDK 1.8
 */
@Service
public class TradeProfitServiceImpl implements TradeProfitService {

    @Autowired
    private TradeProfitDao tradeProfitDao;
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void create(TradeProfit tradeProfit) {
        tradeProfit.setCreateDateTime(LocalDateTime.now());
        tradeProfitDao.insert(tradeProfit);
        applicationContext.publishEvent(new TradeProfitCreateEvent(tradeProfit));
    }

    @Override
    public List<TradeProfit> findBetweenCreateDateTime(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LambdaQueryWrapper<TradeProfit> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(TradeProfit::getCreateDateTime, startDateTime, endDateTime);
        return tradeProfitDao.selectList(wrapper);
    }
}