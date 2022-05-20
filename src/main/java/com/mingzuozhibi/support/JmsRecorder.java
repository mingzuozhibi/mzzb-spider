package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.amqp.logger.Logger;
import com.mingzuozhibi.commons.domain.Result;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
@Getter
public class JmsRecorder {

    private final Logger bind;
    private final String taskName;
    private final int taskSize;

    private int fetchCount;
    private int errorCount;
    private int breakCount;
    private int doneCount;

    public JmsRecorder(Logger bind, String taskName, int taskSize) {
        this.taskName = taskName;
        this.taskSize = taskSize;
        this.bind = bind;
    }

    public void jmsStartUpdate() {
        bind.notify("更新%s：共%d个", this.taskName, this.taskSize);
    }

    public void jmsEndUpdate() {
        bind.notify("更新%s：结束", this.taskName);
    }

    public boolean checkBreakCount(int maxBreakCount) {
        if (this.breakCount >= maxBreakCount) {
            bind.warning("更新%s：连续%d次更新失败，任务提前终止", this.taskName, maxBreakCount);
            return true;
        }
        return false;
    }

    public void jmsStartUpdateRow(String origin) {
        this.fetchCount++;
        bind.info("更新开始：[%s](%s/%d)", origin, this.fetchCount, this.taskSize);
    }

    public void jmsSuccessRow(String origin, String message) {
        this.breakCount = 0;
        this.doneCount++;
        bind.info("更新成功：[%s][%s]", origin, message);
    }

    public boolean checkUnfinished(String origin, Result<?> result) {
        if (!result.hasError()) {
            return false;
        }
        this.breakCount++;
        this.errorCount++;
        bind.warning("抓取失败：%s", result.getMessage());
        bind.warning("更新失败：[%s](%s/%d)", origin, this.fetchCount, this.taskSize);
        return true;
    }

    public void jmsFailedRow(String origin, String message) {
        this.breakCount++;
        this.errorCount++;
        bind.warning("数据异常：%s", message);
        bind.warning("更新失败：[%s](%s/%d)", origin, this.fetchCount, this.taskSize);
    }

    public void jmsErrorRow(String origin, Exception e) {
        this.breakCount++;
        this.errorCount++;
        bind.error("捕获异常：%s", e.toString());
        bind.error("更新失败：[%s](%s/%d)", origin, this.fetchCount, this.taskSize);
    }

    public void jmsSummary() {
        int skipCount = this.taskSize - this.fetchCount;
        bind.notify("There are %d tasks, %d updates, %d successes, %d failures, and %d skips.",
            this.taskSize, this.fetchCount, this.doneCount, this.errorCount, skipCount);
    }

    public static void writeContent(String content, String origin) {
        try {
            File file = File.createTempFile("spider", ".html");
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                bufferedWriter.write(content);
                bufferedWriter.flush();
                log.warn("An error occurred while parsing the content: [{}][file={}]", origin, file.getAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("An error occurred while recording the error content", e);
        }
    }

}
