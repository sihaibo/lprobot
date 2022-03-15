package com.lp.robot.dextools.enums;

import com.baomidou.mybatisplus.core.enums.IEnum;

/**
 * 功能描述:TradeOrderStatusEnum <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 14:44<br/>
 * @since JDK 1.8
 */
public enum TradeOrderStatusEnum implements IEnum<String> {

    // 已挂单
    OPEN,

    // 已取消
    CANCELLED,

    // 已完成
    CLOSED;

    @Override
    public String getValue() {
        return this.name();
    }
}