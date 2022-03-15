package com.lp.robot.gate.event;

import org.springframework.context.ApplicationEvent;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-02 15:07<br/>
 * @since JDK 1.8
 */
public class StrategyBuyCompleteEvent extends ApplicationEvent {

    // 策略
    private String strategy;

    // 可购买笔数
    private int number;

    /**
     * 策略完成，创建订单
     * @param symbol
     * @param strategy
     */
    public StrategyBuyCompleteEvent(String symbol, String strategy, int number) {
        super(symbol);
        this.strategy = strategy;
        this.number = number;
    }

    public String getStrategy() {
        return strategy;
    }

    public int getNumber() {
        return number;
    }
}