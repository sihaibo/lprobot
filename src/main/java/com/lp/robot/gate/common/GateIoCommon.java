package com.lp.robot.gate.common;

import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lp.robot.dextools.entity.TradeOrder;
import com.lp.robot.dextools.enums.TradeOrderStatusEnum;
import com.lp.robot.dextools.enums.TradeOrderTypeEnum;
import com.lp.robot.dextools.enums.TradeOrderVersion;
import com.lp.robot.dextools.service.ConfigService;
import com.lp.robot.dextools.service.TradeOrderService;
import com.lp.robot.gate.obj.Candlestick2;
import com.lp.robot.gate.obj.MarketInfoObj;
import com.lp.robot.gate.obj.TickersObj;
import io.gate.gateapi.ApiClient;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.SpotPricePutOrder;
import io.gate.gateapi.models.SpotPricePutOrder.AccountEnum;
import io.gate.gateapi.models.SpotPricePutOrder.SideEnum;
import io.gate.gateapi.models.SpotPriceTrigger;
import io.gate.gateapi.models.SpotPriceTrigger.RuleEnum;
import io.gate.gateapi.models.SpotPriceTriggeredOrder;
import io.gate.gateapi.models.TriggerOrderResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 功能描述: <br/>
 * gate.io 接口封装
 * @author HaiBo
 * @date: 2022-02-08 18:21<br/>
 * @since JDK 1.8
 */
@Component
@Slf4j
public class GateIoCommon {

    @Autowired
    private ConfigService configService;

    @Autowired
    private TradeOrderService tradeOrderService;

    private SpotApi spotApi;

    @Value("${gate.io.secret}")
    private String secret;
    @Value("${gate.io.key}")
    private String key;

    // 用不到V4接口了
    // @PostConstruct
    private void init() {
        // Initialize API client
        ApiClient client = new ApiClient("", "");
        spotApi = new SpotApi(client);
    }

    private boolean mock() {
        final String mock = configService.getByKey("gate.mock", "false");
        return Boolean.parseBoolean(mock);
    }

    public TickersObj getTicker(String symbol) {
        String res;
        try {
            res = HttpUtilManager.getInstance().doGet("https://data.gateapi.io/api2/1/ticker/" + symbol);
        } catch (Exception e) {
            log.error("gate.io request ticker http exception continue. symbol: {}", symbol, e);
            return null;
        }
        return JSONObject.parseObject(res, TickersObj.class);
    }

    public List<MarketInfoObj> marketInfo() {
        String res;
        try {
            res = HttpUtilManager.getInstance().doGet("https://data.gateapi.io/api2/1/marketinfo");
        } catch (Exception e) {
            log.error("gate.io request marketInfo http exception continue.", e);
            return null;
        }
        final JSONObject resJSON;
        try {
            resJSON = JSON.parseObject(res);
        } catch (Exception e) {
            log.error("gate.io request marketInfo json parse exception continue.", e);
            return null;
        }
        if (!resJSON.getBoolean("result")) {
            log.error("gate.io request marketInfo result failed {}.", res);
            return null;
        }
        List<MarketInfoObj> marketInfoObjs = new ArrayList<>();
        final JSONArray resArray = resJSON.getJSONArray("pairs");
        for (int i = 0; i < resArray.size(); i++) {
            final JSONObject pairJSON = resArray.getJSONObject(i);
            final Iterator<Entry<String, Object>> iterator = pairJSON.entrySet().iterator();
            if (iterator.hasNext()) {
                MarketInfoObj marketInfoObj = new MarketInfoObj();
                final Entry<String, Object> next = iterator.next();
                final JSONObject pairVal = JSON.parseObject(String.valueOf(next.getValue()));
                marketInfoObj.setSymbol(next.getKey());
                marketInfoObj.setPriceDecimalPlaces(pairVal.getIntValue("decimal_places"));
                marketInfoObj.setTotalDecimalPlaces(pairVal.getIntValue("amount_decimal_places"));
                marketInfoObjs.add(marketInfoObj);
            }
        }

        return marketInfoObjs;
    }

    /**
     * @param excludeTickers 排除币种
     * @param volume 交易量
     * 获取所有币种信息并筛选
     * @return List<TickersObj>
     */
    public List<TickersObj> getAllTickers(String excludeTickers, String volume) {
        String res;
        try {
            res = HttpUtilManager.getInstance().doGet("https://data.gateapi.io/api2/1/tickers");
        } catch (Exception e) {
            log.error("gate.io request tickers http exception continue", e);
            return new ArrayList<>();
        }
        JSONObject jsonObject = JSONObject.parseObject(res);
        List<TickersObj> tickersObjs = Collections.synchronizedList(new ArrayList<>());
        // 最小交易量
        final BigDecimal minTradingVolume = new BigDecimal(volume);
        jsonObject.entrySet().parallelStream().forEach(entry -> {
            final String key = entry.getKey();
            // 排除ETF/非USDT交易
            // key.contains("5l") || key.contains("3l") || key.contains("5s") || key.contains("3s")
            if (!key.contains("_usdt")) {
                return;
            }
            // 根据配置排除
            for (String excludeTicker : excludeTickers.split(",")) {
                if (key.contains(excludeTicker)) {
                    return;
                }
            }
            TickersObj tickersObj = JSON.toJavaObject(JSON.parseObject(entry.getValue().toString()), TickersObj.class);
            // 交易量小于最小交易量的就跳过
            if (new BigDecimal(tickersObj.getBaseVolume()).compareTo(minTradingVolume) < 0) {
                return;
            }
            tickersObj.setSymbol(key);
            tickersObjs.add(tickersObj);
        });
        tickersObjs.sort(Comparator.comparing(o -> new BigDecimal(o.getBaseVolume()), Comparator.reverseOrder()));
        return tickersObjs;
    }

    /**
     * 查询币种深度
     * @param symbol
     * @param buy 买入
     * @param volume 买入（U），卖出（币）
     * @return 卖出比例
     */
    public BigDecimal orderBook(String symbol, boolean buy, BigDecimal volume) {
        symbol = symbol.toLowerCase();
        if (!symbol.contains("_usdt")) {
            symbol = symbol + "_usdt";
        }
        // 交易深度
        String result = null;
        try {
            result = HttpUtilManager.getInstance().doGet(String.format("https://data.gateapi.io/api2/1/orderBook/%s", symbol));
        } catch (Exception e) {
            log.error("gate.io request orderBook exception. symbol: {}", symbol, e);
            return BigDecimal.ZERO;
        }

        final JSONObject orderBookJSON = JSON.parseObject(result);
        if (!orderBookJSON.getBoolean("result")) {
            log.warn("gate.io request orderBook failed. symbol: {}, result: {}", symbol, result);
            return BigDecimal.ZERO;
        }
        // asks :卖方深度(0:价格，1：数量) 查询倒数
        // bids :买方深度(0:价格，1：数量) 查询正数
        if (buy) {
            // 买，查询卖深度价格（U/单价=数量）
            final JSONArray asks = orderBookJSON.getJSONArray("asks");
            BigDecimal askTotal = BigDecimal.ZERO;
            for (int i = asks.size() - 2; i >= 0; i--) {
                final JSONArray ask = asks.getJSONArray(i);
                final BigDecimal price = ask.getBigDecimal(0);
                askTotal = askTotal.add(ask.getBigDecimal(1));
                final BigDecimal total = volume.divide(price, 4, BigDecimal.ROUND_DOWN);
                if (askTotal.compareTo(total) > 0) {
                    return price;
                }
            }
        } else {
            final JSONArray bids = orderBookJSON.getJSONArray("bids");
            BigDecimal bidCount = BigDecimal.ZERO;
            for (int i = 1; i < bids.size(); i++) {
                final JSONArray bid = bids.getJSONArray(i);
                final BigDecimal price = bid.getBigDecimal(0);
                bidCount = bidCount.add(bid.getBigDecimal(1));
                if (bidCount.compareTo(volume) > 0) {
                    return price;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 查询K线
     * @param symbol
     * @param groupSec 秒
     * @param rangeHour 时
     * @return
     */
    public List<Candlestick2> candlestick(String symbol, String groupSec, String rangeHour) {
        // 1. 查找K线信息，获取1分钟线就行
        String resLine = null;
        try {
            // time: 时间戳,volume: 交易量,close: 收盘价,high: 最高价,low: 最低价,open: 开盘价
            resLine = HttpUtilManager.getInstance().doGet(String.format("https://data.gateapi.io/api2/1/candlestick2/%s?group_sec=%s&range_hour=%s", symbol, groupSec, rangeHour));
        } catch (Exception e) {
            log.error("gate.io request candlestick http exception continue. symbol: {}, sec:{}, hour:{}", symbol, groupSec, rangeHour, e);
            return new ArrayList<>();
        }
        if (resLine == null) {
            log.error("gate.io request candlestick result is null. symbol:{}, sec:{}, hour:{}", symbol, groupSec, rangeHour);
            return new ArrayList<>();
        }
        List<Candlestick2> resList = Candlestick2.conversion(resLine, symbol);
        if (CollectionUtils.isEmpty(resList)) {
            log.error("gate.io candlestick conversion is empty. symbol:{}, sec:{}, hour:{}", symbol, groupSec, rangeHour);
            return new ArrayList<>();
        }
        return resList;
    }

    /**
     * 查询余额
     * @param symbol USDT/SHIB
     * @return
     */
    public BigDecimal balances(String symbol) {
        return balances0(symbol, "available");
    }

    public BigDecimal balancesForLocked(String symbol) {
        return balances0(symbol, "locked");
    }

    private BigDecimal balances0(String symbol, String key) {
        if (mock()) {
            return new BigDecimal("5");
        }
        String result;
        try {
            result = HttpUtilManager.getInstance().buildKey(this.secret, this.key)
                    .doRequest("post", "https://data.gateapi.io/api2/1/private/balances", new HashMap<>());
        } catch (Exception e) {
            log.error("gate.io request balances http exception continue. symbol: {}", symbol, e);
            return BigDecimal.ZERO;
        }
        final JSONObject resultJSON = JSON.parseObject(result);
        if (!resultJSON.getBoolean("result")) {
            log.error("gate.io request balances result failed. symbol: {}, result:{}", symbol, result);
            return BigDecimal.ZERO;
        }
        final JSONObject availableJSON = resultJSON.getJSONObject(key);
        return availableJSON.getBigDecimal(symbol.contains("_") ? symbol.split("_")[0].toUpperCase() : symbol.toUpperCase());
    }

    /**
     * 买单
     * @param symbol 标识
     * @param rate 现价
     * @param volume 数量(U)，计算币种
     */
    public TradeOrder buy(String symbol, BigDecimal rate, BigDecimal volume) {
        Map<String, String> params = new HashMap<>();
        params.put("currencyPair", symbol);
        params.put("rate", rate.toString());
        params.put("amount", volume.divide(rate, 4, BigDecimal.ROUND_DOWN).toString());
        TradeOrder tradeOrder = new TradeOrder();

        if (mock()) {
            tradeOrder.setTradeNumber(new BigDecimal(params.get("amount")));
            tradeOrder.setOrderNumber(RandomUtil.randomNumbers(8));
            return tradeOrder;
        }

        String result;
        try {
            result = HttpUtilManager.getInstance().buildKey(this.secret, this.key)
                    .doRequest("post", "https://data.gateapi.io/api2/1/private/buy", params);
        } catch (Exception e) {
            log.error("gate.io request buy http exception continue. symbol: {}", symbol, e);
            tradeOrder.setErrorMsg("buy http exception");
            return tradeOrder;
        }
        log.info("gate.io request buy symbol:{} params:{} result:{}", symbol, params, result);
        final JSONObject resultJSON = JSON.parseObject(result);
        if (!resultJSON.getBoolean("result")) {
            log.error("gate.io request buy failed continue. symbol: {}, result:{}", symbol, result);
            tradeOrder.setErrorMsg(resultJSON.getString("message"));
            return tradeOrder;
        }
        tradeOrder.setTradeNumber(new BigDecimal(params.get("amount")));
        tradeOrder.setOrderNumber(resultJSON.getString("orderNumber"));
        return tradeOrder;
    }

    /**
     * 卖单
     * @param symbol 标识
     * @param rate 现价
     * @param volume 数量
     */
    public TradeOrder sell(String symbol, BigDecimal rate, BigDecimal volume) {
        Map<String, String> params = new HashMap<>();
        params.put("currencyPair", symbol);
        params.put("rate", rate.toPlainString());
        params.put("amount", volume.toString());
        TradeOrder tradeOrder = new TradeOrder();

        if (mock()) {
            tradeOrder.setOrderNumber(RandomUtil.randomNumbers(8));
            return tradeOrder;
        }

        String result;
        try {
            result = HttpUtilManager.getInstance().buildKey(this.secret, this.key)
                    .doRequest("post", "https://data.gateapi.io/api2/1/private/sell", params);
        } catch (Exception e) {
            log.error("gate.io request sell http exception continue. symbol: {}", symbol, e);
            tradeOrder.setErrorMsg("sell http exception");
            return tradeOrder;
        }
        log.info("gate.io request sell symbol:{} params:{}. result:{}", symbol, params, result);
        final JSONObject resultJSON = JSON.parseObject(result);
        if (!resultJSON.getBoolean("result")) {
            log.error("gate.io request sell failed continue. symbol: {}, result:{}", symbol, result);
            tradeOrder.setErrorMsg(resultJSON.getString("message"));
            return tradeOrder;
        }
        tradeOrder.setOrderNumber(resultJSON.getString("orderNumber"));
        return tradeOrder;
    }
    /**
     * 触发单_卖单
     * @param symbol 标识
     * @param rate 现价
     * @param volume 数量
     */
    public TradeOrder sellTriggeredOrder(String symbol, BigDecimal rate, BigDecimal volume) {

        SpotPriceTriggeredOrder triggeredOrder = new SpotPriceTriggeredOrder();
        //币种
        triggeredOrder.setMarket(symbol.toUpperCase());
        // 触发条件
        SpotPriceTrigger trigger = new SpotPriceTrigger();
        // / 触发价格
        trigger.setPrice(rate.toPlainString());
        // / 触发条件
        trigger.setRule(RuleEnum.LESS_THAN_OR_EQUAL_TO);
        // / 有效期，单位秒
        trigger.setExpiration(60 * 60 * 24);
        triggeredOrder.setTrigger(trigger);
        // 委托条件
        SpotPricePutOrder putOrder = new SpotPricePutOrder();
        putOrder.setAccount(AccountEnum.NORMAL);
        putOrder.setSide(SideEnum.SELL);
        putOrder.setPrice(rate.multiply(new BigDecimal("0.995")).setScale(rate.scale(), BigDecimal.ROUND_DOWN).toPlainString());
        putOrder.setAmount(volume.setScale(3, BigDecimal.ROUND_DOWN).toPlainString());
        triggeredOrder.setPut(putOrder);

        TradeOrder tradeOrder = new TradeOrder();

        if (mock()) {
            tradeOrder.setOrderNumber(RandomUtil.randomNumbers(8));
            return tradeOrder;
        }

        TriggerOrderResponse result;
        try {
            log.info("gate.io request sell v4 triggeredOrder:{}", JSON.toJSONString(triggeredOrder));
            result = spotApi.createSpotPriceTriggeredOrder(triggeredOrder);
        } catch (ApiException e) {
            log.error("gate.io request sell v4 http exception continue. triggeredOrder:{}", JSON.toJSONString(triggeredOrder), e);
            tradeOrder.setErrorMsg("sell v4 http exception");
            return tradeOrder;
        }
        if (Objects.isNull(result.getId())) {
            tradeOrder.setErrorMsg("sell v4 result is null.");
            return tradeOrder;
        }
        tradeOrder.setOrderNumber(Long.toString(result.getId()));
        return tradeOrder;
    }

    /**
     * 取消订单
     * @param symbol
     * @param orderNumber
     */
    public Boolean cancel(String symbol, String orderNumber) {

        if (mock()) {
            return true;
        }

        Map<String, String> params = new HashMap<>();
        params.put("currencyPair", symbol);
        params.put("orderNumber", orderNumber);
        String result;
        try {
            result = HttpUtilManager.getInstance().buildKey(this.secret, this.key)
                    .doRequest("post", "https://data.gateapi.io/api2/1/private/cancelOrder", params);
        } catch (Exception e) {
            log.error("gate.io request cancel http exception continue. symbol: {}", symbol, e);
            return false;
        }
        log.info("gate.io request cancel result:{}, params:{}", result, params);
        return JSON.parseObject(result).getBoolean("result");
    }

    /**
     * 取消订单_触发单
     * @param orderNumber
     */
    public Boolean cancelTriggeredOrder(String symbol, String orderNumber) {
        if (mock()) {
            return true;
        }
        // 1. 先查询触发单状态，可能触发单成功了，这时候需要取消订单
        final TradeOrder tradeOrder = getTriggeredOrder(symbol, orderNumber);
        // 触发单并且是挂单中的，可以直接删除触发单
        if (TradeOrderVersion.V2.equals(tradeOrder.getTradeOrderVersion())) {
            if (TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus())) {
                return cancel(symbol, orderNumber);
            } else {
                return true;
            }
        } else {
            if (TradeOrderStatusEnum.OPEN.equals(tradeOrder.getTradeOrderStatus())) {
                try {
                    final SpotPriceTriggeredOrder triggeredOrder = spotApi.cancelSpotPriceTriggeredOrder(orderNumber);
                    log.info("gate.io cancel v4 result:{}", JSON.toJSONString(triggeredOrder));
                    return "canceled".equals(triggeredOrder.getStatus());
                } catch (ApiException e) {
                    log.error("gate.io request cancel v4 http exception continue. orderNumber: {}", orderNumber, e);
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    /**
     * 查询订单状态
     * @param symbol
     * @param orderNumber
     * @return
     */
    public TradeOrder getOrder(String symbol, String orderNumber) {
        Map<String, String> params = new HashMap<>();
        params.put("currencyPair", symbol);
        params.put("orderNumber", orderNumber);
        TradeOrder tradeOrder = new TradeOrder();
        if (mock()) {
            return getMockOrder(symbol, orderNumber, tradeOrder);
        }
        String result;
        try {
            result = HttpUtilManager.getInstance().buildKey(this.secret, this.key)
                    .doRequest("post", "https://data.gateapi.io/api2/1/private/getOrder", params);
        } catch (Exception e) {
            log.error("gate.io request get order http exception continue. symbol: {}", symbol, e);
            return tradeOrder;
        }
        log.info("gate.io request get order result:{}", result);
        final JSONObject resultJSON = JSON.parseObject(result);
        if (!resultJSON.getBoolean("result") && resultJSON.getIntValue("code") != 17) {
            log.error("gate.io request get order failed. symbol: {}, result:{}", symbol, result);
            return tradeOrder;
        }
        if (resultJSON.getIntValue("code") == 17 && resultJSON.getString("message").contains("cancelled")) {
            tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.CANCELLED);
            return tradeOrder;
        }
        final JSONObject orderJSON = resultJSON.getJSONObject("order");
        tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.valueOf(orderJSON.getString("status").toUpperCase()));
        tradeOrder.setFilledPrice(orderJSON.getBigDecimal("filledRate"));
        if ("USDT".equals(orderJSON.getString("feeCurrency"))) {
            tradeOrder.setTradeNumber(orderJSON.getBigDecimal("filledAmount"));
            tradeOrder.setToU(tradeOrder.getTradeNumber().multiply(tradeOrder.getFilledPrice()).subtract(orderJSON.getBigDecimal("feeValue")));
        } else {
            tradeOrder.setTradeNumber(orderJSON.getBigDecimal("filledAmount").subtract(orderJSON.getBigDecimal("feeValue")));
            tradeOrder.setToU(tradeOrder.getTradeNumber().multiply(tradeOrder.getFilledPrice()));
        }
        return tradeOrder;
    }

    private TradeOrder getMockOrder(String symbol, String orderNumber, TradeOrder tradeOrder) {
        // 查询当前订单挂单价格，根据实时价格判断是否成功
        final TradeOrder order = tradeOrderService.getByOrderNumber(orderNumber);
        BigDecimal last = new BigDecimal(getTicker(symbol).getLast());
        // 买单，下单金额大于最新金额，买入成功
        // 卖单，下单金额小于最新金额，卖出成功
        if (TradeOrderTypeEnum.BUY.equals(order.getTradeOrderType()) && order.getPrice().compareTo(last) > 0) {
            tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.CLOSED);
        } else if (TradeOrderTypeEnum.SELL.equals(order.getTradeOrderType()) && order.getPrice().compareTo(last) < 0) {
            tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.CLOSED);
        } else {
            tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.OPEN);
        }
        return tradeOrder;
    }

    /**
     * 查询触发单_触发单成功后查询订单状态
     * @param orderNumber
     * @return
     */
    public TradeOrder getTriggeredOrder(String symbol, String orderNumber) {

        TradeOrder tradeOrder = new TradeOrder();
        tradeOrder.setTradeOrderVersion(TradeOrderVersion.V4);
        if (mock()) {
            return getMockOrder(symbol, orderNumber, tradeOrder);
        }
        try {
            final SpotPriceTriggeredOrder triggeredOrder = spotApi.getSpotPriceTriggeredOrder(orderNumber);

            log.info("gate.io get order v4 result:{}", JSON.toJSONString(triggeredOrder));
            final String status = Objects.isNull(triggeredOrder.getStatus()) ? "" : triggeredOrder.getStatus();
            switch (status) {
                case "open":
                    tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.OPEN);
                    break;
                case "finish":
                    if (Objects.isNull(triggeredOrder.getFiredOrderId())) {
                        break;
                    }
                    tradeOrder = getOrder(symbol, Long.toString(triggeredOrder.getFiredOrderId()));
                    // 设置订单ID和版本，取消订单的时候会用到
                    tradeOrder.setOrderNumber(Long.toString(triggeredOrder.getFiredOrderId()));
                    tradeOrder.setPrice(new BigDecimal(triggeredOrder.getPut().getPrice()));
                    tradeOrder.setTradeOrderVersion(TradeOrderVersion.V2);
                    break;
                case "failed":
                case "expired":
                case "cancelled":
                case "canceled":
                    tradeOrder.setTradeOrderStatus(TradeOrderStatusEnum.CANCELLED);
                    break;
            }
            return tradeOrder;
        } catch (ApiException e) {
            log.error("gate.io request cancel v4 http exception continue. orderNumber: {}", orderNumber, e);
            return tradeOrder;
        }
    }
}