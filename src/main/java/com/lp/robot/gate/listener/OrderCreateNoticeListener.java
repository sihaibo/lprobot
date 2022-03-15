package com.lp.robot.gate.listener;

import com.lp.robot.gate.common.WeChatNoticeService;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.gate.common.GateIoCommon;
import com.lp.robot.gate.event.OrderCreateEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-25 17:27<br/>
 * @since JDK 1.8
 */
@Component
public class OrderCreateNoticeListener implements ApplicationListener<OrderCreateEvent> {

    @Autowired
    private WeChatNoticeService weChatNoticeService;
    @Autowired
    private GateIoCommon gateIoCommon;

    @Async
    @Override
    public void onApplicationEvent(OrderCreateEvent event) {
        TradeOrder tradeOrder = (TradeOrder) event.getSource();
        if (TradeOrderStatusEnum.CANCELLED.equals(tradeOrder.getTradeOrderStatus())) {
            return;
        }
        StringBuffer buffer = new StringBuffer("创建订单通知。\n");
        buffer.append("方向：").append(tradeOrder.getTradeOrderType().getValue()).append("\n");
        buffer.append("版本：").append(tradeOrder.getTradeOrderVersion().getValue()).append("\n");
        buffer.append("币种：").append(tradeOrder.getSymbol()).append("\n");
        buffer.append("价格：").append(tradeOrder.getPrice().toPlainString()).append("\n");
        buffer.append("当前价格：").append(gateIoCommon.getTicker(tradeOrder.getSymbol()).getLast()).append("\n");
        buffer.append("数量：").append(tradeOrder.getTradeNumber().toPlainString()).append("\n");
        buffer.append("单号：").append(tradeOrder.getOrderNumber()).append("\n");
        buffer.append("策略：").append(tradeOrder.getStrategy()).append("\n");
        buffer.append("时间：").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append("\n");
        weChatNoticeService.notice(buffer.toString());
    }
}