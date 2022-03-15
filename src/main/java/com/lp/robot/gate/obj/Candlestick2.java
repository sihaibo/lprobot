package com.lp.robot.gate.obj;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2021-07-09 18:15<br/>
 * @since JDK 1.8
 */
@Data
@Slf4j
public class Candlestick2 {

    // time: 时间戳,volume: 交易量,close: 收盘价,high: 最高价,low: 最低价,open: 开盘价

    // 时间戳
    private long time;
    // 交易量
    private BigDecimal volume;
    // 收盘价
    private BigDecimal close;
    // 最高价
    private BigDecimal high;
    // 最低价
    private BigDecimal low;
    // 开盘价
    private BigDecimal open;


    /**
     * 字符串转换
     * @param res 请求结果
     * @return 集合
     */
    public static List<Candlestick2> conversion(String res, String symbol) {
        JSONObject jsonObject;
        try {
            jsonObject = JSONObject.parseObject(res);
        } catch (Exception e) {
            log.error("Candlestick2 conversion error. symbol:{}, res:{}", symbol, res);
            return Collections.emptyList();
        }
        Boolean resBol = jsonObject.getBoolean("result");
        if (Objects.isNull(resBol) || !resBol) {
            log.warn("Candlestick2 conversion warn. symbol:{}, res:{}", symbol, res);
            return Collections.emptyList();
        }
        List<Candlestick2> result = new ArrayList<>();
        JSONArray dataArray = jsonObject.getJSONArray("data");
        for (Object o : dataArray) {
            JSONArray data = JSONArray.parseArray(o.toString());
            Candlestick2 candlestick2 = new Candlestick2();
            candlestick2.setTime(data.getLongValue(0));
            candlestick2.setVolume(data.getBigDecimal(1));
            candlestick2.setClose(data.getBigDecimal(2));
            candlestick2.setHigh(data.getBigDecimal(3));
            candlestick2.setLow(data.getBigDecimal(4));
            candlestick2.setOpen(data.getBigDecimal(5));
            result.add(candlestick2);
        }
        return result;
    }
}