package com.lp.robot.dextools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-25 15:37<br/>
 * @since JDK 1.8
 */
@Data
public class TradeProfit {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String symbol;

    private BigDecimal buyPrice;

    private BigDecimal buyNumber;

    private BigDecimal sellPrice;

    private BigDecimal sellNumber;

    private BigDecimal profit;

    private String strategy;

    private LocalDateTime createDateTime;

}