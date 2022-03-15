package com.lp.robot.gate.event;

import com.lp.robot.dextools.entity.TradeOrder;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEvent;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-03 09:41<br/>
 * @since JDK 1.8
 */
public class StrategySellCompleteEvent extends ApplicationEvent {

    private Consumer<TradeOrder> consumer;

    public StrategySellCompleteEvent(TradeOrder source, Consumer<TradeOrder> consumer) {
        super(source);
        this.consumer = consumer;
    }

    public Consumer<TradeOrder> getConsumer() {
        return consumer;
    }
}