package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort.EntreeJournal;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AD-06 : une page de correctifs se lit en trois appels au port, quelle que soit sa taille. */
@DisplayName("Correctifs de l'API d'échange : lecture groupée")
class LectureEchangesApiTest {

    private static final EchangesApi.Quart Q1 = new EchangesApi.Quart("eq-1", "p", "U1", "2026-12-01", 420, 1140, "JOUR", "m", List.of());

    @Test
    @DisplayName("place disparue ou quart non exposé : l'entrée est sautée, le curseur avance ; trois appels au port")
    void groupee() {
        List<String> appels = new ArrayList<>();
        EchangesApiPort port = new EchangesApiPort() {
            @Override public List<EchangesApi.Quart> quartsExposes(String t, EchangesApi.Fenetre f) { return List.of(); }
            @Override public Map<String, String> equipagesDesPlaces(String t, Collection<String> ids) {
                appels.add("places:" + ids.size());
                return Map.of("pl-1", "eq-1");
            }
            @Override public Map<String, EchangesApi.Quart> quartsExposesParId(String t, Collection<String> ids) {
                appels.add("quarts:" + ids.size());
                return Map.of("eq-1", Q1);
            }
            @Override public List<EntreeJournal> journalQuartsApres(String t, String c, int l) {
                appels.add("journal");
                return List.of(new EntreeJournal("j1", "2026-11-20T10:00:00.000Z", "equipage.cree", "equipage", "eq-1", "U1", null),
                        new EntreeJournal("j2", "2026-11-20T11:00:00.000Z", "place.attribuee", "place", "pl-1", "U1", null),
                        new EntreeJournal("j3", "2026-11-20T12:00:00.000Z", "place.liberee", "place", "pl-supprimee", "U1", null),
                        new EntreeJournal("j4", "2026-11-20T13:00:00.000Z", "equipage.modifie", "equipage", "eq-confection", "U1", null));
            }
            @Override public List<EchangesApi.Releve> relevesApres(String t, String c, int l) { return List.of(); }
        };
        EchangesApi.Page<EchangesApi.Modification> page = new LectureEchangesApi(port).modifications("t-1", null, 100);
        assertThat(page.elements()).extracting(EchangesApi.Modification::objetId).containsExactly("eq-1", "pl-1");
        assertThat(page.elements()).allSatisfy(m -> assertThat(m.quart()).isEqualTo(Q1));
        assertThat(page.curseur()).isEqualTo("2026-11-20T13:00:00.000Zj4");
        assertThat(appels).containsExactly("journal", "places:2", "quarts:2");
    }

    @Test
    @DisplayName("AD-06c : une absence ou un échange rendent un correctif par place, sous le curseur de leur entrée")
    void plusieursPlaces() {
        List<Collection<String>> demandes = new ArrayList<>();
        EchangesApiPort port = new EchangesApiPort() {
            @Override public List<EchangesApi.Quart> quartsExposes(String t, EchangesApi.Fenetre f) { return List.of(); }
            @Override public Map<String, String> equipagesDesPlaces(String t, Collection<String> ids) {
                demandes.add(ids);
                return Map.of("pl-1", "eq-1", "pl-2", "eq-1");
            }
            @Override public Map<String, EchangesApi.Quart> quartsExposesParId(String t, Collection<String> ids) { return Map.of("eq-1", Q1); }
            @Override public List<EntreeJournal> journalQuartsApres(String t, String c, int l) {
                return List.of(new EntreeJournal("j1", "2026-11-20T10:00:00.000Z", "places.vacantes", "absence", "a-1", "U1", Map.of("places", List.of("pl-1", "pl-2"))),
                        new EntreeJournal("j2", "2026-11-20T11:00:00.000Z", "echange.applique", "echange", "x-1", "U1", Map.of("pl-2", "r-9")));
            }
            @Override public List<EchangesApi.Releve> relevesApres(String t, String c, int l) { return List.of(); }
        };
        EchangesApi.Page<EchangesApi.Modification> page = new LectureEchangesApi(port).modifications("t-1", null, 100);
        assertThat(page.elements()).extracting(EchangesApi.Modification::curseur, EchangesApi.Modification::action, EchangesApi.Modification::objetType,
                EchangesApi.Modification::objetId).containsExactly(
                org.assertj.core.groups.Tuple.tuple("2026-11-20T10:00:00.000Zj1", "places.vacantes", "place", "pl-1"),
                org.assertj.core.groups.Tuple.tuple("2026-11-20T10:00:00.000Zj1", "places.vacantes", "place", "pl-2"),
                org.assertj.core.groups.Tuple.tuple("2026-11-20T11:00:00.000Zj2", "echange.applique", "place", "pl-2"));
        assertThat(demandes).hasSize(1);
        assertThat(demandes.getFirst()).containsExactlyInAnyOrder("pl-1", "pl-2");
    }
}
