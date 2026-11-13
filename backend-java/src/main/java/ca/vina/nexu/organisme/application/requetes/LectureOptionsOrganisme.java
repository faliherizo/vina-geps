package ca.vina.nexu.organisme.application.requetes;

import ca.vina.nexu.organisme.domaine.AlerteConsignee;
import ca.vina.nexu.organisme.domaine.CharteAppliquee;
import ca.vina.nexu.organisme.domaine.ChartesPort;
import ca.vina.nexu.organisme.domaine.JournalAlertesPort;
import ca.vina.nexu.organisme.domaine.PaysAutorisesPort;
import ca.vina.nexu.organisme.domaine.RegleAlerte;
import ca.vina.nexu.organisme.domaine.ReglesAlertePort;
import ca.vina.nexu.organisme.domaine.ReglagesPays;

import java.util.List;
import java.util.Optional;

/**
 * Les lectures des options de l'organisme (lot R11). La charte se lit par tout membre de l'organisme —
 * l'interface l'applique à tous ; le reste, par son administration (la garde HTTP en décide).
 */
public class LectureOptionsOrganisme {

    /** Le journal rend les 200 dernières alertes, comme le Node : de quoi enquêter, pas de quoi archiver. */
    static final int LIMITE_JOURNAL = 200;

    private final ChartesPort chartes;
    private final PaysAutorisesPort pays;
    private final ReglesAlertePort regles;
    private final JournalAlertesPort journal;
    private final ReglagesPays reglages;

    public LectureOptionsOrganisme(ChartesPort chartes, PaysAutorisesPort pays, ReglesAlertePort regles, JournalAlertesPort journal, ReglagesPays reglages) {
        this.chartes = chartes;
        this.pays = pays;
        this.regles = regles;
        this.journal = journal;
        this.reglages = reglages;
    }

    /** Les pays autorisés, et si la passerelle transmet le pays des connexions. */
    public record PaysVue(List<String> pays, boolean enteteConfiguree) {
    }

    /** La charte et ses variables CSS ; vide si l'organisme n'en a pas posé. */
    public Optional<CharteAppliquee> charte(String tenantId) {
        return chartes.lire(tenantId).map(CharteAppliquee::de);
    }

    public PaysVue pays(String tenantId) {
        return new PaysVue(pays.lire(tenantId), reglages.estConfigure());
    }

    public List<RegleAlerte> regles(String tenantId) {
        return regles.lister(tenantId);
    }

    public List<AlerteConsignee> journal(String tenantId) {
        return journal.lire(tenantId, LIMITE_JOURNAL);
    }
}
