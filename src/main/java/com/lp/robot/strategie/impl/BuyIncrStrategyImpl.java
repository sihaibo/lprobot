package com.lp.robot.strategie.impl;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.TickersObj;
import com.lp.robot.dextools.service.ConfigService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.StrategyBuyCompleteEvent;
import com.lp.robot.strategie.StrategyProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
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
 * @date: 2022-03-01 18:29<br/>
 * @since JDK 1.8
 */
@Slf4j
@Service("buyIncrStrategy")
public class BuyIncrStrategyImpl implements StrategyProvider {

    @Autowired
    private ThreadPoolTaskExecutor executor;
    @Autowired
    private GateIoCommon gateIoCommon;
    @Autowired
    private ConfigService configService;
    @Autowired
    private ApplicationContext applicationContext;

    // 0点
    private final static LocalTime ZERO = LocalTime.of(0, 0, 0);
    // 8点
    private final static LocalTime EIGHT = LocalTime.of(8, 0, 0);
    // 20点
    private final static LocalTime TWENTY = LocalTime.of(20, 0, 0);

    // 定时缓存
    private TimedCache<String, String> timedCache = CacheUtil.newTimedCache(1000 * 60 * 60);

    @Override
    public void execute() {

        final String exclude = configService.getByKey("exclude.tickers", "");
        final String volume = configService.getByKey("trading.volume", "3000000");
        final List<TickersObj> tickers = gateIoCommon.getAllTickers(exclude, volume);

        log.info("buy incr strategy symbol:{}", tickers.stream().map(TickersObj::getSymbol).collect(Collectors.toList()));

        CountDownLatch latch = new CountDownLatch(tickers.size());
        List<TickersObj> percentages = new ArrayList<>();
        tickers.forEach(ticker -> executor.execute(() -> {
            try {
                final BigDecimal percentage = execute(ticker.getSymbol());
                TickersObj tickersObj = new TickersObj();
                tickersObj.setSymbol(ticker.getSymbol());
                tickersObj.setBuy(percentage);
                percentages.add(tickersObj);
            } finally {
                latch.countDown();
            }
        }));
        try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
        // 根据涨幅比排序，买入
        List<TickersObj> sort = percentages.stream()
                .filter(tickersObj -> tickersObj.getBuy().compareTo(BigDecimal.ZERO) > 0)
                .filter(tickersObj -> tickersObj.getBuy().compareTo(new BigDecimal("1.03")) > 0)
                .sorted(Comparator.comparing(TickersObj::getBuy)).collect(Collectors.toList());
        log.info("buy incr strategy sort result:{}", sort.stream().map(TickersObj::getSymbol).collect(Collectors.toList()));
        // 买入
        int number = configService.getStrategyNumber(CacheSingleton.KEY_STRATEGY_B);

        for (TickersObj tickersObj : sort) {
            // 已经买过的就不要在买了
            if (Objects.nonNull(timedCache.get(tickersObj.getSymbol(), false))) {
                continue;
            }
            if (number == 0) {
                return;
            }
            timedCache.put(tickersObj.getSymbol(), tickersObj.getSymbol());
            applicationContext.publishEvent(new StrategyBuyCompleteEvent(tickersObj.getSymbol(), CacheSingleton.KEY_STRATEGY_B, number));
            number--;
        }
    }

    private BigDecimal execute(String symbol) {
        final LocalTime localTime = LocalTime.now();
        // 大于0点，小于8点（0点涨幅操作）
        if (localTime.compareTo(ZERO) > 0 && EIGHT.compareTo(localTime) > 0) {
            // 百分比
            return percentage(symbol, ZERO, "ZERO", "TWENTY");
        }
        // 大于8点，小于20点（8点涨幅操作）
        if (localTime.compareTo(EIGHT) > 0 && TWENTY.compareTo(localTime) > 0) {
            return percentage(symbol, EIGHT, "EIGHT", "ZERO");
        }
        // 大于20点（20点涨幅操作）
        if (localTime.compareTo(TWENTY) > 0 ) {
            return percentage(symbol, TWENTY, "TWENTY", "EIGHT");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 计算涨幅比例
     * @param symbol
     * @param localTime
     * @param key
     * @param previousKey
     * @return
     */
    private BigDecimal percentage(String symbol, LocalTime localTime, String key, String previousKey) {
        // 1. 把上一个时间点的开盘价Key给删除
        CacheSingleton.getInstance().remove(CacheSingleton.KEY_BUY_INCR, symbol + previousKey);
        // 1. 把上一个时间点的已购币给删除了
        // 2. 查询整点开盘价
        BigDecimal price = CacheSingleton.getInstance().get(CacheSingleton.KEY_BUY_INCR, symbol + key);
        if (Objects.isNull(price)) {
            final List<Candlestick2> candlesticks = gateIoCommon.candlestick(symbol, "3600", "2");
            LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(), localTime);
            final long milli = localDateTime.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            final Optional<Candlestick2> opt = candlesticks.stream().filter(candlestick2 -> candlestick2.getTime() == milli).findFirst();
            if (!opt.isPresent()) {
                return BigDecimal.ZERO;
            }
            CacheSingleton.getInstance().put(CacheSingleton.KEY_BUY_INCR, symbol + key, opt.get().getOpen());
            price = opt.get().getOpen();
        }
        // 3. 获取最新价格，计算涨幅比例
        final TickersObj ticker = gateIoCommon.getTicker(symbol);
        // 涨幅比例
        return new BigDecimal(ticker.getLast()).divide(price, 3, BigDecimal.ROUND_DOWN);
    }
}