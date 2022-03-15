package com.lp.robot.dextools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-22 14:16<br/>
 * @since JDK 1.8
 */
@Data
public class TradeOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String symbol;

    // 三方订单号
    private String orderNumber;

    // 价格
    private BigDecimal price;

    // 成交价格
    private BigDecimal filledPrice;

    // 交易数量
    private BigDecimal tradeNumber;

    // 挂单状态
    private TradeOrderStatusEnum tradeOrderStatus;

    // 交易类型
    private TradeOrderTypeEnum tradeOrderType;

    // 错误描述
    private String errorMsg;

    //
    private LocalDateTime createDateTime;

    private TradeOrderVersion tradeOrderVersion;

    private String strategy;

    @TableField(exist = false)
    private BigDecimal last;

    @TableField(exist = false)
    private BigDecimal toU;

}