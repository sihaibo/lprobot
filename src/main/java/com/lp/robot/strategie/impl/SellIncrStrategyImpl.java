package com.lp.robot.strategie.impl;

import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.entity.TradeProfit;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.dextools.service.TradeProfitService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.common.MdCalculate;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategySellCompleteEvent;
import com.lp.robot.gate.obj.MdResultObj;
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
import org.springframework.util.StringUtils;

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
    @Autowired
    private TradeProfitService tradeProfitService;

    @Override
    public void execute() {
        // 1. 查询所有处理中订单
        final List<TradeOrder> tradeOrders = tradeOrderService.findProcessing();
        // 2. 判断买单成功
        tradeOrders.stream()
//                .filter(tradeOrder -> "INCR".equals(tradeOrder.getStrategy()))
                .filter(tradeOrder -> TradeOrderTypeEnum.BUY.equals(tradeOrder.getTradeOrderType()))
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
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
        log.info("sell incr max price:{}, max:{},symbol:{}", price, max, tradeOrder.getSymbol());
        final BigDecimal finalPrice = price;
        applicationContext.publishEvent(new StrategySellCompleteEvent(tradeOrder, order -> {
            // 1. 先查询是否存在卖单
            BigDecimal sellOrderNumber =
                    CacheSingleton.getInstance().get(CacheSingleton.KEY_BUY_INCR_ORDER_NUMBER, tradeOrder.getOrderNumber());
            // 存在卖单，查询卖单状态。
            if (Objects.nonNull(sellOrderNumber)) {
                final TradeOrder sell = gateIoCommon.getOrder(tradeOrder.getSymbol(), sellOrderNumber.toPlainString());
                if (Objects.isNull(sell.getTradeOrderStatus())) {
                    applicationContext.publishEvent(new ErrorEvent(tradeOrder.getSymbol(), String.format("卖单号：%s，查询失败。", sellOrderNumber.toPlainString())));
                    return;
                }
                // 卖单成功，更新订单状态
                if (TradeOrderStatusEnum.CLOSED.equals(sell.getTradeOrderStatus())) {
                    // 1. 更新买单成功
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CLOSED, tradeOrder.getId());
                    // 2. 更新卖单成功
                    final Integer id = tradeOrderService.getByOrderNumber(sellOrderNumber.toPlainString()).getId();
                    tradeOrderService.updateStatusById(TradeOrderStatusEnum.CLOSED, id);
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
                }
                // TODO: 2022/3/15 判断下，一直没有成功说明被瀑布击穿了，当前卖单如果是盈利单就不处理了。非盈利的话判断当前在回调的话就取消卖单
                return;
            }
            // 最新价>买入价
            if (order.getLast().compareTo(order.getFilledPrice()) > 0) {
                // 1. 买入以来的利润降低10%就可以抛出
                // 买入以来的利润 = 最高价 - 买入价
                final BigDecimal profit = finalPrice.subtract(order.getFilledPrice());
                // 卖出价 = 买入价 + 利润保留80%
                final BigDecimal sellPrice = order.getFilledPrice()
                        .add(profit.multiply(new BigDecimal("0.8")).setScale(profit.scale(), BigDecimal.ROUND_DOWN));
                log.info("sell incr last > buy price. sell price:{}, last:{}, buy price:{}, symbol:{}",
                        sellPrice, order.getLast(), order.getFilledPrice(), tradeOrder.getSymbol());
                // 当前价格比卖出价大，可以继续保留
                if (order.getLast().compareTo(sellPrice) > 0) {
                    return;
                }
                // 2. 买入利润低于1.03的话，先保留继续等等奇迹
                if (order.getLast().divide(order.getFilledPrice(), 2, BigDecimal.ROUND_DOWN).compareTo(new BigDecimal("1.03")) < 0) {
                    return;
                }
                // 3. 查询5分钟MD10线。没有下跌可以继续保留
                 // 查询一小时内5分钟K线
                final List<Candlestick2> candlestick = gateIoCommon.candlestick(tradeOrder.getSymbol(), "300", "1");
                final MdResultObj mdResult = MdCalculate.execute(candlestick, 300, 10);

                log.info("sell incr md10 eq. current:{}, previous:{}  symbol:{}", mdResult.getCurrent(), mdResult.getPrevious(), tradeOrder.getSymbol());
                if (mdResult.getCurrent().compareTo(mdResult.getPrevious()) >= 0) {
                    return;
                }
            } else {
                // 亏损百分比
                final BigDecimal percentage = order.getFilledPrice().divide(order.getLast(), 2, BigDecimal.ROUND_DOWN);
                // 最新价比买入价小，买入价没有跌3%，可以不买
                log.info("sell incr last < buy price. last:{}, buy price:{}, percentage:{}, symbol:{}",
                        order.getLast(), order.getFilledPrice(), percentage, tradeOrder.getSymbol());
                if (percentage.compareTo(new BigDecimal("1.02")) < 0) {
                    return;
                }
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
            sell.setStrategy("INCR");
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