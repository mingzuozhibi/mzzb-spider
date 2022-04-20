package com.mingzuozhibi.commons.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;

import static com.mingzuozhibi.utils.MyTimeUtils.ofEpochMilli;
import static com.mingzuozhibi.utils.MyTimeUtils.toEpochMilli;

public class AdapterOfLocalDateTime extends TypeAdapter<LocalDateTime> {

    @Override
    public void write(JsonWriter out, LocalDateTime value) throws IOException {
        if (value != null) {
            out.value(toEpochMilli(value));
        } else {
            out.nullValue();
        }
    }

    @Override
    public LocalDateTime read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        } else {
            return ofEpochMilli(in.nextLong());
        }
    }

}