package ca.vina.nexu.effectifs.infrastructure.persistance;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** La sérialisation des colonnes JSON du contexte (attributs, paramètres, avant / après du journal). */
final class JsonEffectifs {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> CARTE = new TypeReference<>() { };
    private static final TypeReference<List<List<String>>> CYCLE = new TypeReference<>() { };

    private JsonEffectifs() {
    }

    static String ecrire(Object v) {
        return v == null ? null : JSON.writeValueAsString(v);
    }

    /** Un cycle de postes : des semaines de sept jours, chaque jour une plage ou nul. */
    static List<List<String>> cycle(String json) {
        return JSON.readValue(json, CYCLE);
    }

    static Map<String, Object> carte(String json) {
        return json == null || json.isBlank() ? new LinkedHashMap<>() : JSON.readValue(json, CARTE);
    }
}
