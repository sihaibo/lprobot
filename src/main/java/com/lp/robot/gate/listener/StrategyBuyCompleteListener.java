package com.lp.robot.gate.listener;

import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import com.lp.robot.dextools.service.ConfigService;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategyBuyCompleteEvent;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.TickersObj;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
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
 * @date: 2022-03-02 15:10<br/>
 * @since JDK 1.8
 */
@Slf4j
@Component
public class StrategyBuyCompleteListener implements ApplicationListener<StrategyBuyCompleteEvent> {

    @Autowired
    private TradeOrderService tradeOrderService;
    @Autowired
    private GateIoCommon gateIoCommon;
    @Autowired
    private ConfigService configService;
    @Autowired
    private ApplicationContext applicationContext;

    private ReentrantLock lock = new ReentrantLock();

    @Async
    @Override
    public void onApplicationEvent(StrategyBuyCompleteEvent event) {

        String symbol = String.valueOf(event.getSource());

        // 紧急避险。当BTC当日是亏本的时候就不买了
        final List<Candlestick2> candlestick = gateIoCommon.candlestick("btc_usdt", "3600", "25");
        candlestick.sort(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()));
        final LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(0, 0, 0));
        final long milli = localDateTime.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        final Optional<Candlestick2> opt = candlestick.stream().filter(candlestick2 -> candlestick2.getTime() == milli).findFirst();
        if (!opt.isPresent()) {
            return;
        }
        if (candlestick.get(0).getClose().divide(opt.get().getOpen(), 5, BigDecimal.ROUND_HALF_UP).compareTo(BigDecimal.ONE) < 0) {
            return;
        }

        final List<TradeOrder> processing = tradeOrderService.findProcessing();

        final long count = processing.stream()
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
                .filter(tradeOrder -> event.getStrategy().equals(tradeOrder.getStrategy())).count();

        // 1. 判断当前策略是否超过策略买入最大值
        if (count >= event.getNumber()) {
            return;
        }

        // 2. 判断symbol是否已经买过了
        final long count1 = processing.stream()
                .filter(tradeOrder -> TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus()))
                .filter(tradeOrder -> symbol.equals(tradeOrder.getSymbol())).count();
        if (count1 > 0) {
            return;
        }
        // 上锁处理，要不一直并发账户钱足够，创建订单失败情况
        try {
            lock.lock();
            // 2. 查询账户余额
            final BigDecimal balances = gateIoCommon.balances("usdt");
            // 最大买入
            String max = configService.getByKey("buy.max", "5");
            if (balances.compareTo(new BigDecimal(max)) < 0) {
                return;
            }
            // 3. 获取最新价
            final TickersObj ticker = gateIoCommon.getTicker(symbol);
            BigDecimal last = new BigDecimal(ticker.getLast());
            // 优化买入价格
            BigDecimal price = last.multiply(new BigDecimal("1.001"));
            if (price.compareTo(BigDecimal.ZERO) == 0) {
                return;
            }
            // 3. 设置价格
            BigDecimal volume = new BigDecimal(max);
            BigDecimal orderBookPrice = gateIoCommon.orderBook(symbol, true, volume);
            // 4. 挂买单
            TradeOrder tradeOrder = new TradeOrder();
            tradeOrder.setSymbol(symbol);
            tradeOrder.setPrice(orderBookPrice.compareTo(BigDecimal.ZERO) == 0 ? price : orderBookPrice);
            tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.OPEN);
            tradeOrder.setTradeOrderType(TradeOrderTypeEnum.BUY);
            tradeOrder.setTradeOrderVersion(TradeOrderVersion.V2);
            tradeOrder.setStrategy(event.getStrategy());
            TradeOrder buyTradeOrder = gateIoCommon.buy(symbol, tradeOrder.getPrice(), volume);
            if (Objects.isNull(buyTradeOrder.getOrderNumber())) {
                applicationContext.publishEvent(new ErrorEvent(symbol, String.format("下单失败，描述：%s", buyTradeOrder.getErrorMsg())));
                tradeOrder.setErrorMsg(buyTradeOrder.getErrorMsg());
                tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.CANCELLED);
            } else {
                tradeOrder.setTradeNumber(buyTradeOrder.getTradeNumber());
                tradeOrder.setOrderNumber(buyTradeOrder.getOrderNumber());
            }
            tradeOrderService.insert(tradeOrder);
        } catch (Exception e) {
            log.error("buy strategy exception. symbol:{}", symbol, e);
        } finally {
            lock.unlock();
        }

    }
}