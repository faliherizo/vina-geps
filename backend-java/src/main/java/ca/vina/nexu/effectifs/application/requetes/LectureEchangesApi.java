package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Les lectures de l'API d'échange (AD-06), pour une application de l'organisme : quarts, correctifs, relevés. */
public class LectureEchangesApi {

    private final EchangesApiPort port;

    public LectureEchangesApi(EchangesApiPort port) {
        this.port = port;
    }

    public List<EchangesApi.Quart> quarts(String tenantId, EchangesApi.Fenetre fenetre) {
        return port.quartsExposes(tenantId, fenetre);
    }

    /**
     * Les correctifs après le curseur, chacun avec l'état présent de son quart ; une entrée qui touche plusieurs places
     * (absence, échange) rend un correctif par place. Le curseur avance sur toutes les entrées
     * lues, même celles d'un quart non exposé : sinon une période en confection bloquerait la lecture indéfiniment.
     */
    public EchangesApi.Page<EchangesApi.Modification> modifications(String tenantId, String curseur, int limite) {
        List<DonneesIntegrationPort.EntreeJournal> entrees = port.journalQuartsApres(tenantId, curseur, limite);
        if (entrees.isEmpty()) {
            return new EchangesApi.Page<>(List.of(), curseur);
        }
        // Trois requêtes par page, quel que soit son volume : places → équipages, puis quarts et places des équipages.
        // La lecture entrée par entrée en faisait deux à trois chacune, soit jusqu'à 1 500 pour une page de 500.
        Map<DonneesIntegrationPort.EntreeJournal, List<EchangesApi.Cible>> cibles = new LinkedHashMap<>();
        entrees.forEach(e -> cibles.put(e, EchangesApi.cibles(e.action(), e.objetType(), e.objetId(), e.apres())));
        Map<String, String> equipageDePlace = port.equipagesDesPlaces(tenantId, cibles.values().stream().flatMap(List::stream)
                .filter(c -> EchangesApi.OBJET_PLACE.equals(c.objetType())).map(EchangesApi.Cible::objetId).collect(Collectors.toSet()));
        Map<String, EchangesApi.Quart> quarts = port.quartsExposesParId(tenantId, cibles.values().stream().flatMap(List::stream)
                .map(c -> equipageDe(c, equipageDePlace)).flatMap(Optional::stream).collect(Collectors.toSet()));
        List<EchangesApi.Modification> modifications = cibles.entrySet().stream()
                .flatMap(en -> en.getValue().stream().flatMap(c -> equipageDe(c, equipageDePlace).map(quarts::get).stream()
                        .map(q -> new EchangesApi.Modification(IntegrationsEffectifs.curseurDe(en.getKey()), en.getKey().horodatage(), en.getKey().action(),
                                c.objetType(), c.objetId(), q))))
                .toList();
        return new EchangesApi.Page<>(modifications, IntegrationsEffectifs.curseurDe(entrees.getLast()));
    }

    private static Optional<String> equipageDe(EchangesApi.Cible c, Map<String, String> equipageDePlace) {
        return EchangesApi.OBJET_PLACE.equals(c.objetType()) ? Optional.ofNullable(equipageDePlace.get(c.objetId())) : Optional.of(c.objetId());
    }

    public EchangesApi.Page<EchangesApi.Releve> releves(String tenantId, String curseur, int limite) {
        List<EchangesApi.Releve> releves = port.relevesApres(tenantId, curseur, limite);
        return new EchangesApi.Page<>(releves, releves.isEmpty() ? curseur : releves.getLast().curseur());
    }
}
