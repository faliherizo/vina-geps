package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;

import java.util.List;

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
     * Les correctifs après le curseur, chacun avec l'état présent de son quart. Le curseur avance sur toutes les entrées
     * lues, même celles d'un quart non exposé : sinon une période en confection bloquerait la lecture indéfiniment.
     */
    public EchangesApi.Page<EchangesApi.Modification> modifications(String tenantId, String curseur, int limite) {
        List<DonneesIntegrationPort.EntreeJournal> entrees = port.journalQuartsApres(tenantId, curseur, limite);
        if (entrees.isEmpty()) {
            return new EchangesApi.Page<>(List.of(), curseur);
        }
        List<EchangesApi.Modification> modifications = entrees.stream()
                .flatMap(e -> port.quartExpose(tenantId, e.objetType(), e.objetId()).stream()
                        .map(q -> new EchangesApi.Modification(IntegrationsEffectifs.curseurDe(e), e.horodatage(), e.action(), e.objetType(), e.objetId(), q)))
                .toList();
        return new EchangesApi.Page<>(modifications, IntegrationsEffectifs.curseurDe(entrees.getLast()));
    }

    public EchangesApi.Page<EchangesApi.Releve> releves(String tenantId, String curseur, int limite) {
        List<EchangesApi.Releve> releves = port.relevesApres(tenantId, curseur, limite);
        return new EchangesApi.Page<>(releves, releves.isEmpty() ? curseur : releves.getLast().curseur());
    }
}
