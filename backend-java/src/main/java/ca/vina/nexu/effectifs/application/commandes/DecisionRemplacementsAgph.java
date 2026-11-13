package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphRemplacement;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.DepotRemplacementsAgphPort;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>La décision d'un remplacement long terme</strong> (E-CH-9, E-CH-10, P-CH-9) — jumeau de {@code juger},
 * {@code attribuerQuarts} et {@code rattraper} de {@code serviceRemplacements.ts}. À la fermeture de la fenêtre, le
 * remplacement va à la plus ancienne personne admissible, qui reçoit les quarts vacants du poste sur la durée (sans
 * chevaucher son propre horaire) ; sans intéressé admissible, il demeure non comblé et ses quarts se transfèrent à la
 * gestion régulière — la fermeture se traite au premier geste qui la suit, sans tâche planifiée.
 */
public class DecisionRemplacementsAgph {

    /** Le mécanisme qui décide à la fermeture de la fenêtre. */
    public static final String MECANISME = "remplacement-long-terme";
    static final String OBJET = "remplacement";

    /** Ce que la décision lit : postes, ressources, référentiels, et le moteur de règles. */
    public record Lectures(LecturePostes postes, LectureRessources ressources, LectureReferentiels referentiels, EvaluateurRegles regles) { }

    /** Les quarts : l'horaire de la personne et l'attribution des places. */
    public record Quarts(LectureEquipages equipages, DepotEquipagesPort places) { }

    private final DepotRemplacementsAgphPort depot;
    private final Lectures lire;
    private final Quarts quarts;
    private final JournalEffectifsPort journal;

    public DecisionRemplacementsAgph(DepotRemplacementsAgphPort depot, Lectures lire, Quarts quarts, JournalEffectifsPort journal) {
        this.depot = depot;
        this.lire = lire;
        this.quarts = quarts;
        this.journal = journal;
    }

    public AgphRemplacement lireBrut(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("remplacement « " + id + " » introuvable"));
    }

    /** L'admissibilité d'une personne : même corps (exigence dure), puis les règles INTERET_REMPLACEMENT. */
    public Agph.Jugement juger(Contexte ctx, Ressource r, Poste p, String objetId) {
        if (!r.corpsEmploiCode().equals(p.corpsEmploiCode())) {
            return Agph.Jugement.refuse("le poste " + p.code() + " requiert le corps " + p.corpsEmploiCode());
        }
        Map<String, Object> faits = new LinkedHashMap<>();
        faits.put("corpsRequis", p.corpsEmploiCode());
        faits.put("corpsEmploi", r.corpsEmploiCode());
        faits.put("statutEmploi", comportementDuStatut(ctx, r));
        return Agph.Jugement.de(lire.regles().evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.INTERET_REMPLACEMENT, OBJET, objetId, ctx.aujourdhui(),
                p.uniteCode(), faits, TourDeRoleAgph.portees(r))));
    }

    private String comportementDuStatut(Contexte ctx, Ressource r) {
        if (r.statutEmploiCode() == null) {
            return "";
        }
        String c = lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.STATUTS_EMPLOI, r.statutEmploiCode(), ctx.aujourdhui()).comportement();
        return c == null ? "" : c;
    }

    /** À la fermeture de la fenêtre : attribuer (P-CH-9), ou constater « non comblé » et transférer (E-CH-9, E-CH-10). */
    public AgphRemplacement rattraper(Contexte ctx, AgphRemplacement x) {
        if (x.etat() != Agph.EtatRemplacement.OUVERT || Agph.fenetreOuverte(x.finManifestation(), ctx.maintenant())) {
            return x;
        }
        Poste p = lire.postes().exiger(ctx, x.posteId());
        List<Agph.Interesse> interesses = depot.interessesRetenables(ctx.tenantId(), x.id());
        List<Ressource> classes = Agph.classerInteresses(interesses).stream().map(i -> lire.ressources().exiger(ctx, i.ressourceId())).toList();
        Optional<Ressource> retenu = classes.stream().filter(r -> juger(ctx, r, p, x.id()).admissible()).findFirst();
        if (retenu.isPresent()) {
            attribuer(ctx, x, retenu.get());
        } else {
            laisserNonComble(ctx, x, null, interesses.size());
        }
        return lireBrut(ctx, x.id());
    }

    /**
     * Le remplacement demeure <strong>non comblé</strong> (E-CH-9) — état enregistré, daté, motivé s'il s'agit d'une
     * décision de l'encadrement ({@code motif} non nul) — et ses quarts vacants sont <strong>transférés</strong> à la
     * gestion régulière (E-CH-10) : ils restent des places libres que le comblement, le bassin et l'attribution prennent
     * en charge. Sans motif, c'est le mécanisme qui constate ; le transfert, lui, est toujours le fait du mécanisme.
     */
    public void laisserNonComble(Contexte ctx, AgphRemplacement x, String motif, int interesses) {
        int quarts = depot.quartsVacants(ctx.tenantId(), x.posteId(), x.debut(), x.fin());
        depot.laisserNonComble(ctx.tenantId(), x.id(), new DepotRemplacementsAgphPort.NonComble(ctx.maintenant(), quarts, motif));
        Map<String, Object> constat = new LinkedHashMap<>();
        constat.put("interesses", interesses);
        constat.put("motif", motif);
        journal.ecrire(ctx, new JournalEffectifs.Consignation("remplacement.non_comble", OBJET, x.id(), x.uniteCode(), null, constat,
                motif == null ? MECANISME : null));
        Map<String, Object> transfert = new LinkedHashMap<>();
        transfert.put("vers", Agph.VERS_GESTION_REGULIERE);
        transfert.put("quarts", quarts);
        consigner(ctx, x, "remplacement.transfere", transfert);
    }

    private void attribuer(Contexte ctx, AgphRemplacement x, Ressource r) {
        int n = attribuerQuarts(ctx, x, r);
        depot.attribuer(ctx.tenantId(), x.id(), r.id(), n, ctx.maintenant());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("ressourceId", r.id());
        apres.put("quarts", n);
        consigner(ctx, x, "remplacement.attribue", apres);
    }

    private void consigner(Contexte ctx, AgphRemplacement x, String action, Object apres) {
        journal.ecrire(ctx, new JournalEffectifs.Consignation(action, OBJET, x.id(), x.uniteCode(), null, apres, MECANISME));
    }

    /** Les quarts vacants du poste sur la durée, un à un, en sautant ceux qui chevaucheraient l'horaire de la personne. */
    private int attribuerQuarts(Contexte ctx, AgphRemplacement x, Ressource r) {
        List<Planification.Creneau> horaire = new ArrayList<>(quarts.equipages().creneauxDe(ctx, r.id(), ""));
        int n = 0;
        for (DepotRemplacementsAgphPort.PlaceVacante pl : depot.placesVacantes(ctx.tenantId(), x.posteId(), x.debut(), x.fin())) {
            if (Planification.chevaucheUnQuart(horaire, pl.creneau())) {
                continue;
            }
            quarts.places().attribuer(ctx.tenantId(), pl.id(), r.id(), Equipage.OrigineAttribution.MOTEUR, null, ctx.maintenant());
            horaire.add(pl.creneau());
            n += 1;
        }
        return n;
    }
}
