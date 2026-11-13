package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotPostesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.RepercussionPoste;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Les postes : des lignes de quarts attribuables (E-CH-2 à E-CH-5) — jumeau de {@code servicePostes.ts}. Leurs
 * paramètres fonctionnels sont déclarés dans le référentiel {@code parametres_poste}, pas dans le code.
 */
public class GestionnairePostes {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

    /** Les dépendances de lecture d'un poste : référentiels, périmètres, milieux, ressources. */
    public record Lectures(LecturePostes postes, LectureReferentiels referentiels, LecturePerimetres perimetres, LectureMilieux milieux,
                           LectureRessources ressources) { }

    /** Les écritures d'un poste : le journal, les identifiants, et la répercussion sur ses quarts (E-CH-3). */
    public record Ecritures(JournalEffectifsPort journal, Sequence sequence, RepercussionPostes repercussion) { }

    /** Un poste modifié et ce que sa modification a fait à ses quarts futurs. */
    public record PosteModifie(@JsonUnwrapped Poste poste, RepercussionPostes.Repercussion repercussion) { }

    private static final RepercussionPostes.Repercussion SANS_REPERCUSSION = new RepercussionPostes.Repercussion(0, 0, 0, 0, 0, List.of());

    private final DepotPostesPort depot;
    private final Lectures lire;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;
    private final RepercussionPostes repercussion;

    public GestionnairePostes(DepotPostesPort depot, Lectures lire, Ecritures ecrire) {
        this.depot = depot;
        this.lire = lire;
        this.journal = ecrire.journal();
        this.sequence = ecrire.sequence();
        this.repercussion = ecrire.repercussion();
    }

    /**
     * Pour les assemblages d'essai des autres lots, qui créent et publient des postes sans jamais en modifier :
     * aucune répercussion n'y est branchée. L'application passe toujours par le constructeur complet.
     */
    public GestionnairePostes(DepotPostesPort depot, Lectures lire, JournalEffectifsPort journal, Sequence sequence) {
        this(depot, lire, new Ecritures(journal, sequence, null));
    }

    /** Les paramètres fonctionnels, validés contre leurs définitions en vigueur (E-CH-5). */
    private Map<String, Object> parametresDe(Contexte ctx, Object brut) {
        Map<?, ?> entree = brut instanceof Map<?, ?> m ? m : Map.of();
        Map<String, Comportements.TypeValeurParametre> definitions = new HashMap<>();
        lire.referentiels().lister(ctx, TypeReferentiel.PARAMETRES_POSTE.code(), null, false).forEach(d ->
                definitions.put(d.code(), Comportements.TypeValeurParametre.valueOf(String.valueOf(d.attributs().get("typeValeur")))));
        Map<String, Object> out = new LinkedHashMap<>();
        entree.forEach((cle, valeur) -> out.put(String.valueOf(cle), parametre(definitions, String.valueOf(cle), valeur)));
        return out;
    }

    private static Object parametre(Map<String, Comportements.TypeValeurParametre> definitions, String code, Object valeur) {
        Comportements.TypeValeurParametre type = definitions.get(code);
        if (type == null) {
            throw new EffectifsInvalideException("paramètre de poste inconnu : « " + code + " » (déclarez-le dans le référentiel des paramètres de poste)");
        }
        switch (type) {
            case NOMBRE -> {
                if (!(valeur instanceof Number n) || Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue())) {
                    throw new EffectifsInvalideException("« " + code + " » attend un nombre");
                }
            }
            case BOOLEEN -> {
                if (!(valeur instanceof Boolean)) {
                    throw new EffectifsInvalideException("« " + code + " » attend vrai ou faux");
                }
            }
            case TEXTE -> {
                if (!(valeur instanceof String)) {
                    throw new EffectifsInvalideException("« " + code + " » attend un texte");
                }
            }
        }
        return valeur;
    }

    private DepotPostesPort.Champs champs(Contexte ctx, Map<String, Object> entree) {
        String libelle = GestionnaireRessources.texte(entree.get("libelle"));
        if (libelle.isEmpty()) {
            throw new EffectifsInvalideException("« libellé » est obligatoire");
        }
        String uniteCode = GestionnaireRessources.texte(entree.get("uniteCode"));
        exiger(ctx, TypeReferentiel.UNITES, uniteCode);
        lire.perimetres().exiger(ctx, uniteCode, Perimetres.Niveau.PLANIFICATION);
        String statut = GestionnaireRessources.texte(entree.get("statutEmploiCode"));
        String corps = GestionnaireRessources.texte(entree.get("corpsEmploiCode"));
        String nature = GestionnaireRessources.texte(entree.get("natureCode"));
        exiger(ctx, TypeReferentiel.STATUTS_EMPLOI, statut);
        exiger(ctx, TypeReferentiel.CORPS_EMPLOI, corps);
        exiger(ctx, TypeReferentiel.NATURES_QUART, nature);
        String milieuId = lire.milieux().exiger(ctx, GestionnaireRessources.texte(entree.get("milieuId"))).id();
        List<List<String>> cycle = Planification.cycleDe(entree.get("cycle"));
        Planification.plagesDuCycle(cycle).forEach(plage -> exiger(ctx, TypeReferentiel.PLAGES_HORAIRES, plage));
        String debutCycle = Planification.dateDe(entree.get("debutCycle"), "début du cycle");
        return new DepotPostesPort.Champs(libelle, uniteCode, statut, corps, nature, milieuId, cycle, debutCycle, parametresDe(ctx, entree.get("parametres")));
    }

    private void exiger(Contexte ctx, TypeReferentiel type, String code) {
        lire.referentiels().exigerValeur(ctx.tenantId(), type, code, ctx.aujourdhui());
    }

    public Poste creer(Contexte ctx, Map<String, Object> entree) {
        String code = GestionnaireRessources.texte(entree.get("code"));
        if (!CODE.matcher(code).matches()) {
            throw new EffectifsInvalideException("« code » : 1 à 40 lettres, chiffres, tirets ou soulignés");
        }
        DepotPostesPort.Champs c = champs(ctx, entree);
        if (depot.codeExiste(ctx.tenantId(), code)) {
            throw new ConflitException("poste « " + code + " » existe déjà");
        }
        String id = sequence.suivant("epos");
        depot.inserer(ctx.tenantId(), id, code, c, ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("poste.cree", "poste", id, c.uniteCode(), null, Map.of("code", code, "champs", c)));
        return lire.postes().exiger(ctx, id);
    }

    /** Écrit les champs d'un poste et répercute sa nouvelle forme sur ses quarts futurs (dans la transaction de l'appel). */
    private PosteModifie ecrire(Contexte ctx, Poste avant, DepotPostesPort.Champs c, String action) {
        depot.modifier(ctx.tenantId(), avant.id(), c);
        RepercussionPostes.Repercussion r = repercussion == null ? SANS_REPERCUSSION
                : repercussion.repercuter(ctx, new RepercussionPostes.PosteRepercute(avant.id(), avant.titulaireId(), RepercussionPoste.FormePoste.de(c)));
        journal.ecrire(ctx, JournalEffectifs.Consignation.de(action, "poste", avant.id(), c.uniteCode(), avant, Map.of("champs", c, "repercussion", r)));
        return new PosteModifie(lire.postes().exiger(ctx, avant.id()), r);
    }

    /** Modifie un poste ; ses quarts futurs déjà générés suivent (E-CH-3). */
    public PosteModifie modifier(Contexte ctx, String id, Map<String, Object> entree) {
        Poste avant = lire.postes().exiger(ctx, id);
        lire.perimetres().exiger(ctx, avant.uniteCode(), Perimetres.Niveau.PLANIFICATION);
        return ecrire(ctx, avant, champs(ctx, entree), "poste.modifie");
    }

    /**
     * Modifie <strong>plusieurs postes</strong> d'un geste (E-CH-3) : les mêmes changements, appliqués à chacun, tout ou
     * rien — tout est validé avant la première écriture, et l'appel tient dans une transaction.
     */
    public List<PosteModifie> modifierEnLot(Contexte ctx, Map<String, Object> entree) {
        if (!(entree.get("ids") instanceof List<?> ids) || ids.isEmpty()) {
            throw new EffectifsInvalideException("« ids » : au moins un poste");
        }
        Map<String, Object> changements = RepercussionPoste.changementsDe(entree.get("changements"));
        List<Map.Entry<Poste, DepotPostesPort.Champs>> lot = ids.stream().map(String::valueOf).distinct().map(id -> {
            Poste avant = lire.postes().exiger(ctx, id);
            lire.perimetres().exiger(ctx, avant.uniteCode(), Perimetres.Niveau.PLANIFICATION);
            Map<String, Object> fusion = champsDe(avant);
            fusion.putAll(changements);
            return Map.entry(avant, champs(ctx, fusion));
        }).toList();
        return lot.stream().map(e -> ecrire(ctx, e.getKey(), e.getValue(), "poste.modifie.lot")).toList();
    }

    /** Les champs actuels d'un poste, comme un corps de requête les donnerait. */
    private static Map<String, Object> champsDe(Poste p) {
        Map<String, Object> m = new HashMap<>();
        m.put("libelle", p.libelle());
        m.put("uniteCode", p.uniteCode());
        m.put("statutEmploiCode", p.statutEmploiCode());
        m.put("corpsEmploiCode", p.corpsEmploiCode());
        m.put("milieuId", p.milieuId());
        m.put("natureCode", p.natureCode());
        m.put("cycle", p.cycle());
        m.put("debutCycle", p.debutCycle());
        m.put("parametres", p.parametres());
        return m;
    }

    /** Publie des postes en brouillon (E-CH-4) : ils deviennent offerts, et générables en quarts. */
    public List<Poste> publier(Contexte ctx, Object ids) {
        if (!(ids instanceof List<?> l) || l.isEmpty()) {
            throw new EffectifsInvalideException("« ids » : au moins un poste à publier");
        }
        List<Poste> postes = l.stream().map(id -> lire.postes().exiger(ctx, String.valueOf(id))).toList();
        for (Poste p : postes) {
            lire.perimetres().exiger(ctx, p.uniteCode(), Perimetres.Niveau.PLANIFICATION);
            if (p.etat() != Planification.EtatPoste.BROUILLON) {
                throw new EffectifsInvalideException("le poste « " + p.code() + " » est déjà " + p.etat().name().toLowerCase());
            }
        }
        for (Poste p : postes) {
            depot.poserEtat(ctx.tenantId(), p.id(), Planification.EtatPoste.PUBLIE, p.titulaireId());
            journal.ecrire(ctx, JournalEffectifs.Consignation.de("poste.publie", "poste", p.id(), p.uniteCode(), null, null));
        }
        return postes.stream().map(p -> lire.postes().exiger(ctx, p.id())).toList();
    }

    /**
     * Donne (ou retire) un titulaire à un poste publié — appelé par l'AGPH (R7). Sans titulaire, le poste
     * redevient vacant et ses quarts vont à la gestion régulière.
     */
    public Poste attribuerTitulaire(Contexte ctx, String id, String ressourceId, String systeme) {
        Poste p = lire.postes().exiger(ctx, id);
        if (p.etat() == Planification.EtatPoste.BROUILLON) {
            throw new EffectifsInvalideException("le poste « " + p.code() + " » n'est pas publié");
        }
        if (ressourceId != null) {
            Ressource r = lire.ressources().exiger(ctx, ressourceId);
            if (!r.corpsEmploiCode().equals(p.corpsEmploiCode())) {
                throw new EffectifsInvalideException("« " + r.nom() + " » n'est pas du corps d'emploi du poste (" + p.corpsEmploiCode() + ")");
            }
        }
        Planification.EtatPoste etat = ressourceId == null ? Planification.EtatPoste.VACANT : Planification.EtatPoste.ATTRIBUE;
        depot.poserEtat(ctx.tenantId(), id, etat, ressourceId);
        Map<String, Object> avant = new HashMap<>();
        avant.put("titulaireId", p.titulaireId());
        avant.put("etat", p.etat());
        Map<String, Object> apres = new HashMap<>();
        apres.put("titulaireId", ressourceId);
        apres.put("etat", etat);
        journal.ecrire(ctx, new JournalEffectifs.Consignation("poste.titulaire", "poste", id, p.uniteCode(), avant, apres, systeme));
        return lire.postes().exiger(ctx, id);
    }
}
