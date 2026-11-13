package ca.vina.nexu.approbations.application.commandes;

import ca.vina.nexu.approbations.domaine.Approbations;
import ca.vina.nexu.approbations.domaine.Delegataires;

import java.util.Map;

/** Les écritures du moteur d'approbation : règle, demande, approbateur additionnel, décision, délégation, relance. */
public final class GestionnaireApprobations {

    private final Approbations.Depot depot;
    private final Approbations.CatalogueTypes catalogue;
    private final Delegataires.Comptes comptes;

    public GestionnaireApprobations(Approbations.Depot depot, Approbations.CatalogueTypes catalogue, Delegataires.Comptes comptes) {
        this.depot = depot;
        this.catalogue = catalogue;
        this.comptes = comptes;
    }

    public Map<String, Object> creerRegle(String tenantId, Map<String, Object> corps) {
        Object niveau = corps.get("level");
        Integer level = niveau instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) ? n.intValue() : null;
        Approbations.Saisie saisie = new Approbations.Saisie(texte(corps, "documentType"), texte(corps, "field"), texte(corps, "operator"),
                corps.get("value"), level, texte(corps, "approverRole"), texte(corps, "approverId"));
        return depot.creerRegle(tenantId, Approbations.validerRegle(saisie, code -> catalogue.actif(tenantId, code)), corps.get("value"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> creerDemande(String tenantId, String demandeurParDefaut, Map<String, Object> corps) {
        Map<String, Object> contexte = corps.get("context") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String demandeur = texte(corps, "requestedBy") != null ? texte(corps, "requestedBy") : demandeurParDefaut;
        return depot.creerDemande(tenantId, texte(corps, "documentType"), texte(corps, "documentId"), contexte, demandeur);
    }

    public Map<String, Object> ajouterApprobateur(String tenantId, String demandeId, Map<String, Object> corps) {
        return depot.ajouterApprobateur(tenantId, demandeId, texte(corps, "approverId"));
    }

    public Map<String, Object> decider(String tenantId, String etapeId, Map<String, Object> corps, String acteurId, String role) {
        return depot.decider(tenantId, etapeId, texte(corps, "decision"), acteurId, role);
    }

    public Map<String, Object> deleguer(String tenantId, String deUserId, Map<String, Object> corps) {
        Approbations.Delegation d = Approbations.validerDelegation(deUserId, texte(corps, "toUserId"), texte(corps, "fromDate"), texte(corps, "toDate"));
        Delegataires.verifier(tenantId, comptes.destinataire(tenantId, d.toUserId()));
        return depot.deleguer(tenantId, d);
    }

    public Map<String, Object> relancer(String tenantId, Map<String, Object> corps) {
        return depot.relancer(tenantId, texte(corps, "documentType") == null ? "" : texte(corps, "documentType"),
                texte(corps, "documentId") == null ? "" : texte(corps, "documentId"), nombre(corps.get("oldAmount")), nombre(corps.get("newAmount")),
                corps.get("tolerancePct") == null ? null : nombre(corps.get("tolerancePct")));
    }

    private static String texte(Map<String, Object> c, String cle) {
        Object v = c.get(cle);
        return v == null ? null : String.valueOf(v);
    }

    /** {@code Number(x)} du Node : NaN pour ce qui n'est pas un nombre — la validation du domaine s'en charge. */
    private static Double nombre(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null ? Double.NaN : Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
