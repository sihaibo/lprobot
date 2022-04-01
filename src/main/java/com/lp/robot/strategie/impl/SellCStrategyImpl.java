package com.lp.robot.strategie.impl;

import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.common.MaCalculate;
import com.lp.robot.gate.event.StrategySellCompleteEvent;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.MaResultObj;
import com.lp.robot.strategie.StrategyProvider;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-21 10:29<br/>
 * @since JDK 1.8
 */
@Service("sellCStrategy")
@Slf4j
public class SellCStrategyImpl implements StrategyProvider {

    @Autowired
    private TradeOrderService tradeOrderService;
    @Autowired
    private ThreadPoolTaskExecutor executor;
    @Autowired
    private GateIoCommon gateIoCommon;
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void execute() {
        // 1. 查询所有处理中订单
        final List<TradeOrder> tradeOrders = tradeOrderService.findProcessing();
        // 2. 判断买单成功
        tradeOrders.stream()
                .filter(tradeOrder -> TradeOrderTypeEnum.BUY.equals(tradeOrder.getTradeOrderType()))
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
                .filter(tradeOrder -> !tradeOrder.getStrategy().equals(CacheSingleton.KEY_STRATEGY_E))
                .forEach(tradeOrder -> executor.execute(() -> execute(tradeOrder)));
    }

    private void execute(TradeOrder tradeOrder) {
        applicationContext.publishEvent(new StrategySellCompleteEvent(tradeOrder, order -> {

            int groupSec = tradeOrder.getStrategy().equals(CacheSingleton.KEY_STRATEGY_D) ? 60 : 300;
            // 不存在判断处理
            //根据MA判断卖出，MA5已经降低，并且当前最新的也开始降低了
            final List<Candlestick2> candlestick =
                    gateIoCommon.candlestick(tradeOrder.getSymbol(), String.valueOf(groupSec), "1").stream()
                            .sorted(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()))
                            .collect(Collectors.toList());
            final MaResultObj ma5 = MaCalculate.execute(candlestick, groupSec, 5);
            if (ma5.getCurrent().compareTo(ma5.getPrevious()) > 0) {
                return false;
            }
            log.info("SellCStrategyImpl symbol:{} md5 current:{} < previous:{}", tradeOrder.getSymbol(), ma5.getCurrent(), ma5.getPrevious());
            return true;
        }));
    }
}