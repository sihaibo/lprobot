package com.lp.robot.gate.common;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-02-28 14:46<br/>
 * @since JDK 1.8
 */
public class CacheSingleton {

    public static final String KEY_MA = "KEY_MA";

    /**
     * 上次MA5值
     */
    public static final String KEY_MA5 = "KEY_MA5";


    /**
     * 上次收盘价
     */
    public static final String KEY_LAST = "KEY_LAST";

    /**
     * 买单金额
     */
    public static final String KEY_BUY_PRICE = "KEY_BUY_PRICE";

    /**
     * 整点涨幅，整点开盘价
     */
    public static final String KEY_BUY_INCR = "KEY_BUY_INCR";

    /**
     * 整点涨幅，最大价格
     */
    public static final String KEY_BUY_INCR_MAX_PRICE = "KEY_BUY_INCR_MAX_PRICE";

    /**
     * 整点涨幅，卖单订单号
     */
    public static final String KEY_BUY_INCR_ORDER_NUMBER = "KEY_BUY_INCR_ORDER_NUMBER";

    private Map<String, ConcurrentHashMap<String, BigDecimal>> cache = new ConcurrentHashMap<>();

    private static class CacheSingletonInstance{
        private static final CacheSingleton instance = new CacheSingleton();
    }

    private CacheSingleton() {}

    public static CacheSingleton getInstance() {
        return CacheSingletonInstance.instance;
    }

    public void put(String key, String vKey, BigDecimal vVal) {
        if (Objects.isNull(cache.get(key))) {
            cache.put(key, new ConcurrentHashMap<>());
        }
        cache.get(key).put(vKey, vVal);
    }

    public ConcurrentHashMap<String, BigDecimal> get(String key) {
        if (Objects.isNull(cache.get(key))) {
            cache.put(key, new ConcurrentHashMap<>());
        }
        return cache.get(key);
    }

    public BigDecimal get(String key, String vKey) {
        return get(key).get(vKey);
    }

    public void remove(String key, String vKey) {
        get(key).remove(vKey);
    }

    public void remove(String key) {
        cache.remove(key);
    }

}