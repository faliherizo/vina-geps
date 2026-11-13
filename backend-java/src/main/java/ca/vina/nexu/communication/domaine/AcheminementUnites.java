package ca.vina.nexu.communication.domaine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * <b>L'acheminement réglé par unité.</b> Une unité dit par quels canaux, dans quel ordre et sur quel
 * mode joindre ses membres ; une unité sans réglage retombe sur le défaut de l'établissement — le
 * courriel, en sonnerie. Le réglage est celui de l'unité de la personne : une ressource d'effectifs
 * n'appartient qu'à une unité.
 */
public final class AcheminementUnites {

    /** Au-delà, la chaîne d'escalade ne se lit plus : six canaux suffisent à épuiser le répertoire. */
    public static final int ETAPES_MAX = CanalReception.values().length;

    public static final AcheminementPort.Canal DEFAUT = new AcheminementPort.Canal(CanalReception.COURRIEL.code(), ModeReception.SONNERIE.code(), 1, true);

    private AcheminementUnites() {
    }

    public record Etape(CanalReception canal, ModeReception mode) {
        Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canal", canal.code());
            m.put("mode", mode.code());
            return m;
        }
    }

    public record Reglage(String uniteId, List<Etape> etapes, String modifieLe, String modifiePar) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uniteId", uniteId);
            m.put("etapes", etapes.stream().map(Etape::enCarte).toList());
            m.put("modifieLe", modifieLe);
            m.put("modifiePar", modifiePar);
            return m;
        }
    }

    /** Ce qui empêche d'enregistrer les étapes : aucune, trop, ou un canal répété. */
    public static List<String> valider(List<Etape> etapes) {
        List<String> erreurs = new ArrayList<>();
        if (etapes.isEmpty()) {
            erreurs.add("au moins un canal est requis");
        }
        if (etapes.size() > ETAPES_MAX) {
            erreurs.add("au plus " + ETAPES_MAX + " canaux");
        }
        Set<CanalReception> vus = new HashSet<>();
        if (etapes.stream().anyMatch(e -> !vus.add(e.canal()))) {
            erreurs.add("un canal est répété");
        }
        return erreurs;
    }

    /** Les canaux résolus, dans l'ordre d'escalade : ceux du réglage, ou le défaut. */
    public static List<AcheminementPort.Canal> canaux(Optional<Reglage> reglage) {
        return reglage.map(r -> IntStream.range(0, r.etapes().size())
                        .mapToObj(i -> new AcheminementPort.Canal(r.etapes().get(i).canal().code(), r.etapes().get(i).mode().code(), i + 1, false))
                        .toList())
                .orElse(List.of(DEFAUT));
    }
}
