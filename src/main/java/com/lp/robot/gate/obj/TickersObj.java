package com.lp.robot.gate.obj;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2021-06-26 13:48<br/>
 * @since JDK 1.8
 */
@Data
public class TickersObj implements Serializable {

    // 标识
    private String symbol;

    // 交易量
    private String baseVolume;


    //    highestBid:买方最高价
    private String highestBid;


    //    last:最新成交价
    private String last;

    //    low24hr:24小时最低价
    private String low24hr;

    //    lowestAsk:卖方最低价
    private String lowestAsk;
    //    percentChange:涨跌百分比
    private String percentChange;

    //    quoteVolume: 兑换货币交易量
    private String quoteVolume;

    //    high24hr:24小时最高价
    private String high24hr;

    // 买入价
    private BigDecimal buy;

    // 买入时百分比


}