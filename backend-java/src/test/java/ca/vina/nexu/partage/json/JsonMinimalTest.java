package ca.vina.nexu.partage.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonMinimal — le même texte que JSON.stringify")
class JsonMinimalTest {

    @Test
    void ecritLesTypesDansLOrdre() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "a\"b\\c\nd\re\tf" + (char) 1 + "g");
        m.put("n", 3);
        m.put("l", 4L);
        m.put("d", 2.5);
        m.put("e", 3.0);
        m.put("b", true);
        m.put("z", null);
        m.put("t", List.of(1, "x", Map.of()));
        m.put("o", new StringBuilder("obj"));
        assertThat(JsonMinimal.ecrire(m)).isEqualTo("{\"s\":\"a\\\"b\\\\c\\nd\\re\\tf\\u0001g\",\"n\":3,\"l\":4,\"d\":2.5,\"e\":3,\"b\":true,\"z\":null,\"t\":[1,\"x\",{}],\"o\":\"obj\"}");
        assertThat(JsonMinimal.ecrire(1e16)).isEqualTo("1.0E16");
        assertThat(JsonMinimal.ecrire(List.of())).isEqualTo("[]");
    }
}
