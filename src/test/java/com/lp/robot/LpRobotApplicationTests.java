package com.lp.robot;


import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LpRobotApplicationTests {

    public static void main(String[] args) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Long.parseLong("1627611184") * 1000));
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Long.parseLong("1628307840000")));
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Long.parseLong("1628307900000")));

        System.out.println(LocalDateTime
                .ofInstant(Instant.ofEpochMilli(Long.parseLong("1627611184") * 1000), ZoneOffset.of("+8")));

    }

    @Test
    void contextLoads() {
    }

}
