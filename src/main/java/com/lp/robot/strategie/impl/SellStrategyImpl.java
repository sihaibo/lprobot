package com.lp.robot.strategie.impl;

import com.lp.robot.gate.obj.TickersObj;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategySellCompleteEvent;
import com.lp.robot.strategie.StrategyProvider;
import java.math.BigDecimal;
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
 * @date: 2022-02-22 16:42<br/>
 * @since JDK 1.8
 */
@Slf4j
@Service("sellStrategy")
@Deprecated
public class SellStrategyImpl implements StrategyProvider {

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
        // 2. 买单处理
        final List<TradeOrder> buyTradeOrder = tradeOrders.stream()
                .filter(tradeOrder -> CacheSingleton.KEY_STRATEGY_A.equals(tradeOrder.getStrategy()))
                .filter(tradeOrder -> TradeOrderTypeEnum.BUY.equals(tradeOrder.getTradeOrderType()))
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
                .collect(Collectors.toList());
        buyTradeOrder.forEach(tradeOrder -> executor.execute(() -> buy(tradeOrder)));
        // 3. 卖单处理
        final List<TradeOrder> sellTradeOrder = tradeOrders.stream()
                .filter(tradeOrder -> CacheSingleton.KEY_STRATEGY_A.equals(tradeOrder.getStrategy()))
                .filter(tradeOrder -> TradeOrderTypeEnum.SELL.equals(tradeOrder.getTradeOrderType()))
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
                .collect(Collectors.toList());
        sellTradeOrder.forEach(tradeOrder -> executor.execute(() -> sell(tradeOrder)));
    }

    /**
     * 买单处理
     * @param tradeOrder
     */
    private void buy(TradeOrder tradeOrder) {

        applicationContext.publishEvent(new StrategySellCompleteEvent(tradeOrder, order -> {
            // 挂委托单
            TradeOrder sell = buildTradeOrder(tradeOrder.getSymbol(), order.getTradeNumber(), order.getFilledPrice());
            // 当前价格低于挂单价格，返回
            if (order.getLast().compareTo(sell.getPrice()) < 0) {
                return;
            }
            // 委托单
            final TradeOrder sellTradeOrder = gateIoCommon.sellTriggeredOrder(tradeOrder.getSymbol(), sell.getPrice(), sell.getTradeNumber());
            if (StringUtils.isEmpty(sellTradeOrder.getOrderNumber())) {
                applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(),
                        String.format("挂卖单失败。买单号：%s，描述：%s", tradeOrder.getOrderNumber(), sellTradeOrder.getErrorMsg())));
                sell.setErrorMsg(sellTradeOrder.getErrorMsg());
                sell.setTradeOrderStatus(TradeOrderStatusEnum.CANCELLED);
            } else {
                sell.setOrderNumber(sellTradeOrder.getOrderNumber());
                // 修改买单完成
                TradeOrder update = new TradeOrder();
                update.setTradeOrderStatus(TradeOrderStatusEnum.CLOSED);
                update.setFilledPrice(order.getFilledPrice());
                tradeOrderService.updateById(update, tradeOrder.getId());
            }
            CacheSingleton.getInstance().put(CacheSingleton.KEY_BUY_PRICE, tradeOrder.getSymbol(), order.getFilledPrice());
            tradeOrderService.insert(sell);
        }));
    }

    private TradeOrder buildTradeOrder(String symbol, BigDecimal tradeNumber, BigDecimal price) {
        TradeOrder sell = new TradeOrder();
        sell.setSymbol(symbol);
        sell.setPrice(price.multiply(new BigDecimal("0.985")).setScale(price.scale(), BigDecimal.ROUND_DOWN));
        sell.setTradeNumber(tradeNumber.setScale(3, BigDecimal.ROUND_DOWN));
        sell.setTradeOrderStatus(TradeOrderStatusEnum.OPEN);
        sell.setTradeOrderType(TradeOrderTypeEnum.SELL);
        sell.setTradeOrderVersion(TradeOrderVersion.V4);
        sell.setStrategy(CacheSingleton.KEY_STRATEGY_A);
        return sell;
    }

    /**
     * 卖单处理
     * @param tradeOrder
     */
    private void sell(TradeOrder tradeOrder) {

        // 查询订单是否成功
        final TradeOrder order = TradeOrderVersion.V4.equals(tradeOrder.getTradeOrderVersion())
                ? gateIoCommon.getTriggeredOrder(tradeOrder.getSymbol(), tradeOrder.getOrderNumber())
                : gateIoCommon.getOrder(tradeOrder.getSymbol(), tradeOrder.getOrderNumber());

        if (Objects.isNull(order.getTradeOrderStatus())) {
            applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(), String.format("卖单号：%s，查询失败。", tradeOrder.getOrderNumber())));
            return;
        }
        // 更新订单完成.
        if (TradeOrderStatusEnum.CLOSED.equals(order.getTradeOrderStatus())) {
            tradeOrderService.updateStatusById(TradeOrderStatusEnum.CLOSED, tradeOrder.getId());
            CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_PRICE, tradeOrder.getSymbol());
            return;
        }
        // 手动取消了，还得需要修改订单去重新挂卖单或者直接手动卖
        if (TradeOrderStatusEnum.CANCELLED.equals(order.getTradeOrderStatus())) {
            tradeOrderService.updateStatusById(TradeOrderStatusEnum.CANCELLED, tradeOrder.getId());
            CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_PRICE, tradeOrder.getSymbol());
            return;
        }
        // 1. 获取最新价格
        final TickersObj ticker = gateIoCommon.getTicker(tradeOrder.getSymbol());
        final BigDecimal last = new BigDecimal(ticker.getLast());

        // 追高判断，最新价小于卖出价103%，挂保底单
        if (last.divide(tradeOrder.getPrice(), 2, BigDecimal.ROUND_DOWN).compareTo(new BigDecimal("1.03")) < 0) {
            // 下单金额
            final BigDecimal buyPrice = CacheSingleton.getInstance().get(CacheSingleton.KEY_BUY_PRICE, tradeOrder.getSymbol());
            if (Objects.isNull(buyPrice)) {
                return;
            }
            // 当前价格大于买入价格，并已涨千8，卖订单金额小于买订单金额。挂保本订单(1.007)
            if (last.compareTo(buyPrice) > 0
                    && last.divide(buyPrice, 3, BigDecimal.ROUND_DOWN).compareTo(new BigDecimal("1.008")) > 0
                    && tradeOrder.getPrice().compareTo(buyPrice) < 0) {
                log.info("sell strategy last > buy price. create order. last:{}, buy price:{}, order price:{}",
                        last, buyPrice, tradeOrder.getPrice());
                createOrder(tradeOrder, buyPrice, true);
                CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_PRICE, tradeOrder.getSymbol());
            }

        } else {
            // 最新价比挂单价涨幅超过6%，重新下单卖单
            createOrder(tradeOrder, last, false);
        }
    }

    private void createOrder(TradeOrder tradeOrder, BigDecimal last, boolean reset) {
        final Boolean cancel = TradeOrderVersion.V4.equals(tradeOrder.getTradeOrderVersion())
                ? gateIoCommon.cancelTriggeredOrder(tradeOrder.getSymbol(), tradeOrder.getOrderNumber())
                : gateIoCommon.cancel(tradeOrder.getSymbol(), tradeOrder.getOrderNumber());

        if (!cancel) {
            return;
        }
        tradeOrderService.updateStatusById(TradeOrderStatusEnum.CANCELLED, tradeOrder.getId());
        // 重新下单
        TradeOrder sell = buildTradeOrder(tradeOrder.getSymbol(), tradeOrder.getTradeNumber(), last);
        if (reset) {
            sell.setPrice(last.multiply(new BigDecimal("1.007")));
        }
        final TradeOrder sellTradeOrder = gateIoCommon.sellTriggeredOrder(tradeOrder.getSymbol(), sell.getPrice(), tradeOrder.getTradeNumber());
        if (StringUtils.isEmpty(sellTradeOrder.getOrderNumber())) {
            applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(),
                    String.format("挂卖单失败。买单号：%s，描述：%s", tradeOrder.getOrderNumber(), sellTradeOrder.getErrorMsg())));
            sell.setErrorMsg(sellTradeOrder.getErrorMsg());
            sell.setTradeOrderStatus(TradeOrderStatusEnum.CANCELLED);
        } else {
            sell.setOrderNumber(sellTradeOrder.getOrderNumber());
        }
        tradeOrderService.insert(sell);
    }
}