package com.mingzuozhibi.commons.ztemp;

import com.google.gson.JsonObject;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.mylog.JmsEnums.Name;
import com.mingzuozhibi.commons.mylog.JmsLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.*;
import java.util.Enumeration;
import java.util.Optional;

@Slf4j
@Component
public class ConnectBean extends BaseSupport implements CommandLineRunner {

    @Value("${server.port}")
    private int port;

    @Value("${spring.application.name}")
    private String name;

    private String addr;

    private JmsLogger bind;

    @PostConstruct
    public void bind() {
        bind = jmsSender.bind(Name.SPIDER_CONTENT);
    }

    public void run(String... args) {
        Optional<String> hostAddress = getHostAddress();
        if (hostAddress.isPresent()) {
            addr = hostAddress.get() + ":" + port;
            bind.success("应用已启动 => " + addr);
            jmsSendConnect();
        } else {
            bind.error("Can't get network address");
        }
    }

    public void jmsSendConnect() {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        root.addProperty("addr", addr);
        String json = root.toString();
        jmsSender.send("module.connect", json);
    }

    @Scheduled(cron = "0 0/10 * * * ?")
    public void autoRunTask() {
        if (addr != null) {
            jmsSendConnect();
            log.debug("JMS -> module.connect name={}, addr={}", name, addr);
        }
    }

    private Optional<String> getHostAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (isNotLocalAddress(address) && isIPv4Address(address)) {
                        return Optional.of(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Can't get network address", e);
        }
        log.warn("Can't get network address, no error");
        return Optional.empty();
    }

    private boolean isNotLocalAddress(InetAddress address) {
        return !address.isLoopbackAddress()
            && !address.isAnyLocalAddress()
            && !address.isLinkLocalAddress()
            && !address.isMulticastAddress();
    }

    private boolean isIPv4Address(InetAddress address) {
        return address.getHostAddress().matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }

}
