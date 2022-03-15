package com.lp.robot.gate.listener;

import com.lp.robot.gate.obj.TickersObj;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategySellCompleteEvent;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-03 09:41<br/>
 * @since JDK 1.8
 */
@Slf4j
@Component
public class StrategySellCompleteListener implements ApplicationListener<StrategySellCompleteEvent> {

    @Autowired
    private GateIoCommon gateIoCommon;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private TradeOrderService tradeOrderService;

    @Async
    @Override
    public void onApplicationEvent(StrategySellCompleteEvent event) {

        TradeOrder tradeOrder = (TradeOrder) event.getSource();

        // 查询订单是否成功
        final TradeOrder order = gateIoCommon.getOrder(tradeOrder.getSymbol(), tradeOrder.getOrderNumber());
        if (Objects.isNull(order.getTradeOrderStatus())) {
            applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(), String.format("买单号：%s，查询失败。", tradeOrder.getOrderNumber())));
            return;
        }
        final TickersObj ticker = gateIoCommon.getTicker(tradeOrder.getSymbol());
        final BigDecimal last = new BigDecimal(ticker.getLast());
        // 挂单中，判断价格和当前价格，相差3%就撤销订单
        if (TradeOrderStatusEnum.OPEN.equals(order.getTradeOrderStatus())) {
            if (last.divide(tradeOrder.getPrice(), 2, BigDecimal.ROUND_HALF_UP).compareTo(new BigDecimal("1.03")) >= 0) {
                log.info("sell strategy last price > order price 1.03 cancel buy order. orderNumber:{}, last:{}, order price:{}",
                        tradeOrder.getOrderNumber(), last, tradeOrder.getPrice());
                final Boolean cancel = gateIoCommon.cancel(tradeOrder.getSymbol(), tradeOrder.getOrderNumber());
                if (cancel) {
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CANCELLED, tradeOrder.getId());
                }
            }
            return;
        }
        if (TradeOrderStatusEnum.CLOSED.equals(order.getTradeOrderStatus())) {
            order.setLast(last);
            event.getConsumer().accept(order);
        }
    }
}