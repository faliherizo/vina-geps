package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs.ResultatComblement;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotComblementsAbsencesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.Role;

import java.util.List;
import java.util.Optional;

/**
 * <strong>Le comblement des places libérées par une absence</strong> (lot U5 : E-GN-6, P-GN-1, P-GH-3, P-CH-8) —
 * jumeau de {@code comblerPlacesLiberees}, {@code backend/src/effectifs/absences/serviceComblement.ts}.
 * <p>
 * Dès qu'une absence est accordée — par les règles, ou par une autorisation manuelle —, ce mécanisme s'enclenche de
 * lui-même, place par place, selon le paramétrage de l'équipage : attribution d'office au prioritaire (quart
 * automatisé), offre aux admissibles avisés (quart manuel), ou réservation au remplacement long terme (absence
 * longue). Chaque geste est celui du mécanisme, journalisé comme tel.
 * <p>
 * Quand l'organisme offre ses quarts <strong>par ancienneté et à tour de rôle</strong> (AD-07, art. 16.02), la place
 * offerte ouvre une offre en cascade : un rang à la fois, avec son délai de réponse, au lieu d'aviser tous les
 * admissibles ensemble.
 */
public class GestionnaireComblementAbsences {

    /** Le mécanisme qui comble : lui, et non le gestionnaire qui a autorisé l'absence, attribue et avise. */
    public static final String MECANISME = "comblement-sur-absence";
    private static final String URL_QUARTS = "/admin/mesechanges";
    private static final int MINUTES_HEURE = 60;
    private static final int HEURES_JOUR = 24;

    /** Ce que le mécanisme lit. */
    public record Lectures(LectureEquipages equipages, LectureDisponibilites disponibilites, LectureRessources ressources, LectureReferentiels referentiels) { }

    /** Ce que le mécanisme écrit ou déclenche. */
    public record Ecritures(DepotComblementsAbsencesPort depot, GestionnaireAttributionFinDeCollecte attribution, PublicationAvis avis, JournalEffectifsPort journal,
                            Sequence sequence, CascadeOffresQuart cascade) { }

    /** Une place libérée, avec son quart, pour l'avis. */
    private record Quart(String placeId, Equipage equipage) {
        String libelle() {
            return "le " + equipage.date() + " de " + hhmm(equipage.debut()) + " à " + hhmm(equipage.fin()) + " (" + equipage.uniteCode() + ")";
        }
    }

    private final Lectures lire;
    private final Ecritures ecrire;
    private final AvisDeComblement avis;

    public GestionnaireComblementAbsences(Lectures lire, Ecritures ecrire) {
        this.lire = lire;
        this.ecrire = ecrire;
        this.avis = new AvisDeComblement(lire.ressources(), ecrire.depot(), ecrire.avis());
    }

    static String hhmm(int minutes) {
        return String.format("%02d:%02d", minutes / MINUTES_HEURE % HEURES_JOUR, minutes % MINUTES_HEURE);
    }

    /** Le contexte du mécanisme : il lit et attribue sans périmètre ; chaque écriture est signée de son nom. */
    private static Contexte duMecanisme(Contexte ctx) {
        return new Contexte(ctx.tenantId(), ctx.acteurId(), Role.ADMIN.code(), ctx.maintenant());
    }

    /** L'absence est-elle longue : sa durée contre le seuil du code, ou à défaut la catégorie du code, à la date de l'absence ? */
    public boolean dureeLongue(Contexte ctx, AbsenceEffectifs a) {
        return AbsencesEffectifs.estLongTerme(lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.CODES_ABSENCE, a.codeAbsence(), a.du()).attributs(),
                AbsencesEffectifs.joursDemandes(a.du(), a.au()));
    }

    /** Comble les places qu'une absence accordée vient de libérer, aussitôt la vacance écrite. */
    public AbsencesEffectifs.BilanComblement combler(Contexte ctx, AbsenceEffectifs a, List<AbsencesEffectifs.PlaceTenue> places) {
        if (places.isEmpty()) {
            return AbsencesEffectifs.BilanComblement.de(List.of());
        }
        Contexte sys = duMecanisme(ctx);
        boolean longue = dureeLongue(ctx, a);
        List<ResultatComblement> resultats = places.stream().map(p -> comblerUne(sys, a.id(), new Quart(p.id(), lire.equipages().lirePlace(sys, p.id()).equipage()), longue))
                .toList();
        AbsencesEffectifs.BilanComblement bilan = AbsencesEffectifs.BilanComblement.de(resultats);
        ecrire.journal().ecrire(ctx, new JournalEffectifs.Consignation("places.comblement", "absence", a.id(), a.uniteCode(), null, bilan.vue(), MECANISME));
        return bilan;
    }

    private ResultatComblement comblerUne(Contexte sys, String absenceId, Quart q, boolean longue) {
        Planification.ModeAttribution mode = q.equipage().modeAttribution();
        boolean enCours = longue && lire.equipages().periodeEnCours(sys, q.equipage());
        return switch (AbsencesEffectifs.voieDeComblement(longue, enCours, mode)) {
            case RESERVER -> enregistrer(sys, absenceId, q, ResultatComblement.REMPLACEMENT_LONG_TERME, null).resultat();
            case ATTRIBUER -> attribuerDOffice(sys, absenceId, q);
            case OFFRIR -> offrir(sys, absenceId, q);
        };
    }

    private DepotComblementsAbsencesPort.Comblement enregistrer(Contexte sys, String absenceId, Quart q, ResultatComblement r, String attribueeA) {
        DepotComblementsAbsencesPort.Comblement c = new DepotComblementsAbsencesPort.Comblement(ecrire.sequence().suivant("ecbl"), absenceId, q.placeId(),
                q.equipage().modeAttribution(), r, attribueeA, sys.maintenant());
        ecrire.depot().inserer(sys.tenantId(), c, MECANISME);
        return c;
    }

    private ResultatComblement attribuerDOffice(Contexte sys, String absenceId, Quart q) {
        Optional<Disponibilites.Candidature> retenu = ecrire.attribution().attribuerAuPrioritaire(sys, q.placeId(), MECANISME);
        if (retenu.isEmpty()) {
            return enregistrer(sys, absenceId, q, ResultatComblement.SANS_CANDIDAT, null).resultat();
        }
        String rid = retenu.get().ressourceId();
        DepotComblementsAbsencesPort.Comblement c = enregistrer(sys, absenceId, q, ResultatComblement.ATTRIBUEE, rid);
        avis.aviser(sys, c.id(), rid, new AvisDeComblement.Message("quart.attribue", "Quart attribué le " + q.equipage().date(),
                "Le quart " + q.libelle() + " vous est attribué d'office : il remplace une absence et vous étiez prioritaire selon les règles d'attribution.", URL_QUARTS));
        return ResultatComblement.ATTRIBUEE;
    }

    private ResultatComblement offrir(Contexte sys, String absenceId, Quart q) {
        List<Disponibilites.Candidature> admissibles = lire.disponibilites().candidats(sys, q.placeId()).admissibles();
        if (admissibles.isEmpty()) {
            return enregistrer(sys, absenceId, q, ResultatComblement.SANS_CANDIDAT, null).resultat();
        }
        DepotComblementsAbsencesPort.Comblement c = enregistrer(sys, absenceId, q, ResultatComblement.OFFERTE, null);
        if (ecrire.cascade().estEnCascade(sys.tenantId())) {
            ecrire.cascade().ouvrir(sys, new CascadeOffresQuart.Ouverture(c.id(), absenceId, q.placeId(), q.equipage(), admissibles));
            return ResultatComblement.OFFERTE;
        }
        for (Disponibilites.Candidature x : admissibles) {
            avis.aviser(sys, c.id(), x.ressourceId(), new AvisDeComblement.Message("quart.offert", "Quart disponible le " + q.equipage().date(),
                    "Un quart " + q.libelle() + " est disponible et vous y êtes admissible. Faites savoir à votre planificateur si vous le prenez.", URL_QUARTS));
        }
        return ResultatComblement.OFFERTE;
    }
}
