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
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-02 16:04<br/>
 * @since JDK 1.8
 */
@Service("sellIncrStrategy")
@Slf4j
public class SellIncrStrategyImpl implements StrategyProvider {

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
                .filter(tradeOrder -> tradeOrder.getStrategy().equals(CacheSingleton.KEY_STRATEGY_E))
                .forEach(tradeOrder -> executor.execute(() -> execute(tradeOrder)));
    }

    private void execute(TradeOrder tradeOrder) {
        // 查询买入以来最大价格
        final BigDecimal max = gateIoCommon.candlestick(tradeOrder.getSymbol(), "60", "3").stream()
                .filter(candlestick2 -> candlestick2.getTime() > tradeOrder.getCreateDateTime().toInstant(ZoneOffset.of("+8")).toEpochMilli())
                .map(Candlestick2::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal price = CacheSingleton.getInstance().get(CacheSingleton.KEY_BUY_INCR_MAX_PRICE, tradeOrder.getSymbol());
        // 初始化或更新缓存数据
        if (Objects.isNull(price) || max.compareTo(price) > 0) {
            price = max;
            CacheSingleton.getInstance().put(CacheSingleton.KEY_BUY_INCR_MAX_PRICE, tradeOrder.getSymbol(), max);
        }
        log.info("sell incr max price:{}, symbol:{}", price, tradeOrder.getSymbol());
        final BigDecimal finalPrice = price;
        applicationContext.publishEvent(new StrategySellCompleteEvent(tradeOrder, order -> {

            // 最新价>买入价
            if (order.getLast().compareTo(order.getFilledPrice()) > 0) {
                // 1. 买入以来的利润降低10%就可以抛出
                // 买入以来的利润 = 最高价 - 买入价
                final BigDecimal profit = finalPrice.subtract(order.getFilledPrice());
                // 卖出价 = 买入价 + 利润保留85%
                final BigDecimal sellPrice = order.getFilledPrice()
                        .add(profit.multiply(new BigDecimal("0.85")).setScale(profit.scale(), BigDecimal.ROUND_DOWN));
                log.info("sell incr last > buy price. symbol:{} sell price:{}, last:{}, buy price:{}",
                        tradeOrder.getSymbol(), sellPrice, order.getLast(), order.getFilledPrice());
                // 当前价格比卖出价大，可以继续保留
                if (order.getLast().compareTo(sellPrice) > 0) {
                    return false;
                }
                // 判断最近5分钟K线
                // 查询一小时内5分钟K线
                // 2. 查询5分钟MA5线。没有下跌可以继续保留
                final List<Candlestick2> candlestick = gateIoCommon.candlestick(tradeOrder.getSymbol(), "300", "1");
                final MaResultObj maResult = MaCalculate.execute(candlestick, 300, 5);

                BigDecimal cache = CacheSingleton.getInstance().get(CacheSingleton.KEY_MA_SELL, maResult.getKey());
                log.info("sell incr ma5 eq. symbol:{} current:{}, previous:{}, cache:{}",
                        tradeOrder.getSymbol(), maResult.getCurrent(), maResult.getPrevious(), cache);
                if (maResult.getCurrent().compareTo(maResult.getPrevious()) >= 0) {
                    // MA回涨，删除已经缓存的MA
                    CacheSingleton.getInstance().remove(CacheSingleton.KEY_MA_SELL, maResult.getKey());
                    // 买入利润低于1.01的话，先保留继续等等奇迹
                    if (order.getLast().divide(order.getFilledPrice(), 2, BigDecimal.ROUND_DOWN).compareTo(new BigDecimal("1.01")) < 0) {
                        return false;
                    }
                } else {
                    // MA跌落，缓存不存在就保存一下
                    if (Objects.isNull(cache)) {
                        cache = maResult.getPrevious();
                        CacheSingleton.getInstance().put(CacheSingleton.KEY_MA_SELL, maResult.getKey(), cache);
                    }
                    // 缓存值和当前MA值大于千一，直接卖出
                    if (cache.divide(maResult.getCurrent(), 3, BigDecimal.ROUND_DOWN).compareTo(new BigDecimal("1.002")) < 0) {
                        return false;
                    }
                }
            } else {
                // 亏损百分比
                final BigDecimal percentage = order.getFilledPrice().divide(order.getLast(), 2, BigDecimal.ROUND_DOWN);
                // 最新价比买入价小，买入价没有跌2%，可以不买
                log.info("sell incr last < buy price. last:{}, buy price:{}, percentage:{}, symbol:{}",
                        order.getLast(), order.getFilledPrice(), percentage, tradeOrder.getSymbol());
                if (percentage.compareTo(new BigDecimal("1.005")) < 0) {
                    return false;
                }
            }
            // 要卖出了，删除下缓存
            CacheSingleton.getInstance().remove(CacheSingleton.KEY_MA_SELL, "5_300");
            CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_INCR_MAX_PRICE, tradeOrder.getSymbol());
            return true;
        }));
    }
}