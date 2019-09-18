package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mingzuozhibi.common.gson.GsonFactory;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UpdateDiscsWriter {

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    private Gson gson = GsonFactory.createGson();

    public void writeUpdateDiscs(Map<String, DiscParser> discInfos, boolean fullUpdate) {
        List<String> updatedDiscs = discInfos.values().stream()
            .map(gson::toJson)
            .collect(Collectors.toList());

        writeHistory(updatedDiscs);

        cleanPrevDiscs(fullUpdate);

        writeThisDiscs(updatedDiscs);
    }

    private void writeHistory(List<String> updatedDiscs) {
        JsonObject history = new JsonObject();
        history.addProperty("date", LocalDateTime.now().toString());
        history.add("updatedDiscs", gson.toJsonTree(updatedDiscs));
        listOpts.leftPush("history.update.discs", history.toString());
        listOpts.trim("history.update.discs", 0, 99);
    }

    private void cleanPrevDiscs(boolean fullUpdate) {
        if (fullUpdate) {
            listOpts.trim("done.update.discs", 1, 0);
            listOpts.trim("prev.update.discs", 1, 0);
        } else {
            listOpts.trim("prev.update.discs", 1, 0);
        }
    }

    private void writeThisDiscs(List<String> updatedDiscs) {
        listOpts.rightPushAll("done.update.discs", updatedDiscs);
        listOpts.rightPushAll("prev.update.discs", updatedDiscs);
    }

}
