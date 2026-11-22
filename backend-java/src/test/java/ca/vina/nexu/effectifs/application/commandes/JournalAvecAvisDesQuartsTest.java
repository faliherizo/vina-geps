package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort.EntreeJournal;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** AD-06c (addenda 5 b) : chaque écriture du journal qui touche un quart publié l'annonce, une fois par quart et par geste. */
@DisplayName("Avis des quarts modifiés : le journal annonce")
class JournalAvecAvisDesQuartsTest {

    private static final Contexte CTX = new Contexte("t-1", "u-1", "admin", "2026-12-01T11:40:00.000Z");
    private static final EchangesApi.Quart PUBLIE = new EchangesApi.Quart("eq-1", "per", "U1", "2026-12-01", 420, 1140, "JOUR", "m", List.of());

    private final List<JournalEffectifs.Consignation> ecrites = new ArrayList<>();
    private final List<String> lus = new ArrayList<>();
    private final List<List<Object>> emis = new ArrayList<>();

    /** Seul eq-1 est publié ; pl-1 et pl-2 sont à lui, pl-conf à un quart en confection. */
    private final EchangesApiPort quarts = new EchangesApiPort() {
        @Override public List<EchangesApi.Quart> quartsExposes(String t, EchangesApi.Fenetre f) { return List.of(); }
        @Override public Map<String, String> equipagesDesPlaces(String t, Collection<String> ids) {
            lus.add("places:" + ids.stream().sorted().collect(Collectors.joining(",")));
            Map<String, String> toutes = Map.of("pl-1", "eq-1", "pl-2", "eq-1", "pl-conf", "eq-conf");
            return toutes.entrySet().stream().filter(e -> ids.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        @Override public Map<String, EchangesApi.Quart> quartsExposesParId(String t, Collection<String> ids) {
            lus.add("quarts:" + ids.stream().sorted().collect(Collectors.joining(",")));
            return ids.contains("eq-1") ? Map.of("eq-1", PUBLIE) : Map.of();
        }
        @Override public List<EntreeJournal> journalQuartsApres(String t, String c, int l) { return List.of(); }
        @Override public List<EchangesApi.Releve> relevesApres(String t, String c, int l) { return List.of(); }
    };

    private final JournalEffectifsPort interne = new JournalEffectifsPort() {
        @Override public void ecrire(Contexte ctx, JournalEffectifs.Consignation c) { ecrites.add(c); }
        @Override public List<JournalEffectifs.Entree> lire(String t, JournalEffectifs.Filtre f, Set<String> u, int l) {
            lus.add("lire:" + l);
            return List.of();
        }
    };

    private final JournalAvecAvisDesQuarts journal = new JournalAvecAvisDesQuarts(interne, quarts, (t, evenement, donnees, cle, instant) -> {
        emis.add(List.of(t, evenement, donnees, cle, instant));
        return 1;
    });

    @Test
    @DisplayName("deux places d'un même quart rendues vacantes : écrit d'abord, puis un avis mince, sa clé coalesce le geste")
    void placesVacantes() {
        JournalEffectifs.Consignation c = JournalEffectifs.Consignation.de("places.vacantes", "absence", "a-1", "U1", null, Map.of("places", List.of("pl-1", "pl-2")));
        journal.ecrire(CTX, c);
        assertThat(ecrites).containsExactly(c);
        assertThat(lus).containsExactly("places:pl-1,pl-2", "quarts:eq-1");
        assertThat(emis).hasSize(1);
        assertThat(emis.getFirst()).containsExactly("t-1", JournalAvecAvisDesQuarts.EVENEMENT,
                Map.of("quartId", "eq-1", "date", "2026-12-01", "uniteCode", "U1", "action", "places.vacantes", "horodatage", CTX.maintenant(),
                        "lire", JournalAvecAvisDesQuarts.LECTURE),
                "eq-1@" + CTX.maintenant(), CTX.maintenant());
    }

    @Test
    @DisplayName("un équipage se lit sans passer par les places ; un quart en confection n'est pas annoncé")
    void equipageEtConfection() {
        journal.ecrire(CTX, JournalEffectifs.Consignation.de("equipage.modifie", "equipage", "eq-1", "U1", null, null));
        assertThat(lus).containsExactly("quarts:eq-1");
        assertThat(emis).hasSize(1);
        journal.ecrire(CTX, JournalEffectifs.Consignation.de("place.attribuee", "place", "pl-conf", "U1", null, null));
        assertThat(emis).hasSize(1);
    }

    @Test
    @DisplayName("une écriture étrangère aux quarts n'interroge rien ; la lecture passe au journal")
    void horsQuart() {
        journal.ecrire(CTX, JournalEffectifs.Consignation.de("ressource.soldes", "ressource", "r-1", "U1", null, Map.of()));
        assertThat(ecrites).hasSize(1);
        assertThat(lus).isEmpty();
        assertThat(emis).isEmpty();
        assertThat(journal.lire("t-1", null, null, 7)).isEmpty();
        assertThat(lus).containsExactly("lire:7");
    }
}
