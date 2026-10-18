package com.example.ClaimShieldX.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Map;

/** JSON in and out of the JSONB columns of the analysis tables. */
public final class Json {
    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize JSON", e);
        }
    }

    public static Map<String, Object> map(String json) {
        try {
            return json == null ? Map.of() : MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read JSON", e);
        }
    }

    public static List<Object> list(String json) {
        try {
            return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<List<Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read JSON", e);
        }
    }

    public static List<String> strings(String json) {
        try {
            return json == null ? List.of() : MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read JSON", e);
        }
    }
}
