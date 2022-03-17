package com.lp.robot.strategie.impl;

import com.lp.robot.dextools.service.ConfigService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.common.LimitedList;
import com.lp.robot.gate.common.MaCalculate;
import com.lp.robot.gate.event.StrategyBuyCompleteEvent;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.MaResultObj;
import com.lp.robot.gate.obj.TickersObj;
import com.lp.robot.strategie.StrategyProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 10:36<br/>
 * @since JDK 1.8
 */
@Slf4j
@Service("buyStrategy")
public class BuyStrategyImpl implements StrategyProvider {

    @Autowired
    private GateIoCommon gateIoCommon;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ThreadPoolTaskExecutor executor;
    @Autowired
    private ConfigService configService;

    @Override
    public void execute() {

        final String exclude = configService.getByKey("exclude.tickers", "");
        final String volume = configService.getByKey("trading.volume", "3000000");
        final List<TickersObj> tickers = gateIoCommon.getAllTickers(exclude, volume);
//        TickersObj tickersObj = new TickersObj();
//        tickersObj.setSymbol("mir_usdt");
//        List<TickersObj> tickers = Collections.singletonList(tickersObj);

        CountDownLatch latch = new CountDownLatch(tickers.size());
        List<String> symbols = new ArrayList<>();
        tickers.forEach(ticker -> executor.execute(() -> {
            try {
                final String symbol = execute0(ticker.getSymbol());
                if (Objects.nonNull(symbol)) {
                    symbols.add(symbol);
                }
            } finally {
                latch.countDown();
            }
        }));
        try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
        // 发起买入
        final int number = configService.getStrategyNumber("BASE");
        for (int i = 0; i < symbols.size(); i++) {
            if (i < number) {
                applicationContext.publishEvent(new StrategyBuyCompleteEvent(symbols.get(i), "BASE", number));
            }
        }
    }

    private String execute0(String symbol) {
        log.info("buy strategy start. symbol:{}", symbol);
        // 获取5分钟K线
        // 查询一小时内5分钟K线
        final List<Candlestick2> candlestick5 = gateIoCommon.candlestick(symbol, "300", "1");
        // 计算15分钟的MA10
        // 查询一小时内5分钟K线
//        final List<Candlestick2> candlestick15 = gateIoCommon.candlestick(symbol, "900", "3");
        // 计算5分钟MA5、MA10、15分钟MA10
        final boolean ma35 = isMaContinue(symbol, MaCalculate.execute(candlestick5, 300, 5));
        final boolean ma310 = isMaContinue(symbol, MaCalculate.execute(candlestick5, 300, 10));
//        final boolean md95 = isMaContinue(symbol, MaCalculate.execute(candlestick15, 900, 5));
        if (ma35 || ma310) {
            return null;
        }
        // 计算1分钟的K线是否上涨（遇到过正在下跌的情况就买入进去了）
        final MaResultObj maResultObj = MaCalculate.execute(gateIoCommon.candlestick(symbol, "60", "0.5"), 60, 5);
        if (maResultObj.getCurrent().compareTo(maResultObj.getPrevious()) < 0) {
            return null;
        }

        // 3. 24小时内最低价的时候在买
        final TickersObj ticker = gateIoCommon.getTicker(symbol);
        // 最大价
        BigDecimal maxClose = new BigDecimal(ticker.getHigh24hr());
        // 最低价
        BigDecimal minClose = new BigDecimal(ticker.getLow24hr());
        // 当前价
        BigDecimal currentClose = new BigDecimal(ticker.getLast());
        // max:150 min:100
        // (150 / 100) = 1.5 - 1 = 0.5 * 0.3 = 0.15 + 1 = 1.15 * 100 = 115
        // current < 115 买入
        final BigDecimal multiply = maxClose.divide(minClose, 4, BigDecimal.ROUND_DOWN)
                .subtract(BigDecimal.ONE).multiply(new BigDecimal("0.6")).add(BigDecimal.ONE).multiply(minClose);
        log.info("buy strategy, 24 hours range eq . max:{}, min:{}, last:{}, mult:{} symbol:{}",
                maxClose, minClose, currentClose, multiply, symbol);
        if (currentClose.compareTo(multiply) > 0) {
            return null;
        }
        log.info("buy strategy, success symbol:{}", symbol);
        return symbol;
    }

    private boolean isMaContinue(String symbol, MaResultObj ma) {

        log.info("buy strategy, ma{} eq current:{}, previous:{} symbol:{}", ma.getKey(), ma.getCurrent(), ma.getPrevious(), symbol);

        if (ma.getCurrent().compareTo(ma.getPrevious()) <= 0) {
            log.warn("buy strategy ma{} is down, continue. symbol:{}", ma.getKey(), symbol);
            CacheSingleton.getInstance().remove(CacheSingleton.KEY_MA + ma.getKey(), symbol);
            return true;
        } else {
            // 记录下转折点MA
            BigDecimal cache = CacheSingleton.getInstance().get(CacheSingleton.KEY_MA + ma.getKey(), symbol);
            if (Objects.isNull(cache)) {
                log.warn("buy strategy cache ma{} init . ma:{}, symbol:{}", ma.getKey(), ma.getPrevious(), symbol);
                CacheSingleton.getInstance().put(CacheSingleton.KEY_MA + ma.getKey(), symbol, ma.getPrevious());
                cache = ma.getPrevious();
            }
            log.info("buy strategy current ma{}:{} cache ma:{} . symbol:{}", ma.getKey(), ma.getCurrent(), cache, symbol);
            // 判断转折点MA5和当前MA5
            if (ma.getCurrent().divide(cache, 3, BigDecimal.ROUND_DOWN).compareTo(new BigDecimal("1.002")) < 0) {
                log.warn("buy strategy current ma{}:{} < cache ma:{} 0.5% continue. symbol:{}", ma.getKey(), ma.getCurrent(), cache, symbol);
                return true;
            }
        }
        return false;
    }

    private String execute(String symbol) {
        // 查询一小时内5分钟K线
        final List<Candlestick2> candlestick = gateIoCommon.candlestick(symbol, "300", "2");
        // 根据时间倒序
        candlestick.sort(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()));
        // 收盘价总和
        BigDecimal closeSum = BigDecimal.ZERO;
        for (int i = 1; i <= 5; i++) {
            final Candlestick2 candlestick2 = candlestick.get(i);
            closeSum = closeSum.add(candlestick2.getClose());
        }
        // MA5值
        final BigDecimal ma5 = closeSum.divide(new BigDecimal("5"), closeSum.scale(), BigDecimal.ROUND_HALF_UP);
        // 最后一次收盘K线
        final Candlestick2 lastCandlestick = candlestick.get(1);
        // 上一次MA5值不存在的话，初始化一下
        if (Objects.isNull(CacheSingleton.getInstance().get(CacheSingleton.KEY_MA5, symbol))) {
            log.info("buy strategy last close is null. init. symbol:{}", symbol);
            lastVal(symbol, ma5, lastCandlestick.getClose());
            return null;
        }

        if (ma5.compareTo(CacheSingleton.getInstance().get(CacheSingleton.KEY_MA5, symbol)) <= 0) {
            lastVal(symbol, ma5, lastCandlestick.getClose());
            return null;
        }

        // 判断最近的MA5值
        List<BigDecimal> lastList = new ArrayList<>();
        LimitedList<Candlestick2> limitedList = new LimitedList<>(5);
        for (int i = 1; i < candlestick.size(); i++) {
            limitedList.add(candlestick.get(i));
            if (limitedList.size() < 5) {
                continue;
            }
            lastList.add(
                    limitedList.stream()
                            .map(Candlestick2::getClose).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(new BigDecimal("5"), closeSum.scale(), BigDecimal.ROUND_HALF_UP));
        }
        int g = 0;
        for (int i = 0; i < lastList.size() - 1; i++) {
            if (lastList.get(i).compareTo(lastList.get(i + 1)) <= 0) {
                g++;
            }
        }
        if (g < 4) {
            log.info("buy strategy ma5 history failed. symbol:{} ", symbol);
            lastVal(symbol, ma5, lastCandlestick.getClose());
            return null;
        }

        // 俩小时内最高最低价，当前价格在最高价格40%以下，可以买入
        final List<Candlestick2> candlesticks = gateIoCommon.candlestick(symbol, "3600", "2");
        candlesticks.sort(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()));
        // 最大价
        BigDecimal maxClose = candlesticks.stream().map(Candlestick2::getHigh).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        // 最低价
        BigDecimal minClose = candlesticks.stream().map(Candlestick2::getLow).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        // 当前价
        BigDecimal current = candlesticks.get(0).getClose();

        // max:150 min:100
        // (150 / 100) = 1.5 - 1 = 0.5 * 0.3 = 0.15 + 1 = 1.15 * 100 = 115
        // current < 115 买入
        final BigDecimal multiply = maxClose.divide(minClose, 4, BigDecimal.ROUND_DOWN)
                .subtract(BigDecimal.ONE).multiply(new BigDecimal("0.3")).add(BigDecimal.ONE).multiply(minClose);
        if (current.compareTo(multiply) > 0) {
            lastVal(symbol, ma5, lastCandlestick.getClose());
            return null;
        }

        // 本次MA5 > 上次MA5，信号转折点
        // 最新的K线图
        final Candlestick2 latest = candlestick.get(0);
        final BigDecimal close = latest.getClose();
        // 最新收盘价/转折点收盘价 计算百分比
        final BigDecimal percentage = close.divide(CacheSingleton.getInstance().get(CacheSingleton.KEY_LAST, symbol), 4, BigDecimal.ROUND_HALF_UP);
        lastVal(symbol, ma5, lastCandlestick.getClose());
        // 百分比大于等于千五，可以买入。或者最新的K线判断信号
        if (percentage.compareTo(new BigDecimal("1.005")) >= 0 || latestSignal(lastCandlestick, latest)) {
            return symbol;
        }
        return null;
    }

    /**
     * 最新的K线判断信号
     * @param lastCandlestick
     * @param latest
     * @return
     */
    private boolean latestSignal(Candlestick2 lastCandlestick, Candlestick2 latest) {
        // 单线百分比
        BigDecimal candlestickPercentage = lastCandlestick.getClose().divide(lastCandlestick.getOpen(), 4, BigDecimal.ROUND_HALF_UP);
        // 单线上升3%，并且最新K线也是涨就买入
        return candlestickPercentage.compareTo(new BigDecimal("1.03")) >= 0
                && latest.getClose().compareTo(latest.getOpen()) > 0;
    }

    private void lastVal(String symbol, BigDecimal ma5, BigDecimal close) {
        CacheSingleton.getInstance().put(CacheSingleton.KEY_MA5, symbol, ma5);
        CacheSingleton.getInstance().put(CacheSingleton.KEY_LAST, symbol, close);
    }
}