package com.lp.robot.strategie.impl;

import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.entity.TradeProfit;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.dextools.service.TradeProfitService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.common.MaCalculate;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategySellCompleteEvent;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.MaResultObj;
import com.lp.robot.strategie.StrategyProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    @Autowired
    private TradeProfitService tradeProfitService;

    @Override
    public void execute() {
        // 1. 查询所有处理中订单
        final List<TradeOrder> tradeOrders = tradeOrderService.findProcessing();
        // 2. 判断买单成功
        tradeOrders.stream()
                .filter(tradeOrder -> TradeOrderTypeEnum.BUY.equals(tradeOrder.getTradeOrderType()))
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
                .forEach(tradeOrder -> executor.execute(() -> execute(tradeOrder)));
    }

    private void execute(TradeOrder tradeOrder) {
        applicationContext.publishEvent(new StrategySellCompleteEvent(tradeOrder, order -> {
            // 1. 先查询是否存在卖单
            BigDecimal sellOrderNumber = CacheSingleton.getInstance().get(CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber());
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
                    CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_INCR_MAX_PRICE, tradeOrder.getSymbol());
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
                    final Boolean cancel = gateIoCommon.cancel(tradeOrder.getSymbol(), sellOrderNumber.toPlainString());
                    if (cancel) {
                        // 1. 更新卖单取消
                        tradeOrderService.updateStatusById(TradeOrderStatusEnum.CANCELLED, sellTradeOrder.getId());
                        CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber());
                    }
                }
                return;
            }
            // 不存在判断处理
            //根据MA判断卖出，MA5已经降低，并且当前最新的也开始降低了
            final List<Candlestick2> candlestick =
                    gateIoCommon.candlestick(tradeOrder.getSymbol(), "300", "1").stream()
                            .sorted(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()))
                            .collect(Collectors.toList());
            final MaResultObj ma5 = MaCalculate.execute(candlestick, 300, 5);
            if (ma5.getCurrent().compareTo(ma5.getPrevious()) > 0) {
                return;
            }
            log.info("SellCStrategyImpl md5 current:{} < previous:{}", ma5.getCurrent(), ma5.getPrevious());


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

        }));
    }
}