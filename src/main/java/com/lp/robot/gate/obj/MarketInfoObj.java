package com.lp.robot.gate.obj;

import lombok.Data;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-14 15:54<br/>
 * @since JDK 1.8
 */
@Data
public class MarketInfoObj {

    private String symbol;

    /**
     * 价格精度
     */
    private int priceDecimalPlaces;
    /**
     * 数量精度
     */
    private int totalDecimalPlaces;

}