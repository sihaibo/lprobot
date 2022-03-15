package com.lp.robot;

import com.lp.robot.gate.handler.StatisticalHandler;
import com.lp.robot.strategie.StrategyProvider;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2021-06-26 15:01<br/>
 * @since JDK 1.8
 */
@Component
@Slf4j
public class SchedulerAdmin {


    @Resource(name = "buyStrategy")
    private StrategyProvider buyStrategy;

    @Resource(name = "buyIncrStrategy")
    private StrategyProvider buyIncrStrategy;

    @Resource(name = "sellIncrStrategy")
    private StrategyProvider sellIncrStrategy;

    @Autowired
    private StatisticalHandler statisticalHandler;

    @Scheduled(cron = "5 0 8 * * ?")
    private void statistical() {
        statisticalHandler.execute();
    }


    @Scheduled(cron = "10 1/5 * * * ?")
    private void buy() {
        buyStrategy.execute();
    }

    @Scheduled(cron = "0/10 * * * * ?")
    private void sell() {
        sellIncrStrategy.execute();
    }

    @Scheduled(cron = "0/15 * 0,8,20 * * ?")
    private void incrBuy() {
        buyIncrStrategy.execute();
    }
}