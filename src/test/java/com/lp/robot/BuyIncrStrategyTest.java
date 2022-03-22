package com.lp.robot;

import com.lp.robot.gate.common.CacheSingleton;
import com.lp.robot.gate.event.StrategyBuyCompleteEvent;
import com.lp.robot.strategie.StrategyProvider;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-02 14:35<br/>
 * @since JDK 1.8
 */
@SpringBootTest(classes = {LpRobotApplication.class})
@RunWith(SpringRunner.class)
public class BuyIncrStrategyTest {

    @Resource(name = "buyIncrStrategy")
    private StrategyProvider buyIncrStrategy;

    @Resource(name = "buyStrategy")
    private StrategyProvider buyStrategy;

    @Resource(name = "buyCStrategy")
    private StrategyProvider buyCStrategy;

    @Autowired
    private ApplicationContext applicationContext;


    @Test
    public void buy() {
        buyCStrategy.execute();
    }

    @SneakyThrows
    @Test
    public void publish() {
        applicationContext.publishEvent(new StrategyBuyCompleteEvent("btc_usdt", CacheSingleton.KEY_STRATEGY_C, 0));
        Thread.currentThread().join();
    }
}