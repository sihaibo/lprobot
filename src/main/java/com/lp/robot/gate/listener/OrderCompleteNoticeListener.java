package com.lp.robot.gate.listener;

import com.lp.robot.gate.common.WeChatNoticeService;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.event.OrderCompleteEvent;
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
 * @date: 2022-02-25 17:50<br/>
 * @since JDK 1.8
 */
@Component
public class OrderCompleteNoticeListener implements ApplicationListener<OrderCompleteEvent> {

    @Autowired
    private WeChatNoticeService weChatNoticeService;
    @Autowired
    private TradeOrderService tradeOrderService;

    @Async
    @Override
    public void onApplicationEvent(OrderCompleteEvent event) {
        String id = String.valueOf(event.getSource());
        final TradeOrder tradeOrder = tradeOrderService.getById(id);
        StringBuffer buffer = new StringBuffer("订单完成通知。\n");
        buffer.append("方向：").append(tradeOrder.getTradeOrderType().getValue()).append("\n");
        buffer.append("币种：").append(tradeOrder.getSymbol()).append("\n");
        buffer.append("价格：").append(tradeOrder.getPrice().toPlainString()).append("\n");
        buffer.append("数量：").append(tradeOrder.getTradeNumber().toPlainString()).append("\n");
        buffer.append("策略：").append(tradeOrder.getStrategy()).append("\n");
        buffer.append("状态：").append(TradeOrderStatusEnum.CLOSED.equals(tradeOrder.getTradeOrderStatus()) ? "完成" : "取消").append("\n");
        buffer.append("时间：").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append("\n");
//        weChatNoticeService.notice(buffer.toString());
    }
}