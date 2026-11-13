package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ComptesPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotPerimetresPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.Map;

/** Les écritures des périmètres : ajouter, fermer (jamais effacer — le journal doit pouvoir dire qui avait le droit). */
public class GestionnairePerimetres {

    private final DepotPerimetresPort depot;
    private final LectureReferentiels referentiels;
    private final ComptesPort comptes;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnairePerimetres(DepotPerimetresPort depot, LectureReferentiels referentiels, ComptesPort comptes, JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.referentiels = referentiels;
        this.comptes = comptes;
        this.journal = journal;
        this.sequence = sequence;
    }

    private static String texte(Object v) {
        return v instanceof String s ? s : "";
    }

    public Perimetres.Perimetre ajouter(Contexte ctx, Map<String, Object> entree) {
        String utilisateurId = texte(entree.get("utilisateurId"));
        // Un compte d'un autre établissement n'est pas « introuvable » pour l'appelant : il est hors de sa
        // portée, et le lui dire en détail révélerait qu'il existe.
        if (utilisateurId.isEmpty() || !comptes.etablissementDe(utilisateurId).filter(ctx.tenantId()::equals).isPresent()) {
            throw new EffectifsInvalideException("utilisateur inconnu dans cet établissement");
        }
        String uniteCode = texte(entree.get("uniteCode"));
        Object brut = entree.get("depuis");
        String depuis = brut == null || "".equals(brut) ? ctx.aujourdhui() : Referentiels.dateValide(brut, "depuis");
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, uniteCode, depuis);
        Object etendue = entree.get("etendue");
        Perimetres.Perimetre p = new Perimetres.Perimetre(sequence.suivant("eper"), utilisateurId, uniteCode,
                Perimetres.etendueDe(etendue == null ? Perimetres.Etendue.SOUS_ARBRE.name() : etendue), Perimetres.niveauDe(entree.get("niveau")), depuis, null);
        depot.inserer(ctx.tenantId(), p, ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("perimetre.ajoute", "perimetre", p.id(), p.uniteCode(), null, p));
        return p;
    }

    public Perimetres.Perimetre fermer(Contexte ctx, String id, Map<String, Object> entree) {
        Perimetres.Perimetre p = depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("périmètre « " + id + " » introuvable"));
        Object brut = entree.get("date");
        String date = brut == null || "".equals(brut) ? ctx.aujourdhui() : Referentiels.dateValide(brut, "date");
        Perimetres.verifierFermeture(p, date, ctx.aujourdhui());
        Perimetres.Perimetre ferme = new Perimetres.Perimetre(p.id(), p.utilisateurId(), p.uniteCode(), p.etendue(), p.niveau(), p.depuis(), date);
        depot.fermer(ctx.tenantId(), id, date);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("perimetre.ferme", "perimetre", id, p.uniteCode(), p, ferme));
        return ferme;
    }
}
