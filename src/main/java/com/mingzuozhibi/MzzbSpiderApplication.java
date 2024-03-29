package com.mingzuozhibi;

import com.mingzuozhibi.commons.amqp.AmqpSender;
import com.mingzuozhibi.commons.base.BaseKeys.Name;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableAutoConfiguration(exclude = {
    JacksonAutoConfiguration.class,
})
public class MzzbSpiderApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(MzzbSpiderApplication.class, args);
        context.getBean(AmqpSender.class).bind(Name.SERVER_CORE)
            .notify("MzzbSpiderApplication已启动");
    }
}
