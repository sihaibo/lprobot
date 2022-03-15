package com.lp.robot.dextools.service;

import com.lp.robot.dextools.entity.TradeProfit;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 功能描述:TradeProfitService <br/>
 *
 * @author HaiBo
 * @date: 2022-03-10 17:42<br/>
 * @since JDK 1.8
 */
public interface TradeProfitService {

    void create(TradeProfit tradeProfit);

    List<TradeProfit> findBetweenCreateDateTime(LocalDateTime startDateTime, LocalDateTime endDateTime);
}