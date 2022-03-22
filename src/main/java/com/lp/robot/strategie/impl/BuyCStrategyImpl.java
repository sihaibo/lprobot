package com.lp.robot.strategie.impl;

import com.alibaba.fastjson.JSON;
import com.lp.robot.dextools.service.ConfigService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.common.LimitedList;
import com.lp.robot.gate.event.ErrorEvent;
import com.lp.robot.gate.event.StrategyBuyCompleteEvent;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.TickersObj;
import com.lp.robot.strategie.StrategyProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * @date: 2022-03-17 16:38<br/>
 * @since JDK 1.8
 */
@Slf4j
@Service("buyCStrategy")
public class BuyCStrategyImpl implements StrategyProvider {

    @Autowired
    private ThreadPoolTaskExecutor executor;
    @Autowired
    private GateIoCommon gateIoCommon;
    @Autowired
    private ConfigService configService;
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void execute() {

        final String exclude = configService.getByKey("exclude.tickers", "");
        final String volume = configService.getByKey("trading.volume", "3000000");
        final List<TickersObj> tickers = gateIoCommon.getAllTickers(exclude, volume);
        CountDownLatch latch = new CountDownLatch(tickers.size());
        List<String> symbols = new ArrayList<>();
        tickers.forEach(tickersObj -> executor.execute(() -> {
            try {
                if (execute(tickersObj.getSymbol())) {
                    symbols.add(tickersObj.getSymbol());
                }
            } finally {
                latch.countDown();
            }
        }));
        try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
        // 发起买入
        final int number = configService.getStrategyNumber(CacheSingleton.KEY_STRATEGY_C);
        for (int i = 0; i < symbols.size(); i++) {
            if (i < number) {
                applicationContext.publishEvent(new StrategyBuyCompleteEvent(symbols.get(i), CacheSingleton.KEY_STRATEGY_C, number));
            }
        }
    }

    public boolean execute(String symbol) {
        // 查询一小时内5分钟K线
        final List<Candlestick2> candlestick = gateIoCommon.candlestick(symbol, "300", "1");
        candlestick.sort(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()));
        LimitedList<Candlestick2> limitedList = new LimitedList<>(10);
        BigDecimal first = BigDecimal.ZERO, second = BigDecimal.ZERO, third = BigDecimal.ZERO;
        if (candlestick.size() < 13) {
            log.error("BuyCStrategyImpl execute. gate resp candlestick symbol:{} error. {}", symbol, JSON.toJSONString(candlestick));
            applicationContext.publishEvent(new ErrorEvent(symbol, "查询1小时5分钟K线返回数量有问题."));
            return false;
        }
        for (int i = 1; i < 13; i++) {
            limitedList.add(candlestick.get(i));
            if (limitedList.size() < 10) {
                continue;
            }
            if (i == 10) {
                third = limitedList.stream()
                        .map(Candlestick2::getClose)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(String.valueOf(5)), candlestick.get(0).getClose().scale(), BigDecimal.ROUND_DOWN);
            }
            if (i == 11) {
                second = limitedList.stream()
                        .map(Candlestick2::getClose)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(String.valueOf(5)), candlestick.get(0).getClose().scale(), BigDecimal.ROUND_DOWN);
            }
            if (i == 12) {
                first = limitedList.stream()
                        .map(Candlestick2::getClose)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(String.valueOf(5)), candlestick.get(0).getClose().scale(), BigDecimal.ROUND_DOWN);
            }
        }
        return third.compareTo(second) > 0 && first.compareTo(second) > 0;
    }
}