package com.mingzuozhibi;

import com.google.gson.Gson;
import com.mingzuozhibi.commons.gson.GsonFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MzzbDiscSpiderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MzzbDiscSpiderApplication.class, args);
    }

    @Bean
    public Gson gson() {
        return GsonFactory.createGson();
    }

}
