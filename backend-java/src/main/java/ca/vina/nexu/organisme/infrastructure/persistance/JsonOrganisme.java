package ca.vina.nexu.organisme.infrastructure.persistance;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Les colonnes JSON du contexte (pays, destinataires) : des tableaux de textes, écrits comme {@code JSON.stringify}. */
final class JsonOrganisme {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TEXTES = new TypeReference<>() { };

    private JsonOrganisme() {
    }

    static String ecrire(List<String> v) {
        return JSON.writeValueAsString(v);
    }

    static List<String> textes(String json) {
        return json == null || json.isBlank() ? List.of() : JSON.readValue(json, TEXTES);
    }
}
