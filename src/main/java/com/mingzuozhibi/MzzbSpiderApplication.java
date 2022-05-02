package com.mingzuozhibi;

import com.mingzuozhibi.commons.mylog.JmsEnums.Name;
import com.mingzuozhibi.commons.mylog.JmsSender;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableAutoConfiguration(exclude = {
    JacksonAutoConfiguration.class,
})
public class MzzbSpiderApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context =
            SpringApplication.run(MzzbSpiderApplication.class, args);
        context.getBean(JmsSender.class).bind(Name.SERVER_CORE)
            .notify("MzzbSpiderApplication已启动");
    }
}
