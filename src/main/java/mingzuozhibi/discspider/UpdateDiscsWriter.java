package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class UpdateDiscsWriter {

    private Gson gson = new Gson();

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    public void writeUpdateDiscs(List<String> updatedDiscs, boolean fullUpdate) {
        writeHistory(updatedDiscs);

        cleanPrevDiscs(fullUpdate);

        writeThisDiscs(updatedDiscs);
    }

    private void writeHistory(List<String> updatedDiscs) {
        JsonObject history = new JsonObject();
        history.addProperty("date", LocalDateTime.now().toString());
        history.add("data", gson.toJsonTree(updatedDiscs));
        listOpts.leftPush("history.update.discs", history.toString());
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
