package com.lp.robot.gate.handler;

import com.lp.robot.gate.common.WeChatNoticeService;
import com.lp.robot.dextools.entity.TradeProfit;
import com.lp.robot.dextools.service.TradeProfitService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-10 17:58<br/>
 * @since JDK 1.8
 */
@Component
public class StatisticalHandler {

    @Autowired
    private TradeProfitService tradeProfitService;
    @Autowired
    private WeChatNoticeService weChatNoticeService;

    public void execute() {
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime localDateTime = now.minusDays(1);
        final List<TradeProfit> profits = tradeProfitService.findBetweenCreateDateTime(localDateTime, now);
        StringBuffer buffer = new StringBuffer("盈利统计。\n");
        buffer.append("昨日交易笔数：").append(profits.size()).append("\n");
        profits.stream().collect(Collectors.groupingBy(TradeProfit::getStrategy)).forEach((s, tradeProfits) -> {
            buffer.append("策略：").append(s).append("\n");
            buffer.append("笔数：").append(tradeProfits.size()).append("\n");
            buffer.append("盈利：").append(tradeProfits.stream().map(TradeProfit::getProfit).reduce(BigDecimal.ZERO,BigDecimal::add)).append("\n");
            buffer.append("盈利笔数：").append(
                    tradeProfits.stream()
                            .filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) > 0)
                            .count()).append("\n");
            buffer.append("盈利金额：").append(
                    tradeProfits.stream()
                            .filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) > 0)
                            .map(TradeProfit::getProfit)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)).append("\n");
            buffer.append("亏损笔数：").append(
                    tradeProfits.stream()
                            .filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) < 0)
                            .count()).append("\n");
            buffer.append("亏损金额：").append(
                    tradeProfits.stream()
                            .filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) < 0)
                            .map(TradeProfit::getProfit)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)).append("\n");
        });
        buffer.append("总盈利：").append(profits.stream().map(TradeProfit::getProfit).reduce(BigDecimal.ZERO,BigDecimal::add)).append("\n");
        buffer.append("总盈利笔数：").append(profits.stream().filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) > 0).count()).append("\n");
        buffer.append("总盈利金额：").append(
                profits.stream()
                        .filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) > 0)
                        .map(TradeProfit::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add)).append("\n");
        buffer.append("总亏损笔数：").append(profits.stream().filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) < 0).count()).append("\n");
        buffer.append("总亏损金额：").append(
                profits.stream()
                        .filter(tradeProfit -> tradeProfit.getProfit().compareTo(BigDecimal.ZERO) < 0)
                        .map(TradeProfit::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add)).append("\n");
        buffer.append("时间：").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append("\n");
        weChatNoticeService.notice(buffer.toString());
    }

}