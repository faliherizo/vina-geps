package ca.vina.nexu.partage.json.infrastructure;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/** Lire et écrire le JSON des colonnes ({@code *_json}) : une carte ordonnée, une liste, un texte — Jackson confiné à l'infrastructure. */
public final class Json {

    private Json() {
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final ObjectMapper PRESENTE = JsonMapper.builder().enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT).build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };
    private static final TypeReference<List<Object>> LISTE = new TypeReference<>() { };

    public static Map<String, Object> carte(String json) {
        return json == null || json.isBlank() ? Map.of() : MAPPER.readValue(json, CARTE);
    }

    public static List<Object> liste(String json) {
        return json == null || json.isBlank() ? List.of() : MAPPER.readValue(json, LISTE);
    }

    public static String texte(Object valeur) {
        return MAPPER.writeValueAsString(valeur);
    }

    /** Indenté à deux espaces, comme {@code JSON.stringify(v, null, 2)}. */
    public static String textePresente(Object valeur) {
        return PRESENTE.writeValueAsString(valeur);
    }
}
