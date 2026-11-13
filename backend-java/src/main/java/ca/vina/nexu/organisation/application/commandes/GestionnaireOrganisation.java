package ca.vina.nexu.organisation.application.commandes;

import ca.vina.nexu.organisation.application.requetes.LectureOrganisation;
import ca.vina.nexu.organisation.domaine.DepotOrganisationPort;
import ca.vina.nexu.organisation.domaine.Organisation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Les écritures du contexte {@code organisation} — Java nu, sans Spring. */
public class GestionnaireOrganisation {

    private final DepotOrganisationPort depot;
    private final LectureOrganisation lecture;
    private final Clock horloge;
    private final Supplier<String> identifiants;

    public GestionnaireOrganisation(DepotOrganisationPort depot, LectureOrganisation lecture, Clock horloge,
                                    Supplier<String> identifiants) {
        this.depot = depot;
        this.lecture = lecture;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    private static String texte(Map<String, Object> corps, String cle) {
        Object v = corps.get(cle);
        return v == null ? "" : v.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> liste(Map<String, Object> corps, String cle) {
        Object v = corps.get(cle);
        return v instanceof List<?> l ? (List<Object>) l : List.of();
    }

    /** Ce que le corps HTTP décrit, traduit **une fois** en objets du domaine. */
    public static Organisation.Reorganisation reorganisationDe(Map<String, Object> corps) {
        List<String> sources = new ArrayList<>();
        for (Object s : liste(corps, "sources")) {
            sources.add(s == null ? "" : s.toString());
        }
        List<Organisation.Cible> cibles = new ArrayList<>();
        for (Object c : liste(corps, "cibles")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = c instanceof Map<?, ?> ? (Map<String, Object>) c : Map.of();
            Object parent = m.get("parentVersionId");
            cibles.add(new Organisation.Cible(texte(m, "uniteId"), texte(m, "code"), texte(m, "libelle"),
                    parent == null || parent.toString().isBlank() ? null : parent.toString()));
        }
        return new Organisation.Reorganisation(Organisation.Operation.de(texte(corps, "operation")),
                texte(corps, "effetLe"), sources, cibles, texte(corps, "motif"));
    }

    public record ResultatApplique(String operation, List<String> terminees,
                                   List<Organisation.VersionUnite> creees, List<String> motifs) { }

    /**
     * <strong>Appliquer une réorganisation</strong> (exigence 11).
     *
     * <p>Le domaine décide de la forme et des dates ; le dépôt persiste en une transaction.
     */
    public ResultatApplique appliquer(String tenantId, Map<String, Object> corps, String auteurId) {
        Organisation.Reorganisation demande = reorganisationDe(corps);
        Organisation.ResultatReorganisation resultat =
                Organisation.reorganiser(lecture.versions(tenantId), demande, i -> identifiants.get());
        depot.appliquer(tenantId, resultat, demande.motif(), auteurId, Instant.now(horloge).toString());
        return new ResultatApplique(demande.operation().code(),
                resultat.terminees().stream().map(Organisation.VersionTerminee::id).toList(),
                resultat.creees(), resultat.motifs());
    }
}
