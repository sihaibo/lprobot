package com.lp.robot.dextools.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lp.robot.dextools.dao.ConfigDao;
import com.lp.robot.dextools.entity.Config;
import com.lp.robot.dextools.service.ConfigService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @since JDK 1.8
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    @Autowired
    private ConfigDao configDao;

    @Override
    public String getByKey(String key, String defaultValue) {
        LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Config::getKey, key);
        final Config config = configDao.selectOne(wrapper);
        return Objects.isNull(config) ? defaultValue : config.getVal();
    }

    @Override
    public int getStrategyNumber(String strategyName) {
        final String config = getByKey("strategy.number", "{\"BASE\":\"10\",\"INCR\":\"10\"}");
        JSONObject configJSON = JSON.parseObject(config);
        return configJSON.getIntValue(strategyName);
    }
}