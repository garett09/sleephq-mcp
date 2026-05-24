package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonApiTest {

    @Test
    void parse_validJson() {
        JsonNode node = JsonApi.parse("{\"a\":1,\"b\":\"x\"}");
        assertThat(node.path("a").asInt()).isEqualTo(1);
        assertThat(node.path("b").asText()).isEqualTo("x");
    }

    @Test
    void attributes_unwrapsSingleResource() {
        JsonNode node = JsonApi.parse("{\"data\":{\"id\":7,\"type\":\"team\",\"attributes\":{\"name\":\"alpha\"}}}");
        JsonNode attrs = JsonApi.attributes(node);
        assertThat(attrs.path("name").asText()).isEqualTo("alpha");
    }

    @Test
    void attributes_throwsForCollection() {
        JsonNode node = JsonApi.parse("{\"data\":[{\"id\":1},{\"id\":2}]}");
        assertThatThrownBy(() -> JsonApi.attributes(node))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toSingleResourceJsonFromCollection_findsById() {
        String collection = "{\"data\":[{\"id\":\"1\",\"type\":\"team\"},{\"id\":\"2\",\"type\":\"team\"}],\"meta\":{\"p\":1}}";
        String single = JsonApi.toSingleResourceJsonFromCollection(collection, "2");
        assertThat(single).contains("\"id\":\"2\"");
        assertThat(single).contains("\"meta\":{\"p\":1}");
    }

    @Test
    void toSingleResourceJsonFromCollection_throwsWhenMissing() {
        String collection = "{\"data\":[{\"id\":\"1\",\"type\":\"team\"}]}";
        assertThatThrownBy(() -> JsonApi.toSingleResourceJsonFromCollection(collection, "99"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void toSingleResourceJsonFromCollection_throwsWhenNotArray() {
        assertThatThrownBy(() -> JsonApi.toSingleResourceJsonFromCollection("{\"data\":{}}", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void id_returnsStringFormOrNull() {
        assertThat(JsonApi.id(JsonApi.parse("{\"data\":{\"id\":42}}"))).isEqualTo("42");
        assertThat(JsonApi.id(JsonApi.parse("{\"data\":{}}"))).isNull();
    }
}
