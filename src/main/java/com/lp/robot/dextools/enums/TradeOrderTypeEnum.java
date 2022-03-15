package com.lp.robot.dextools.enums;

import com.baomidou.mybatisplus.core.enums.IEnum;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 16:11<br/>
 * @since JDK 1.8
 */
public enum TradeOrderTypeEnum implements IEnum<String> {

    BUY,

    SELL;

    @Override
    public String getValue() {
        return this.name();
    }
}