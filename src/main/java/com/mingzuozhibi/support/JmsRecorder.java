package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.logger.Logger;
import lombok.Getter;

import java.io.PrintWriter;
import java.io.StringWriter;

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
        bind.notify("更新%s：共%d个".formatted(this.taskName, this.taskSize));
    }

    public void jmsEndUpdate() {
        bind.success("更新%s：结束".formatted(this.taskName));
    }

    public boolean checkBreakCount(int maxBreakCount) {
        if (this.breakCount >= maxBreakCount) {
            bind.warning("更新%s：连续%d次更新失败，任务提前终止".formatted(this.taskName, maxBreakCount));
            return true;
        }
        return false;
    }

    public void jmsStartUpdateRow(String origin) {
        this.fetchCount++;
        bind.info("更新开始：[%s](%s/%d)".formatted(origin, this.fetchCount, this.taskSize));
    }

    public void jmsSuccessRow(String origin, String message) {
        this.breakCount = 0;
        this.doneCount++;
        bind.info("更新成功：[%s][%s]".formatted(origin, message));
    }

    public void jmsFailedRow(String origin, String message) {
        this.breakCount++;
        this.errorCount++;
        bind.warning("数据异常：[%s][%s]".formatted(origin, message));
        bind.warning("更新失败：[%s](%s/%d)".formatted(origin, this.fetchCount, this.taskSize));
    }

    public void jmsErrorRow(String origin, Exception e) {
        this.breakCount++;
        this.errorCount++;
        bind.error(buildStackTrace(e));
        bind.error("遇到错误：[%s][%s]".formatted(origin, e.toString()));
        bind.error("更新失败：[%s](%s/%d)".formatted(origin, this.fetchCount, this.taskSize));
    }

    public void jmsSummary() {
        var skipCount = this.taskSize - this.fetchCount;
        bind.notify("There are %d tasks, %d updates, %d successes, %d failures, and %d skips.".formatted(
            this.taskSize, this.fetchCount, this.doneCount, this.errorCount, skipCount));
    }

    private static String buildStackTrace(Exception e) {
        var out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

}
