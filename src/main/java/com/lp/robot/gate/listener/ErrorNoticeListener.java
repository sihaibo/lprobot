package com.lp.robot.gate.listener;

import com.lp.robot.gate.common.WeChatNoticeService;
import com.lp.robot.gate.event.ErrorEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-01 16:28<br/>
 * @since JDK 1.8
 */
@Component
public class ErrorNoticeListener implements ApplicationListener<ErrorEvent> {

    @Autowired
    private WeChatNoticeService weChatNoticeService;

    @Async
    @Override
    public void onApplicationEvent(ErrorEvent event) {
        StringBuffer buffer = new StringBuffer("订单异常通知。\n");
        buffer.append("币种：").append(event.getSource().toString()).append("\n");
        buffer.append("描述：").append(event.getMsg()).append("\n");
        buffer.append("时间：").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append("\n");
        weChatNoticeService.notice(buffer.toString());
    }
}