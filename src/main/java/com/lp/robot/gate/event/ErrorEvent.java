package com.lp.robot.gate.event;

import org.springframework.context.ApplicationEvent;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 15:48<br/>
 * @since JDK 1.8
 */
public class ErrorEvent extends ApplicationEvent {

    private String msg;

    public ErrorEvent(Object source, String msg) {
        super(source);
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}