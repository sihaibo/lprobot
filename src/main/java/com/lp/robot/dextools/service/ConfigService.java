package com.lp.robot.dextools.service;

/**
 * 功能描述:CurrencyTradeService <br/>
 *
 * @author HaiBo
 * @since JDK 1.8
 */
public interface ConfigService {

    String getByKey(String key, String defaultValue);

    int getStrategyNumber(String strategyName);
}