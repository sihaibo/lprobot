package com.lp.robot.dextools.service;

import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import java.util.List;

/**
 * 功能描述:TradeOrderService <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 15:22<br/>
 * @since JDK 1.8
 */
public interface TradeOrderService {

    void insert(TradeOrder tradeOrder);

    /**
     * 查询处理中的订单
     * @param symbol
     * @return
     */
    long getBySymbolAndProcessing(String symbol);

    /**
     * 查询所有处理中的订单
     * @return
     */
    List<TradeOrder> findProcessing();

    void updateStatusById(TradeOrderStatusEnum tradeOrderStatus, Integer id);

    TradeOrder getByOrderNumber(String orderNumber);

    TradeOrder getById(String id);

    void updateById(TradeOrder tradeOrder, Integer id);
}