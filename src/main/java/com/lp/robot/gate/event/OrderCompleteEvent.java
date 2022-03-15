package com.lp.robot.gate.event;

import org.springframework.context.ApplicationEvent;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-25 15:42<br/>
 * @since JDK 1.8
 */
public class OrderCompleteEvent extends ApplicationEvent {

    public OrderCompleteEvent(Object source) {
        super(source);
    }
}