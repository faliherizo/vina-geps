package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.approbations.infrastructure.persistance.DepotApprobations;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireFluxApprobations;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePointagesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.RestrictionModificationHeures;
import ca.vina.nexu.effectifs.application.requetes.LectureFluxApprobations;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurFluxApprobations;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotFeuillesTemps;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaTempsEffectifs;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

/** Le câblage, pour les essais, des flux d'approbation des heures (E-GP-6) et de la restriction de leur modification (E-GP-3). */
public final class FluxEssai {

    private FluxEssai() {
    }

    /** Ce que les essais branchent : les lectures et écritures des flux, la restriction, et le moteur générique. */
    public record Cablage(LectureFluxApprobations lecture, GestionnaireFluxApprobations flux, RestrictionModificationHeures restriction, DepotApprobations moteur) {
        public GestionnairePointagesEffectifs.Approbation approbation() {
            return new GestionnairePointagesEffectifs.Approbation(restriction, flux);
        }
    }

    /** Les briques communes aux contextes : la base, l'horloge, les périmètres, le moteur de règles et le journal. */
    public record Socle(JdbcClient jdbc, Clock horloge, LecturePerimetres perimetres, EvaluateurRegles regles, JournalEffectifsPort journal) { }

    public static Cablage cabler(Socle s) {
        DepotApprobations moteur = new DepotApprobations(s.jdbc(), s.horloge(), new DepotComptes(s.jdbc(), s.horloge()), new CourrierJournal());
        AdaptateurComptesPourEffectifs comptes = new AdaptateurComptesPourEffectifs(s.jdbc());
        LectureFluxApprobations lecture = new LectureFluxApprobations(new AdaptateurFluxApprobations(moteur), comptes, s.perimetres());
        DepotFeuillesTemps sources = new DepotFeuillesTemps(s.jdbc(), new SchemaTempsEffectifs(s.jdbc()), new SchemaPresenceEffectifs(s.jdbc()),
                new SchemaPlanification(s.jdbc()));
        return new Cablage(lecture, new GestionnaireFluxApprobations(lecture, comptes, s.journal()), new RestrictionModificationHeures(s.regles(), s.perimetres(), sources),
                moteur);
    }
}
