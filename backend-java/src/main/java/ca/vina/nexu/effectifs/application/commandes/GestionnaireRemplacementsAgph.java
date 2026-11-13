package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphInteresse;
import ca.vina.nexu.effectifs.domaine.AgphRemplacement;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotRemplacementsAgphPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Les remplacements long terme</strong> (lot R7 — E-CH-7, E-CH-8) — jumeau de {@code serviceRemplacements.ts}.
 * Un poste dont le titulaire est absent longtemps, ou un poste vacant, s'ouvre en remplacement : les personnes
 * admissibles se manifestent jusqu'à une date ; la décision appartient à {@link DecisionRemplacementsAgph}.
 */
public class GestionnaireRemplacementsAgph {

    private static final String FERMEE = "la fenêtre de manifestation est fermée";
    private static final String DEJA_DECIDE = "ce remplacement est déjà décidé";
    private static final int LONGUEUR_DATE = 10;
    private static final int MOTIF_MAX = 500;

    /** Le socle : postes, ressources, périmètres, journal, identifiants. */
    public record Socle(LecturePostes postes, LectureRessources ressources, LecturePerimetres perimetres, JournalEffectifsPort journal, Sequence sequence) { }

    private final DepotRemplacementsAgphPort depot;
    private final DecisionRemplacementsAgph decision;
    private final Socle socle;

    public GestionnaireRemplacementsAgph(DepotRemplacementsAgphPort depot, DecisionRemplacementsAgph decision, Socle socle) {
        this.depot = depot;
        this.decision = decision;
        this.socle = socle;
    }

    private void consigner(Contexte ctx, String id, String uniteCode, String action, Object apres) {
        socle.journal().ecrire(ctx, JournalEffectifs.Consignation.de(action, DecisionRemplacementsAgph.OBJET, id, uniteCode, null, apres));
    }

    public AgphRemplacement ouvrir(Contexte ctx, Map<String, Object> entree) {
        Poste p = socle.postes().exiger(ctx, entree.get("posteId") instanceof String s ? s : "");
        socle.perimetres().exiger(ctx, p.uniteCode(), Perimetres.Niveau.GESTION);
        if (p.etat() == Planification.EtatPoste.BROUILLON) {
            throw new EffectifsInvalideException("le poste « " + p.code() + " » n'est pas publié");
        }
        DepotRemplacementsAgphPort.Nouveau n = nouveau(ctx, p, entree);
        String id = socle.sequence().suivant("erlt");
        depot.inserer(ctx.tenantId(), id, n, ctx);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("posteId", p.id());
        apres.put("origine", n.origine());
        apres.put("debut", n.debut());
        apres.put("fin", n.fin());
        apres.put("finManifestation", n.finManifestation());
        consigner(ctx, id, p.uniteCode(), "remplacement.ouvert", apres);
        return decision.lireBrut(ctx, id);
    }

    private static DepotRemplacementsAgphPort.Nouveau nouveau(Contexte ctx, Poste p, Map<String, Object> entree) {
        Agph.OrigineRemplacement origine = Agph.origineDe(entree.get("origine"));
        String debut = Referentiels.dateValide(entree.get("debut"), "debut");
        String fin = Referentiels.dateValide(entree.get("fin"), "fin");
        if (fin.compareTo(debut) < 0) {
            throw new EffectifsInvalideException("la fin (" + fin + ") précède le début (" + debut + ")");
        }
        String finManifestation = fenetre(ctx, entree.get("finManifestation"), debut);
        String referenceId = entree.get("referenceId") instanceof String s && !s.isBlank() ? s.trim() : null;
        if (origine == Agph.OrigineRemplacement.ABSENCE && referenceId == null) {
            throw new EffectifsInvalideException("un remplacement d'absence nomme l'absence remplacée (« referenceId »)");
        }
        return new DepotRemplacementsAgphPort.Nouveau(p.uniteCode(), p.id(), origine, referenceId, debut, fin, finManifestation);
    }

    /** La fin de la fenêtre : un instant à venir, au plus tard le premier jour du remplacement. */
    private static String fenetre(Contexte ctx, Object brut, String debut) {
        String finManifestation = Agph.instantDe(brut);
        if (finManifestation.isEmpty()) {
            throw new EffectifsInvalideException("« finManifestation » : un instant ISO 8601");
        }
        if (!Agph.fenetreOuverte(finManifestation, ctx.maintenant())) {
            throw new EffectifsInvalideException("la fenêtre de manifestation se fermerait dans le passé");
        }
        if (finManifestation.substring(0, LONGUEUR_DATE).compareTo(debut) > 0) {
            throw new EffectifsInvalideException("la fenêtre doit se fermer au plus tard le premier jour du remplacement (" + debut + ")");
        }
        return finManifestation;
    }

    /** Visible par le périmètre de l'unité ; ou par une personne admissible, pour s'y manifester : même corps d'emploi que le poste. */
    private boolean visible(Contexte ctx, AgphRemplacement x) {
        if (ctx.estAdministrateur() || socle.perimetres().unitesVisibles(ctx, Perimetres.Niveau.LECTURE).contains(x.uniteCode())) {
            return true;
        }
        Optional<Ressource> moi = socle.ressources().deLActeur(ctx);
        return moi.isPresent() && moi.get().corpsEmploiCode().equals(socle.postes().exiger(ctx, x.posteId()).corpsEmploiCode());
    }

    public AgphRemplacement lire(Contexte ctx, String id) {
        AgphRemplacement x = decision.lireBrut(ctx, id);
        if (!visible(ctx, x)) {
            throw new IntrouvableException("remplacement « " + id + " » introuvable");
        }
        return decision.rattraper(ctx, x);
    }

    /** {@code etat} facultatif (nul ou vide : sans filtre). */
    public List<AgphRemplacement> lister(Contexte ctx, String etat) {
        return depot.lister(ctx.tenantId()).stream().filter(x -> visible(ctx, x)).map(x -> decision.rattraper(ctx, x))
                .filter(x -> etat == null || etat.isBlank() || x.etat().name().equals(etat.trim())).toList();
    }

    /** Se manifester (E-CH-8) : pour soi-même, pendant la fenêtre, si l'on est admissible. */
    public AgphRemplacement manifester(Contexte ctx, String id) {
        AgphRemplacement x = exigerOuvert(ctx, id);
        Ressource r = socle.ressources().exigerDeLActeur(ctx);
        Poste p = socle.postes().exiger(ctx, x.posteId());
        if (r.id().equals(p.titulaireId())) {
            throw new EffectifsInvalideException("on ne se remplace pas soi-même");
        }
        decision.juger(ctx, r, p, x.id()).exiger();
        depot.manifester(ctx.tenantId(), id, r.id(), ctx.maintenant());
        consigner(ctx, id, x.uniteCode(), "remplacement.interet", Map.of("ressourceId", r.id()));
        return lire(ctx, id);
    }

    private AgphRemplacement exigerOuvert(Contexte ctx, String id) {
        AgphRemplacement x = lire(ctx, id);
        if (x.etat() != Agph.EtatRemplacement.OUVERT) {
            throw new ConflitException(FERMEE);
        }
        return x;
    }

    public AgphRemplacement retirerInteret(Contexte ctx, String id) {
        AgphRemplacement x = exigerOuvert(ctx, id);
        Ressource r = socle.ressources().exigerDeLActeur(ctx);
        if (depot.retirer(ctx.tenantId(), id, r.id(), ctx.maintenant()) == 0) {
            throw new IntrouvableException("vous n'avez pas manifesté d'intérêt pour ce remplacement");
        }
        consigner(ctx, id, x.uniteCode(), "remplacement.interet.retire", Map.of("ressourceId", r.id()));
        return lire(ctx, id);
    }

    /** Les intéressés, pour l'encadrement : qui, depuis quand, dans l'ordre où l'attribution les prendra. */
    public List<AgphInteresse> interesses(Contexte ctx, String id) {
        AgphRemplacement x = lire(ctx, id);
        socle.perimetres().exiger(ctx, x.uniteCode(), Perimetres.Niveau.LECTURE);
        List<AgphInteresse> lignes = depot.interesses(ctx.tenantId(), id);
        return Agph.classerInteresses(lignes.stream().map(AgphInteresse::pourClassement).toList()).stream()
                .map(i -> lignes.stream().filter(l -> l.ressourceId().equals(i.ressourceId())).findFirst().orElseThrow()).toList();
    }

    /**
     * Laisser un remplacement non comblé (E-CH-9) : l'encadrement décide, motif à l'appui, de ne pas le combler — même
     * s'il a des intéressés. La fenêtre se ferme et les quarts passent à la gestion régulière (E-CH-10).
     */
    public AgphRemplacement laisserNonComble(Contexte ctx, String id, Map<String, Object> entree) {
        AgphRemplacement x = lire(ctx, id);
        socle.perimetres().exiger(ctx, x.uniteCode(), Perimetres.Niveau.GESTION);
        if (x.etat() != Agph.EtatRemplacement.OUVERT) {
            throw new ConflitException(DEJA_DECIDE);
        }
        String motif = entree.get("motif") instanceof String s ? s.trim() : "";
        if (motif.isEmpty()) {
            throw new EffectifsInvalideException("« motif » est obligatoire : dites pourquoi ce remplacement demeure non comblé");
        }
        if (motif.length() > MOTIF_MAX) {
            throw new EffectifsInvalideException("« motif » : " + MOTIF_MAX + " caractères au plus");
        }
        decision.laisserNonComble(ctx, x, motif, x.interesses());
        return lire(ctx, id);
    }

    /** Fermer la fenêtre maintenant (l'encadrement n'attend pas la date) : la décision suit aussitôt. */
    public AgphRemplacement fermer(Contexte ctx, String id) {
        AgphRemplacement x = lire(ctx, id);
        socle.perimetres().exiger(ctx, x.uniteCode(), Perimetres.Niveau.GESTION);
        if (x.etat() != Agph.EtatRemplacement.OUVERT) {
            throw new ConflitException(DEJA_DECIDE);
        }
        depot.fermerFenetre(ctx.tenantId(), id, ctx.maintenant());
        consigner(ctx, id, x.uniteCode(), "remplacement.fenetre.fermee", null);
        return lire(ctx, id);
    }
}
