package com.lp.robot.gate.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lp.robot.gate.event.ErrorEvent;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2021-08-04 14:02<br/>
 * @since JDK 1.8
 */
@Component
@Slf4j
public class WeChatNoticeService {

    private String accessToken;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${wx.corp.id}")
    private String corpId;
    @Value("${wx.secret}")
    private String secret;
    @Value("${wx.to.user}")
    private String toUser;


    @PostConstruct
    private void init() {
        final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(1);
        threadPool.scheduleWithFixedDelay(() -> {
            try {
                String result = getToken();
                JSONObject resultJSON = JSON.parseObject(result);
                int errcode = resultJSON.getIntValue("errcode");
                if (errcode != 0) {
                    log.error("WeChatNoticeService notice getToken error result:{}", result);
                    applicationContext.publishEvent(new ErrorEvent("WeChatToken", result));
                    return;
                }
                this.accessToken = resultJSON.getString("access_token");
            } catch (IOException e) {
                log.error("WeChatNoticeService notice getToken IOException", e);
                applicationContext.publishEvent(new ErrorEvent("WeChatToken", "http io exception"));
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    @Async
    public void notice(String msg) {

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("touser", this.toUser);
        jsonObject.put("msgtype", "text");
        jsonObject.put("agentid", "1000002");

        JSONObject textJsonObject = new JSONObject();
        textJsonObject.put("content", msg);

        jsonObject.put("text", textJsonObject);

        String result;
        try {
            result = HttpUtilManager.getInstance().doPostBody(String.format("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=%s", accessToken), jsonObject.toJSONString());
        } catch (IOException e) {
            log.error("WeChatNoticeService notice IOException", e);
            applicationContext.publishEvent(new ErrorEvent("WeChatSend", "http io exception"));
            return;
        }
        if (result.contains("access_token")) {
            log.error("WeChatNoticeService notice error {}, msg:{}", result, jsonObject.toJSONString());
            applicationContext.publishEvent(new ErrorEvent("WeChatSend", String.format("%s||%s", result, jsonObject.toJSONString())));
        }
    }

    private String getToken() throws IOException {
        String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";
        return HttpUtilManager.getInstance().doGet(String.format(url, this.corpId, this.secret));
    }

}