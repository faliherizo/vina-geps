package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotQuotasPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Quota;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.util.List;
import java.util.Map;

/**
 * Quotas de comblement et surcharges temporaires (E-GR-8, E-GR-9) — jumeau de {@code serviceQuotas.ts}. Un
 * quota se remplace par versions datées, jamais dans le passé ; une surcharge commence maintenant ou plus tard,
 * si bien que le quota d'hier relu demain n'a pas changé.
 */
public class GestionnaireQuotas {

    private final DepotQuotasPort depot;
    private final LectureQuotas lecture;
    private final LectureReferentiels referentiels;
    private final LecturePerimetres perimetres;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireQuotas(DepotQuotasPort depot, LectureQuotas lecture, LectureReferentiels referentiels, LecturePerimetres perimetres,
                              JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.lecture = lecture;
        this.referentiels = referentiels;
        this.perimetres = perimetres;
        this.journal = journal;
        this.sequence = sequence;
    }

    private String codeFacultatif(Contexte ctx, TypeReferentiel type, Object v) {
        if (!(v instanceof String s) || s.isEmpty()) {
            return null;
        }
        referentiels.exigerValeur(ctx.tenantId(), type, s, ctx.aujourdhui());
        return s;
    }

    /** Définit un quota, ou le remplace à partir d'une date (aujourd'hui ou plus tard). */
    public Quota definir(Contexte ctx, Map<String, Object> entree) {
        String jour = ctx.aujourdhui();
        String uniteCode = entree.get("uniteCode") == null ? "" : String.valueOf(entree.get("uniteCode"));
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, uniteCode, jour);
        perimetres.exiger(ctx, uniteCode, Perimetres.Niveau.PLANIFICATION);
        String plageCode = codeFacultatif(ctx, TypeReferentiel.PLAGES_HORAIRES, entree.get("plageCode"));
        String natureCode = codeFacultatif(ctx, TypeReferentiel.NATURES_QUART, entree.get("natureCode"));
        int quota = Planification.quotaDe(entree.get("quota"));
        Object brut = entree.get("depuis");
        String depuis = brut == null || "".equals(brut) ? jour : Referentiels.dateValide(brut, "depuis");
        if (depuis.compareTo(jour) < 0) {
            throw new EffectifsInvalideException("un quota ne peut pas prendre effet dans le passé");
        }
        String cle = Quota.cleDe(uniteCode, plageCode, natureCode);
        List<Quota.Version> existantes = lecture.parCle(ctx, uniteCode, cle).map(Quota::versions).orElse(List.of());
        if (!existantes.isEmpty()) {
            depot.fermerVersion(ctx.tenantId(), Referentiels.planifierRemplacement(existantes, depuis, jour), depuis);
        }
        depot.insererVersion(ctx.tenantId(), sequence.suivant("equo"), uniteCode, plageCode, natureCode, quota, depuis, ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("quota.defini", "quota", cle, uniteCode, null, Map.of("quota", quota, "depuis", depuis)));
        return lecture.parCle(ctx, uniteCode, cle).orElseThrow();
    }

    /** Surcharge temporaire d'un quota (E-GR-9) : jamais rétroactive, 90 jours au plus, motivée. */
    public Quota surcharger(Contexte ctx, String versionId, Map<String, Object> entree) {
        DepotQuotasPort.Cle v = depot.cleDeLaVersion(ctx.tenantId(), versionId)
                .orElseThrow(() -> new IntrouvableException("quota « " + versionId + " » introuvable"));
        perimetres.exiger(ctx, v.uniteCode(), Perimetres.Niveau.PLANIFICATION);
        int valeur = Planification.quotaDe(entree.get("valeur"));
        String depuis = entree.get("depuis") == null ? "" : String.valueOf(entree.get("depuis"));
        String jusqua = entree.get("jusqua") == null ? "" : String.valueOf(entree.get("jusqua"));
        Planification.verifierSurcharge(depuis, jusqua, ctx.maintenant());
        String motif = GestionnaireRessources.texte(entree.get("motif"));
        if (motif.isEmpty()) {
            throw new EffectifsInvalideException("« motif » : dites pourquoi le quota est surchargé");
        }
        String id = sequence.suivant("esur");
        depot.insererSurcharge(ctx.tenantId(), id, v.cle(), new Quota.SurchargeMotivee(id, valeur, Iso.de(Planification.instantDe(depuis).orElseThrow()),
                Iso.de(Planification.instantDe(jusqua).orElseThrow()), ctx.maintenant(), motif, ctx.acteurId()));
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("quota.surcharge", "quota", v.cle(), v.uniteCode(), null,
                Map.of("valeur", valeur, "depuis", depuis, "jusqua", jusqua, "motif", motif)));
        return lecture.parCle(ctx, v.uniteCode(), v.cle()).orElseThrow();
    }
}
