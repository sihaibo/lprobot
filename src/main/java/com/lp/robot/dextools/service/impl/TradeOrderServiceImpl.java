package com.lp.robot.dextools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lp.robot.dextools.dao.TradeOrderDao;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.event.OrderCompleteEvent;
import com.lp.robot.gate.event.OrderCreateEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 15:22<br/>
 * @since JDK 1.8
 */
@Service
public class TradeOrderServiceImpl implements TradeOrderService {

    @Autowired
    private TradeOrderDao tradeOrderDao;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void insert(TradeOrder tradeOrder) {
        tradeOrder.setCreateDateTime(LocalDateTime.now());
        tradeOrderDao.insert(tradeOrder);
        applicationContext.publishEvent(new OrderCreateEvent(tradeOrder));
    }

    @Override
    public long getBySymbolAndProcessing(String symbol) {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeOrder::getSymbol, symbol);
        wrapper.eq(TradeOrder::getTradeOrderStatus, TradeOrderStatusEnum.OPEN);
        return tradeOrderDao.selectCount(wrapper);
    }

    @Override
    public List<TradeOrder> findProcessing() {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeOrder::getTradeOrderStatus, TradeOrderStatusEnum.OPEN);
        return tradeOrderDao.selectList(wrapper);
    }

    @Override
    public void updateStatusById(TradeOrderStatusEnum tradeOrderStatus, Integer id) {
        TradeOrder tradeOrder = new TradeOrder();
        tradeOrder.setTradeOrderStatus(tradeOrderStatus);
        tradeOrder.setId(id);
        tradeOrderDao.updateById(tradeOrder);
        if (TradeOrderStatusEnum.CLOSED.equals(tradeOrderStatus)) {
            applicationContext.publishEvent(new OrderCompleteEvent(id));
        }
    }

    @Override
    public TradeOrder getByOrderNumber(String orderNumber) {
        LambdaQueryWrapper<TradeOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TradeOrder::getOrderNumber, orderNumber);
        return tradeOrderDao.selectOne(wrapper);
    }

    @Override
    public TradeOrder getById(String id) {
        return tradeOrderDao.selectById(id);
    }

    @Override
    public void updateById(TradeOrder tradeOrder, Integer id) {
        LambdaUpdateWrapper<TradeOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TradeOrder::getId, id);
        tradeOrderDao.update(tradeOrder, wrapper);
    }
}