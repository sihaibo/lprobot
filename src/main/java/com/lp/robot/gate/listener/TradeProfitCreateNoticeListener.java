package com.lp.robot.gate.listener;

import com.lp.robot.gate.common.WeChatNoticeService;
import com.lp.robot.dextools.entity.TradeProfit;
import com.lp.robot.gate.event.TradeProfitCreateEvent;
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
 * @date: 2022-03-10 17:51<br/>
 * @since JDK 1.8
 */
@Component
public class TradeProfitCreateNoticeListener implements ApplicationListener<TradeProfitCreateEvent> {

    @Autowired
    private WeChatNoticeService weChatNoticeService;

    @Async
    @Override
    public void onApplicationEvent(TradeProfitCreateEvent event) {
        TradeProfit tradeProfit = (TradeProfit) event.getSource();
        StringBuffer buffer = new StringBuffer("盈利明细。\n");
        buffer.append("币种：").append(tradeProfit.getSymbol()).append("\n");
        buffer.append("买入价格：").append(tradeProfit.getBuyPrice()).append("\n");
        buffer.append("买入数量：").append(tradeProfit.getBuyNumber()).append("\n");
        buffer.append("卖出价格：").append(tradeProfit.getSellPrice()).append("\n");
        buffer.append("卖出数量：").append(tradeProfit.getSellNumber()).append("\n");
        buffer.append("盈利：").append(tradeProfit.getProfit().toPlainString()).append("\n");
        buffer.append("策略：").append(tradeProfit.getStrategy()).append("\n");
        buffer.append("时间：").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append("\n");
        weChatNoticeService.notice(buffer.toString());
    }
}