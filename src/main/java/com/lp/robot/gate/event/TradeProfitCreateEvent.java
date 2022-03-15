package com.lp.robot.gate.event;

import org.springframework.context.ApplicationEvent;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-10 17:51<br/>
 * @since JDK 1.8
 */
public class TradeProfitCreateEvent extends ApplicationEvent {

    public TradeProfitCreateEvent(Object source) {
        super(source);
    }
}