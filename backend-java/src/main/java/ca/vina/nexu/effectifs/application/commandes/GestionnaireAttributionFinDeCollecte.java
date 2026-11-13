package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * L'attribution de fin de collecte (P-GH-4) — jumeau de {@code attribuerDepuisDisponibilites}. En confection, chaque
 * place vacante d'un quart en mode automatisé va au premier candidat que les règles acceptent ; une place sans
 * candidat reste vacante et ira à la gestion régulière. Un refus (règle, conflit) fait passer au suivant ; un
 * incident, lui, remonte : rien n'est tu.
 */
public class GestionnaireAttributionFinDeCollecte {

    /** Le nom sous lequel le moteur attribue et journalise — jamais le planificateur qui l'a déclenché. */
    public static final String MECANISME = "attribution-fin-de-collecte";

    /** Le bilan d'un passage. */
    public record Bilan(int attribuees, int restees) { }

    private final LectureDisponibilites.Lectures lire;
    private final LectureDisponibilites disponibilites;
    private final GestionnairePlaces places;
    private final JournalEffectifsPort journal;

    public GestionnaireAttributionFinDeCollecte(LectureDisponibilites.Lectures lire, LectureDisponibilites disponibilites, GestionnairePlaces places,
                                                JournalEffectifsPort journal) {
        this.lire = lire;
        this.disponibilites = disponibilites;
        this.places = places;
        this.journal = journal;
    }

    public Bilan attribuer(Contexte ctx, String periodeId) {
        Periode p = lire.periodes().exiger(ctx, periodeId, Perimetres.Niveau.PLANIFICATION);
        if (p.etat() != Planification.EtatPeriode.CONFECTION) {
            throw new EffectifsInvalideException("l'attribution depuis les disponibilités se fait une fois la collecte fermée, en confection");
        }
        List<String> vacantes = lire.equipages().lister(ctx, periodeId, null, null).stream()
                .filter(e -> e.modeAttribution() == Planification.ModeAttribution.AUTOMATISE)
                .flatMap(e -> e.places().stream()).filter(x -> x.ressourceId() == null).map(Equipage.Place::id).toList();
        int attribuees = 0;
        for (String placeId : vacantes) {
            attribuees += pourvoir(ctx, placeId) ? 1 : 0;
        }
        Bilan bilan = new Bilan(attribuees, vacantes.size() - attribuees);
        journal.ecrire(ctx, new JournalEffectifs.Consignation("periode.attribution.disponibilites", "periode", periodeId, p.uniteCode(), null,
                Map.of("attribuees", bilan.attribuees(), "restees", bilan.restees()), MECANISME));
        return bilan;
    }

    private boolean pourvoir(Contexte ctx, String placeId) {
        return attribuerAuPrioritaire(ctx, placeId, MECANISME).isPresent();
    }

    /**
     * Donne une place vacante au premier candidat du classement que les règles {@code ATTRIBUTION} acceptent (P-GN-1,
     * P-GH-4), sous le nom d'un mécanisme ; rend le retenu. Un refus fait passer au suivant ; un incident remonte.
     */
    public Optional<Disponibilites.Candidature> attribuerAuPrioritaire(Contexte ctx, String placeId, String mecanisme) {
        for (Disponibilites.Candidature c : disponibilites.candidats(ctx, placeId).admissibles()) {
            try {
                places.attribuer(ctx, placeId, Map.of("ressourceId", c.ressourceId()), mecanisme);
                return Optional.of(c);
            } catch (ConflitException refus) {
                // Un refus des règles ou un chevauchement écarte ce candidat : le suivant est essayé.
            }
        }
        return Optional.empty();
    }
}
