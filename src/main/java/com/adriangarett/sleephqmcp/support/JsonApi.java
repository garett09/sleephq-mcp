package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal helpers for navigating SleepHQ's JSON:API responses.
 * SleepHQ wraps payloads as {@code { "data": { "id", "type", "attributes": {...}, "relationships": {...} } }}.
 * Collection endpoints wrap as {@code { "data": [ ... ], "meta": {...} }}.
 */
public final class JsonApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> RESOURCE_KEYS = Set.of(
            "id", "type", "attributes", "relationships", "links", "meta");

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
        return singleResourceData(node).path("attributes");
    }

    /**
     * Normalizes SleepHQ JSON:API quirks for a single {@code machine_date} (and similar resources):
     * <ul>
     *   <li>{@code data} as a one-element array (some find-by-date responses)</li>
     *   <li>summary fields on {@code data} instead of under {@code attributes}</li>
     *   <li>{@code attributes: null} or missing {@code relationships}</li>
     * </ul>
     *
     * @throws IllegalArgumentException when {@code data} is missing, empty, or not a resource object
     */
    /**
     * Whether {@code document} has a non-empty single-resource {@code data} payload (object or one-element array).
     */
    public static boolean hasSingleResourceData(JsonNode document) {
        JsonNode data = document.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return false;
        }
        if (data.isArray()) {
            return !data.isEmpty();
        }
        return data.isObject();
    }

    public static ObjectNode singleResourceData(JsonNode document) {
        JsonNode data = document.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalArgumentException("JSON:API document missing data");
        }
        ObjectNode resource;
        if (data.isArray()) {
            if (data.isEmpty()) {
                throw new IllegalArgumentException("JSON:API data array is empty");
            }
            if (data.size() > 1) {
                throw new IllegalArgumentException(
                        "JSON:API data array has " + data.size() + " resources; expected one");
            }
            resource = (ObjectNode) data.get(0).deepCopy();
        } else if (data.isObject()) {
            resource = (ObjectNode) data.deepCopy();
        } else {
            throw new IllegalArgumentException("JSON:API data is not an object or array");
        }
        return ensureResourceShape(resource);
    }

    private static ObjectNode ensureResourceShape(ObjectNode resource) {
        JsonNode attrs = resource.path("attributes");
        if (attrs.isObject()) {
            ensureRelationships(resource);
            return resource;
        }
        ObjectNode attributes = MAPPER.createObjectNode();
        List<String> hoistKeys = new ArrayList<>();
        resource.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!RESOURCE_KEYS.contains(key)) {
                attributes.set(key, entry.getValue().deepCopy());
                hoistKeys.add(key);
            }
        });
        hoistKeys.forEach(resource::remove);
        resource.set("attributes", attributes);
        ensureRelationships(resource);
        return resource;
    }

    private static void ensureRelationships(ObjectNode resource) {
        if (!resource.path("relationships").isObject()) {
            resource.set("relationships", MAPPER.createObjectNode());
        }
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
