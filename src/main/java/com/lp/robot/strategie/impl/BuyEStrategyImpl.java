package com.lp.robot.strategie.impl;

import com.lp.robot.dextools.service.ConfigService;
import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.StrategyBuyCompleteEvent;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.TickersObj;
import com.lp.robot.strategie.StrategyProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * @date: 2022-04-01 11:17<br/>
 * @since JDK 1.8
 */
@Slf4j
@Service("buyEStrategy")
public class BuyEStrategyImpl implements StrategyProvider {

    private boolean enable = false;

    private List<String> boughtList = new ArrayList<>();

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
        if (!isEnable()) {
            return;
        }
        final String exclude = configService.getByKey("exclude.tickers", "");
        final String volume = configService.getByKey("trading.volume", "3000000");
        final List<TickersObj> tickers =
                gateIoCommon.getAllTickers(exclude, volume)
                        .stream().sorted(Comparator.comparing(o -> new BigDecimal(o.getBaseVolume()), Comparator.reverseOrder()))
                        .collect(Collectors.toList());
        tickers.removeIf(tickersObj -> boughtList.contains(tickersObj.getSymbol()));
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

        tickers.removeIf(tickersObj -> !symbols.contains(tickersObj.getSymbol()));

        // 发起买入
        final int number = configService.getStrategyNumber(CacheSingleton.KEY_STRATEGY_E);
        for (int i = 0; i < tickers.size(); i++) {
            boughtList.add(tickers.get(i).getSymbol());
            if (i < number) {
                applicationContext.publishEvent(new StrategyBuyCompleteEvent(tickers.get(i).getSymbol(), CacheSingleton.KEY_STRATEGY_E, number));
            }
        }
    }

    private boolean execute(String symbol) {
        final List<Candlestick2> candlestick = gateIoCommon.candlestick(symbol, "3600", "25");
        candlestick.sort(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()));
        final LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(0, 0, 0));
        final long milli = localDateTime.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        final Optional<Candlestick2> opt = candlestick.stream().filter(candlestick2 -> candlestick2.getTime() == milli).findFirst();
        return opt.filter(candlestick2 ->
                candlestick.get(0).getClose().divide(candlestick2.getOpen(), 5, BigDecimal.ROUND_HALF_UP)
                        .compareTo(new BigDecimal("1.002")) >= 0).isPresent();
    }

    private boolean isEnable() {
        final LocalDateTime localDateTime = LocalDateTime.now();
        if (!this.enable && localDateTime.getHour() == 0) {
            this.enable = true;
        }
        final String day = Integer.toString(localDateTime.getDayOfYear());
        if (!boughtList.contains(day)) {
            boughtList.clear();
            boughtList.add(day);
        }
        return enable;
    }
}