package ca.vina.nexu.rapports.domaine;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Le port des sources de faits calculés</b> (P-RF-1) — jumeau de {@code backend/src/rapports/ports.ts}.
 *
 * <p>Le constructeur de rapports ne sait pas calculer une heure supplémentaire ni un statut
 * opérationnel : c'est le contexte qui possède ces règles qui fournit les faits. Le port est défini
 * <b>ici</b>, côté rapports ; les contextes l'implémentent. La dépendance va donc des effectifs vers les
 * rapports, jamais l'inverse.
 */
public interface FournisseurFaits {

    /** Qui demande le rapport : un fournisseur en tire le périmètre que le seul locataire ne dit pas. */
    record Demandeur(String tenantId, String acteurId, String role, String maintenant) {
    }

    /** Les codes de source du catalogue que ce fournisseur sert. */
    Set<String> sources();

    /** Les faits d'une source, <b>déjà bornés</b> au périmètre du demandeur et à la période. */
    List<Map<String, Object>> faits(String source, Demandeur demandeur, CatalogueRapports.Periode periode);
}
