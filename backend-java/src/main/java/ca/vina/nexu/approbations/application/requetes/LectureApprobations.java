package ca.vina.nexu.approbations.application.requetes;

import ca.vina.nexu.approbations.domaine.Approbations;
import ca.vina.nexu.approbations.domaine.Delegataires;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ce que l'écran des approbations lit : règles, demandes, une demande, les délégations, la demande d'un document. */
public final class LectureApprobations {

    private static final String DESTINATAIRE = "toUserId";

    private final Approbations.Depot depot;
    private final Delegataires.Comptes comptes;

    public LectureApprobations(Approbations.Depot depot, Delegataires.Comptes comptes) {
        this.depot = depot;
        this.comptes = comptes;
    }

    public List<Map<String, Object>> regles(String tenantId, String documentType) {
        return depot.regles(tenantId, documentType).stream().map(Approbations.Regle::vue).toList();
    }

    public List<Map<String, Object>> demandes(String tenantId, String statut) {
        return depot.demandes(tenantId, statut);
    }

    public Map<String, Object> demande(String tenantId, String id) {
        return depot.demande(tenantId, id).orElseThrow(() -> new IntrouvableException("approval request not found"));
    }

    public List<Map<String, Object>> delegations(String tenantId, String userId, String mine) {
        return depot.delegations(tenantId, "1".equals(mine) ? userId : null).stream().map(d -> nommee(tenantId, d)).toList();
    }

    /**
     * Le <strong>nom</strong> accompagne l'identifiant, juste après lui : une liste qui n'affiche que
     * {@code usr-1788…} rend à l'écran le défaut que la suggestion corrige à la saisie.
     */
    private Map<String, Object> nommee(String tenantId, Map<String, Object> d) {
        String vers = String.valueOf(d.get(DESTINATAIRE));
        String nom = comptes.destinataire(tenantId, vers).map(Delegataires.Destinataire::nom).orElse(vers);
        Map<String, Object> m = new LinkedHashMap<>();
        d.forEach((cle, valeur) -> {
            m.put(cle, valeur);
            if (DESTINATAIRE.equals(cle)) {
                m.put("toNom", nom);
            }
        });
        return m;
    }

    public Map<String, Object> parDocument(String tenantId, String documentType, String documentId) {
        return depot.demandeDuDocument(tenantId, documentType == null ? "" : documentType, documentId == null ? "" : documentId)
                .orElseThrow(() -> new IntrouvableException("no approval request for this document"));
    }
}
