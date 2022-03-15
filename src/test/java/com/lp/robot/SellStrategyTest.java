package com.lp.robot;

import com.lp.robot.strategie.StrategyProvider;
import javax.annotation.Resource;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-01 10:58<br/>
 * @since JDK 1.8
 */
@SpringBootTest(classes = {LpRobotApplication.class})
@RunWith(SpringRunner.class)
public class SellStrategyTest {

    @Resource(name = "sellStrategy")
    private StrategyProvider sellStrategy;


}