package com.mingzuozhibi.content;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DateResult {
    public DateResult(List<DiscContent> result) {
        this.date = LocalDateTime.now();
        this.result = result;
    }

    private LocalDateTime date;
    private List<DiscContent> result;

    public int count() {
        return result == null ? 0 : result.size();
    }
}
