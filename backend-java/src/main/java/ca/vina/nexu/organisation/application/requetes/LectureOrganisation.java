package ca.vina.nexu.organisation.application.requetes;

import ca.vina.nexu.organisation.domaine.DepotOrganisationPort;
import ca.vina.nexu.organisation.domaine.Organisation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Les lectures du contexte {@code organisation} — Java nu, sans Spring. */
public class LectureOrganisation {

    private final DepotOrganisationPort depot;
    private final Clock horloge;

    public LectureOrganisation(DepotOrganisationPort depot, Clock horloge) {
        this.depot = depot;
        this.horloge = horloge;
    }

    public String aujourdhui() {
        return LocalDate.now(horloge).toString();
    }

    public List<Organisation.VersionUnite> versions(String tenantId) {
        return depot.lister(tenantId);
    }

    /**
     * <strong>L'organigramme à une date</strong> (exigence 10).
     *
     * <p>Sans date, c'est aujourd'hui. Avec, c'est l'organigramme de ce jour-là — y compris les
     * rattachements de l'époque, et c'est toute la différence pour une reddition de comptes.
     */
    public List<Organisation.VersionDecrite> organigramme(String tenantId, String le) {
        return Organisation.organigrammeLe(depot.lister(tenantId), le == null || le.isBlank() ? aujourdhui() : le);
    }

    public List<Organisation.VersionUnite> historiqueUnite(String tenantId, String uniteId) {
        return Organisation.historique(depot.lister(tenantId), uniteId);
    }

    public Organisation.Filiation filiationVersion(String tenantId, String versionId) {
        List<Organisation.VersionUnite> toutes = depot.lister(tenantId);
        if (toutes.stream().noneMatch(v -> v.id().equals(versionId))) {
            throw new DepotOrganisationPort.IntrouvableException("version", versionId);
        }
        return Organisation.filiation(toutes, versionId);
    }
}
