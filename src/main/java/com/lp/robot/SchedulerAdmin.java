package com.lp.robot;

import com.lp.robot.gate.handler.StatisticalHandler;
import com.lp.robot.strategie.StrategyFactory;
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


    @Autowired
    private StrategyFactory factory;

    @Autowired
    private StatisticalHandler statisticalHandler;

    @Scheduled(cron = "5 0 8 * * ?")
    private void statistical() {
        statisticalHandler.execute();
    }


    @Scheduled(cron = "10 0/5 * * * ?")
    private void buy() {
        factory.get("buyCStrategy").execute();
    }

    @Scheduled(cron = "30 * * * * ?")
    private void sell() {
        factory.get("sellCStrategy").execute();
    }

    @Scheduled(cron = "0/15 * 0,8,20 * * ?")
    private void incrBuy() {
        factory.get("buyIncrStrategy").execute();
    }
}