package com.lp.robot.strategie;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-23 15:16<br/>
 * @since JDK 1.8
 */
@Component
public class StrategyFactory {

    @Autowired
    private Map<String, StrategyProvider> strategyMap;

    public StrategyProvider get(String strategyName) {
        return strategyMap.get(strategyName);
    }
}