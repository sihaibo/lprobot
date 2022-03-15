package com.lp.robot.gate.common;

import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.MdResultObj;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 功能描述: <br/>
 * MD计算
 * @author HaiBo
 * @date: 2022-03-14 10:08<br/>
 * @since JDK 1.8
 */
public class MdCalculate {

    public static MdResultObj execute(List<Candlestick2> candlestick, int base, int index) {
        MdResultObj result = new MdResultObj();
        result.setIndex(index);
        result.setBase(base);
        // 根据时间倒序
        candlestick.sort(Comparator.comparing(Candlestick2::getTime, Comparator.reverseOrder()));
        LimitedList<Candlestick2> limitedList = new LimitedList<>(index);
        for (int i = 1; i < index + 2; i++) {
            limitedList.add(candlestick.get(i));
            if (limitedList.size() < index) {
                continue;
            }
            if (i == index) {
                result.setCurrent(limitedList.stream()
                        .map(Candlestick2::getClose)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(String.valueOf(index)), candlestick.get(0).getClose().scale(), BigDecimal.ROUND_DOWN));
            }
            if (i == index + 1) {
                result.setPrevious(limitedList.stream()
                        .map(Candlestick2::getClose)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(String.valueOf(index)), candlestick.get(0).getClose().scale(), BigDecimal.ROUND_DOWN));
            }
        }
        return result;
    }

}