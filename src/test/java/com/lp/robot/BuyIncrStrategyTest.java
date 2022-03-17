package com.lp.robot;

import com.lp.robot.strategie.StrategyProvider;
import javax.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    public void buy() {
        buyCStrategy.execute();
    }

}