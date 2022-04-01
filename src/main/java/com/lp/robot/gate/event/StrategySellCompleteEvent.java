package com.lp.robot.gate.event;

import com.lp.robot.dextools.entity.TradeOrder;
import java.util.function.Function;
import org.springframework.context.ApplicationEvent;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-03 09:41<br/>
 * @since JDK 1.8
 */
public class StrategySellCompleteEvent extends ApplicationEvent {

    private Function<TradeOrder, Boolean> function;

    public StrategySellCompleteEvent(TradeOrder source, Function<TradeOrder, Boolean> function) {
        super(source);
        this.function = function;
    }

    public Function<TradeOrder, Boolean> getFunction() {
        return function;
    }
}