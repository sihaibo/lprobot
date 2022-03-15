package com.lp.robot;

import com.lp.robot.gate.common.WeChatNoticeService;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 功能描述: <br/>
 *
 * @author HaiBo
 * @date: 2022-03-15 15:48<br/>
 * @since JDK 1.8
 */
@SpringBootTest(classes = {LpRobotApplication.class})
@RunWith(SpringRunner.class)
public class WxNoticeTest {

    @Autowired
    private WeChatNoticeService weChatNoticeService;

    @SneakyThrows
    @Test
    public void notice() {
        weChatNoticeService.notice("通知测试...");
        Thread.currentThread().join();
    }

}