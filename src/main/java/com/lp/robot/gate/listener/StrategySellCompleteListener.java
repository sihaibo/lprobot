package com.lp.robot.gate.listener;

import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.entity.TradeProfit;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.dextools.service.TradeProfitService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategySellCompleteEvent;
import com.lp.robot.gate.obj.TickersObj;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    @Autowired
    private TradeProfitService tradeProfitService;

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
            if (last.divide(tradeOrder.getPrice(), 2, BigDecimal.ROUND_HALF_UP).compareTo(new BigDecimal("1.02")) >= 0
                    || tradeOrder.getCreateDateTime().plusMinutes(15).compareTo(LocalDateTime.now()) < 0) {
                log.info("sell strategy last price > order price 1.02 cancel buy order. orderNumber:{}, last:{}, order price:{}",
                        tradeOrder.getOrderNumber(), last, tradeOrder.getPrice());
                final Boolean cancel = gateIoCommon.cancel(tradeOrder.getSymbol(), tradeOrder.getOrderNumber());
                if (cancel) {
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CANCELLED, tradeOrder.getId());
                }
            }
            return;
        }
        if (TradeOrderStatusEnum.CLOSED.equals(order.getTradeOrderStatus())) {
            // 1. 先查询是否存在卖单
            BigDecimal sellOrderNumber = CacheSingleton
                    .getInstance().get(CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber());
            if (Objects.nonNull(sellOrderNumber)){
                final TradeOrder sell = gateIoCommon.getOrder(tradeOrder.getSymbol(), sellOrderNumber.toPlainString());
                if (Objects.isNull(sell.getTradeOrderStatus())) {
                    applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(), String.format("卖单号：%s，查询失败。", sellOrderNumber.toPlainString())));
                    return;
                }
                final TradeOrder sellTradeOrder = tradeOrderService.getByOrderNumber(sellOrderNumber.toPlainString());
                // 卖单成功，更新订单状态
                if (TradeOrderStatusEnum.CLOSED.equals(sell.getTradeOrderStatus())) {
                    // 1. 更新买单成功
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CLOSED, tradeOrder.getId());
                    // 2. 更新卖单成功
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CLOSED, sellTradeOrder.getId());
                    CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber());

                    // 3. 计算利润
                    TradeProfit tradeProfit = new TradeProfit();
                    tradeProfit.setBuyNumber(order.getTradeNumber());
                    tradeProfit.setBuyPrice(order.getFilledPrice());
                    tradeProfit.setSellNumber(sell.getTradeNumber());
                    tradeProfit.setSellPrice(sell.getFilledPrice());
                    tradeProfit.setProfit(sell.getToU().subtract(order.getToU()));
                    tradeProfit.setSymbol(tradeOrder.getSymbol());
                    tradeProfit.setStrategy(tradeOrder.getStrategy());
                    tradeProfitService.create(tradeProfit);
                } else if (TradeOrderStatusEnum.CANCELLED.equals(sell.getTradeOrderStatus())) {
                    // 通过交易所手动取消订单了
                    // 1. 更新卖单取消
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CANCELLED, sellTradeOrder.getId());
                    CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber());
                } else if (TradeOrderStatusEnum.OPEN.equals(sell.getTradeOrderStatus())) {
                    // 1. 卖单挂了不到3分钟，可以不卖
                    if (sellTradeOrder.getCreateDateTime().plusMinutes(3).compareTo(LocalDateTime.now()) > 0) {
                        return;
                    }
                    // 判断下，数量是否还够，交易所可能挂单卖出去一部分了。
                    final BigDecimal balances = gateIoCommon.balancesForLocked(tradeOrder.getSymbol().replace("_usdt", ""));
                    // 资金足够的时候 才可以取消订单
                    if (balances.compareTo(order.getTradeNumber()) >= 0) {
                        gateIoCommon.cancel(tradeOrder.getSymbol(), sellOrderNumber.toPlainString());
                    }
                }
                return;
            }
            order.setLast(last);
            if (!event.getFunction().apply(order)) {
                return;
            }
            final BigDecimal orderBookPrice = gateIoCommon.orderBook(tradeOrder.getSymbol(), false,
                    order.getTradeNumber().setScale(4, BigDecimal.ROUND_DOWN));
            // 卖出
            TradeOrder sell = new TradeOrder();
            sell.setSymbol(tradeOrder.getSymbol());
            sell.setPrice(orderBookPrice.compareTo(BigDecimal.ZERO) == 0 ? order.getLast() : orderBookPrice);
            sell.setTradeNumber(order.getTradeNumber().setScale(4, BigDecimal.ROUND_DOWN));
            sell.setTradeOrderStatus(TradeOrderStatusEnum.OPEN);
            sell.setTradeOrderType(TradeOrderTypeEnum.SELL);
            sell.setTradeOrderVersion(TradeOrderVersion.V2);
            sell.setStrategy(CacheSingleton.KEY_STRATEGY_B);
            final TradeOrder result = gateIoCommon.sell(tradeOrder.getSymbol(), sell.getPrice(), sell.getTradeNumber());
            if (StringUtils.isEmpty(result.getOrderNumber())) {
                applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(),
                        String.format("挂卖单失败。买单号：%s，描述：%s", tradeOrder.getOrderNumber(), result.getErrorMsg())));
                sell.setErrorMsg(result.getErrorMsg());
                sell.setTradeOrderStatus(TradeOrderStatusEnum.CANCELLED);
            } else {
                sell.setOrderNumber(result.getOrderNumber());
                CacheSingleton.getInstance().put(
                        CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber(), new BigDecimal(result.getOrderNumber()));

            }
            tradeOrderService.insert(sell);
        }
    }
}