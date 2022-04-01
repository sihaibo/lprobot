package com.lp.robot.gate.common;

import com.lp.robot.gate.obj.Candlestick2;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-28 17:05<br/>
 * @since JDK 1.8
 */
public class MACDCalculate {

    // EMA（12）= 前一日EMA（12）×11/13＋今日收盘价×2/13
    // EMA（26）= 前一日EMA（26）×25/27＋今日收盘价×2/27
    // DIFF=今日EMA（12）- 今日EMA（26）
    // DEA（MACD）= 前一日DEA×8/10＋今日DIF×2/10
    // BAR=2×(DIFF－DEA)
    // 其买卖原则为：
    //1.DIF、DEA均为正，DIF向上突破DEA，买入信号参考。
    //2.DIF、DEA均为负，DIF向下跌破DEA，卖出信号参考。
    //3.DIF线与K线发生背离，行情可能出现反转信号。
    //4.DIF、DEA的值从正数变成负数，或者从负数变成正数并不是交易信号，因为它们落后于市场。
    //
    //基本用法
    //1. MACD金叉：DIFF 由下向上突破 DEA，为买入信号。
    //2. MACD死叉：DIFF 由上向下突破 DEA，为卖出信号。
    //3. MACD 绿转红：MACD 值由负变正，市场由空头转为多头。
    //4. MACD 红转绿：MACD 值由正变负，市场由多头转为空头。
    //5. DIFF 与 DEA 均为正值,即都在零轴线以上时，大势属多头市场，DIFF 向上突破 DEA，可作买入信号。
    //6. DIFF 与 DEA 均为负值,即都在零轴线以下时，大势属空头市场，DIFF 向下跌破 DEA，可作卖出信号。
    //7. 当 DEA 线与 K 线趋势发生背离时为反转信号。
    //8. DEA 在盘整局面时失误率较高,但如果配合RSI 及KDj指标可适当弥补缺点。

    public static final String KEY_DIF = "DIF";
    public static final String KEY_DEA = "DEA";
    public static final String KEY_MACD = "MACD";

    private static BigDecimal ema(List<BigDecimal> candlestick, int number) {
        final BigDecimal k = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(number + 1), candlestick.get(0).scale(), BigDecimal.ROUND_HALF_UP);
        BigDecimal ema = candlestick.get(0);
        for (int i = 1; i < candlestick.size(); i++) {
            // ema = list.get(i) * k + ema * (1 - k);
            // 第二天以后，当天收盘 收盘价乘以系数再加上昨天EMA乘以系数-1
            ema = candlestick.get(i).multiply(k).setScale(candlestick.get(0).scale(), BigDecimal.ROUND_HALF_UP).add(ema.multiply(BigDecimal.ONE.subtract(k)));
        }
        return ema;
    }

    public static HashMap<String, BigDecimal> macd(final List<BigDecimal> list, final int shortPeriod, final int longPeriod, int midPeriod) {
        HashMap<String, BigDecimal> data = new HashMap<>();
        List<BigDecimal> diffList = new ArrayList<>();
        BigDecimal shortEMA, longEMA, dea, dif = BigDecimal.ZERO;
        for (int i = list.size() - 1; i >= 0; i--) {
            List<BigDecimal> sublist = list.subList(0, list.size() - i);
            shortEMA = ema(sublist, shortPeriod);
            longEMA = ema(sublist, longPeriod);
            dif = shortEMA.subtract(longEMA);
            diffList.add(dif);
        }
        dea = ema(diffList, midPeriod);
        data.put(KEY_DIF, dif.setScale(list.get(0).scale(), BigDecimal.ROUND_HALF_UP));
        data.put(KEY_DEA, dea.setScale(list.get(0).scale(), BigDecimal.ROUND_HALF_UP));
        data.put(KEY_MACD, dif.subtract(dea).multiply(new BigDecimal("2")).setScale(list.get(0).scale(), BigDecimal.ROUND_HALF_UP));
        return data;
    }
}