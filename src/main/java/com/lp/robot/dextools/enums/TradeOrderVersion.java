package com.lp.robot.dextools.enums;

import com.baomidou.mybatisplus.core.enums.IEnum;

/**
 * 功能描述:TradeOrderVersion <br/>
 *
 * @author HaiBo
 * @date: 2022-02-24 16:48<br/>
 * @since JDK 1.8
 */
public enum TradeOrderVersion implements IEnum<String> {

    V2,

    V4;

    @Override
    public String getValue() {
        return this.name();
    }
}