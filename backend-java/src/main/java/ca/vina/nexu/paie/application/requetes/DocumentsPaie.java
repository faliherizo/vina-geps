package ca.vina.nexu.paie.application.requetes;

import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.Cumuls;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.EtatCycle;
import ca.vina.nexu.paie.domaine.LecturePaiePort;
import ca.vina.nexu.paie.domaine.RefusPaie;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Les documents de la paie (ADR 0084) : la fiche de paie d'une personne, et le registre d'un cycle. Une personne ne
 * télécharge que ses fiches de cycles traités ; qui gère la paie télécharge celles d'un cycle préparé, marquées
 * « projet ».
 */
public class DocumentsPaie {

    /** Un document produit : son nom de fichier et ses octets PDF. */
    public record Fichier(String nom, byte[] octets) { }

    /** La fiche : le bulletin, son cycle, et le cumul de l'année civile, ce bulletin compris. */
    public record Fiche(Bulletin bulletin, CyclePaie cycle, Cumuls cumulAnnuel) {

        public boolean estUnProjet() {
            return cycle.etat() != EtatCycle.TRAITE;
        }
    }

    public record Registre(CyclePaie cycle, List<Bulletin> bulletins) { }

    /** Port : la mise en page, implantée dans l'infrastructure (PDFBox, PDF/A-2b). */
    public interface Rendu {
        byte[] fiche(Fiche fiche);

        byte[] registre(Registre registre);
    }

    /** Un matricule est une saisie : il ne passe dans un en-tête HTTP qu'une fois réduit à des caractères sûrs. */
    private static final Pattern NOM_SUR = Pattern.compile("[^A-Za-z0-9._-]");

    private final LecturePaie lecture;
    private final LecturePaiePort depot;
    private final Rendu rendu;

    public DocumentsPaie(LecturePaie lecture, LecturePaiePort depot, Rendu rendu) {
        this.lecture = lecture;
        this.depot = depot;
        this.rendu = rendu;
    }

    public Fichier ficheDuCycle(String tenantId, String cycleId, String bulletinId) {
        CyclePaie cycle = lecture.exigerCycle(tenantId, cycleId);
        Bulletin b = lecture.bulletins(tenantId, cycleId).stream().filter(x -> x.id().equals(bulletinId)).findFirst()
                .orElseThrow(() -> introuvable(bulletinId));
        return fiche(tenantId, b, cycle);
    }

    /** Ses propres fiches seulement, et de cycles traités : celle d'un autre est introuvable, pas interdite. */
    public Fichier maFiche(String tenantId, String utilisateurId, String bulletinId) {
        Bulletin b = lecture.mesBulletins(tenantId, utilisateurId).stream().filter(x -> x.id().equals(bulletinId)).findFirst()
                .orElseThrow(() -> introuvable(bulletinId));
        return fiche(tenantId, b, lecture.exigerCycle(tenantId, b.cycleId()));
    }

    public Fichier registre(String tenantId, String cycleId) {
        CyclePaie cycle = lecture.exigerCycle(tenantId, cycleId);
        return new Fichier("registre-paie-" + cycle.datePaie() + ".pdf", rendu.registre(new Registre(cycle, lecture.bulletins(tenantId, cycleId))));
    }

    private Fichier fiche(String tenantId, Bulletin b, CyclePaie cycle) {
        String annee = b.datePaie().substring(0, 4);
        Cumuls avant = depot.bulletinsTraitesDe(tenantId, b.ressourceId()).stream()
                .filter(x -> !x.id().equals(b.id()) && x.datePaie().startsWith(annee) && x.datePaie().compareTo(b.datePaie()) <= 0)
                .map(Bulletin::retenues).reduce(Cumuls.AUCUN, Cumuls::plus, (x, y) -> x);
        String nom = "fiche-paie-" + NOM_SUR.matcher(b.matricule()).replaceAll("-") + "-" + b.datePaie() + ".pdf";
        return new Fichier(nom, rendu.fiche(new Fiche(b, cycle, avant.plus(b.retenues()))));
    }

    private static RefusPaie introuvable(String bulletinId) {
        return RefusPaie.introuvable("bulletin « " + bulletinId + " » introuvable");
    }
}
