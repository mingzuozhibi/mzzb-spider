package mingzuozhibi.discspider;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class JmsHelper {

    @Value("${spring.application.name}")
    private String moduleName;

    @Autowired
    private JmsTemplate template;

    public void sendAddr(String moduleAddr) {
        JsonObject root = new JsonObject();
        root.addProperty("name", moduleName);
        root.addProperty("addr", moduleAddr);
        jmsSend("module.connect", root.toString());
    }

    public void sendInfo(String message) {
        sendModuleMsg("info", message);
        log.info("JMS -> {} {}", "module.message", message);
    }

    public void sendWarn(String message) {
        sendModuleMsg("warn", message);
        log.warn("JMS -> {} {}", "module.message", message);
    }

    private void sendModuleMsg(String type, String message) {
        JsonObject root = new JsonObject();
        root.addProperty("name", moduleName);
        root.add("data", buildData(type, message));
        jmsSend("module.message", root.toString());
    }

    private JsonObject buildData(String type, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("type", type);
        data.addProperty("text", message);
        data.addProperty("createOn", Instant.now().toEpochMilli());
        return data;
    }

    private void jmsSend(String destinationName, String message) {
        template.convertAndSend(destinationName, message);
        log.info("JMS -> {} {}", destinationName, message);
    }

}
