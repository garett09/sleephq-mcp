package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Minimal helpers for navigating SleepHQ's JSON:API responses.
 * SleepHQ wraps payloads as {@code { "data": { "id", "type", "attributes": {...}, "relationships": {...} } }}.
 * Collection endpoints wrap as {@code { "data": [ ... ], "meta": {...} }}.
 */
public final class JsonApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonApi() {
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    public static JsonNode attributes(JsonNode node) {
        JsonNode data = node.path("data");
        if (data.isArray()) {
            throw new IllegalArgumentException("Expected single resource envelope, got collection");
        }
        return data.path("attributes");
    }

    public static String id(JsonNode node) {
        return node.path("data").path("id").asText(null);
    }

    public static List<JsonNode> collection(JsonNode node) {
        JsonNode data = node.path("data");
        if (!data.isArray()) {
            throw new IllegalArgumentException("Expected collection envelope, got single resource");
        }
        return data.findValues("attributes");
    }

    public static String toJsonString(Map<String, ?> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Finds a JSON:API resource in {@code data[]} by {@code id} and returns a single-resource document
     * {@code { "data": &lt;item&gt;, "meta": ... }}.
     *
     * @throws IllegalArgumentException if envelope is not a collection or id is absent
     */
    public static String toSingleResourceJsonFromCollection(String collectionJson, String resourceId) {
        JsonNode root = parse(collectionJson);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IllegalArgumentException("Expected JSON:API collection (data array)");
        }
        for (JsonNode item : data) {
            if (resourceId.equals(item.path("id").asText())) {
                ObjectNode out = MAPPER.createObjectNode();
                out.set("data", item.deepCopy());
                if (root.has("meta")) {
                    out.set("meta", root.get("meta").deepCopy());
                }
                try {
                    return MAPPER.writeValueAsString(out);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalArgumentException("No resource with id " + resourceId);
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
