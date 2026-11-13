package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.DepotRepercussionPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.RepercussionPoste;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <strong>Répercussion d'un poste modifié sur ses quarts déjà générés</strong> (E-CH-3) — jumeau de
 * {@code serviceRepercussion.ts}. On ne touche qu'aux places futures (après aujourd'hui : jamais rétroactif), d'une
 * période non close, non pointées. Une place attribuée garde son titulaire, sauf conflit (corps d'emploi qui n'est
 * plus le sien, chevauchement avec un autre quart) : elle est libérée, et le conflit est rapporté et journalisé.
 * S'exécute dans la transaction de la modification du poste.
 */
public class RepercussionPostes {

    /** Ce qu'un conflit a obligé à défaire. */
    public record Conflit(String placeId, String date, String ressourceId, String motif) { }

    /** Ce que la répercussion a changé. */
    public record Repercussion(int deplacees, int requalifiees, int retirees, int ajoutees, int pointeesIgnorees, List<Conflit> conflits) { }

    /** Le poste tel qu'il est devenu, et son titulaire. */
    public record PosteRepercute(String id, String titulaireId, RepercussionPoste.FormePoste forme) { }

    /** Les dépôts touchés. */
    public record Depots(DepotRepercussionPort repercussion, DepotEquipagesPort equipages) { }

    private final Depots depots;
    private final LectureReferentiels referentiels;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public RepercussionPostes(Depots depots, LectureReferentiels referentiels, JournalEffectifsPort journal, Sequence sequence) {
        this.depots = depots;
        this.referentiels = referentiels;
        this.journal = journal;
        this.sequence = sequence;
    }

    /** Le compte rendu d'une répercussion, et ses traces. */
    private final class Rapport {
        private final Contexte ctx;
        private final PosteRepercute poste;
        private final Map<String, Planification.Plage> plages;
        private int deplacees;
        private int requalifiees;
        private int retirees;
        private int ajoutees;
        private int pointeesIgnorees;
        private final List<Conflit> conflits = new ArrayList<>();

        Rapport(Contexte ctx, PosteRepercute poste, Map<String, Planification.Plage> plages) {
            this.ctx = ctx;
            this.poste = poste;
            this.plages = plages;
        }

        Repercussion resultat() {
            return new Repercussion(deplacees, requalifiees, retirees, ajoutees, pointeesIgnorees, List.copyOf(conflits));
        }

        void tracer(String action, String placeId, Map<String, Object> avant, Map<String, Object> apres) {
            Map<String, Object> complet = new LinkedHashMap<>(apres);
            complet.put("posteId", poste.id());
            journal.ecrire(ctx, JournalEffectifs.Consignation.de(action, "place", placeId, poste.forme().uniteCode(), avant, complet));
        }

        /** Garde le titulaire s'il le peut encore ; sinon libère la place, rapporte et trace le conflit. */
        void verifierTitulaire(String placeId, String date, String ressourceId, Planification.Creneau creneau) {
            if (ressourceId == null) {
                return;
            }
            String motif = conflitDe(ctx, ressourceId, placeId, poste.forme().corpsEmploiCode(), creneau);
            if (motif == null) {
                return;
            }
            depots.equipages().liberer(ctx.tenantId(), placeId);
            conflits.add(new Conflit(placeId, date, ressourceId, motif));
            Map<String, Object> apres = new LinkedHashMap<>();
            apres.put("ressourceId", null);
            apres.put("motif", motif);
            tracer("place.repercussion.conflit", placeId, Map.of("ressourceId", ressourceId), apres);
        }
    }

    private String conflitDe(Contexte ctx, String ressourceId, String placeId, String corps, Planification.Creneau creneau) {
        if (!depots.repercussion().corpsDeRessource(ctx.tenantId(), ressourceId).map(corps::equals).orElse(false)) {
            return "n'est plus du corps d'emploi du poste (" + corps + ")";
        }
        return Planification.chevaucheUnQuart(depots.equipages().creneauxDe(ctx.tenantId(), ressourceId, placeId), creneau)
                ? "tient déjà un quart qui chevauche les nouvelles heures" : null;
    }

    private Map<String, Planification.Plage> plagesHoraires(Contexte ctx) {
        Map<String, Planification.Plage> plages = new HashMap<>();
        referentiels.lister(ctx, TypeReferentiel.PLAGES_HORAIRES.code(), null, false).forEach(v -> plages.put(v.code(),
                new Planification.Plage(((Number) v.attributs().get("debut")).intValue(), ((Number) v.attributs().get("fin")).intValue())));
        return plages;
    }

    /** Après le départ d'une place : l'équipage vide disparaît, sinon son minimum ne dépasse pas ce qui reste. */
    private void rangerApresDepart(String tenantId, String equipageId) {
        DepotRepercussionPort d = depots.repercussion();
        int restantes = d.nombrePlaces(tenantId, equipageId);
        if (restantes == 0) {
            d.supprimerEquipage(tenantId, equipageId);
            return;
        }
        d.poserMinimum(tenantId, equipageId, RepercussionPoste.minimumApresRetrait(d.minimum(tenantId, equipageId), restantes));
    }

    /** L'équipage qui reçoit la place : celui de la même clé, ou un nouveau, généré. */
    private String equipageCible(Rapport r, DepotRepercussionPort.Cle cle) {
        return depots.repercussion().equipageDe(r.ctx.tenantId(), cle).orElseGet(() -> {
            String id = sequence.suivant("eeqp");
            depots.repercussion().insererEquipage(r.ctx.tenantId(), id, cle, r.plages.get(cle.plageCode()), r.ctx.maintenant());
            return id;
        });
    }

    /** Fait entrer une place : rang suivant, minimum tenu si toutes les places étaient requises. */
    private int accueillir(String tenantId, String equipageId) {
        DepotRepercussionPort d = depots.repercussion();
        d.poserMinimum(tenantId, equipageId, RepercussionPoste.minimumApresAjout(d.minimum(tenantId, equipageId), d.nombrePlaces(tenantId, equipageId)));
        return d.rangMax(tenantId, equipageId) + 1;
    }

    private void retirer(Rapport r, DepotRepercussionPort.PlaceFuture p) {
        depots.repercussion().supprimerPlace(r.ctx.tenantId(), p.placeId());
        rangerApresDepart(r.ctx.tenantId(), p.equipageId());
        r.retirees++;
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("date", p.place().date());
        avant.put("equipageId", p.equipageId());
        avant.put("ressourceId", p.ressourceId());
        r.tracer("place.repercussion.retiree", p.placeId(), avant, Map.of());
    }

    private void deplacer(Rapport r, DepotRepercussionPort.PlaceFuture p, String plageCode) {
        RepercussionPoste.FormePoste f = r.poste.forme();
        String cible = equipageCible(r, new DepotRepercussionPort.Cle(p.periodeId(), p.place().uniteCode(), p.place().date(), plageCode, f.milieuId(), f.natureCode()));
        depots.repercussion().deplacerPlace(r.ctx.tenantId(), p.placeId(), cible, accueillir(r.ctx.tenantId(), cible), f.corpsEmploiCode());
        rangerApresDepart(r.ctx.tenantId(), p.equipageId());
        r.deplacees++;
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("equipageId", p.equipageId());
        avant.put("plageCode", p.place().plageCode());
        avant.put("milieuId", p.place().milieuId());
        avant.put("natureCode", p.place().natureCode());
        r.tracer("place.repercussion.deplacee", p.placeId(), avant, Map.of("equipageId", cible, "plageCode", plageCode, "milieuId", f.milieuId(), "natureCode", f.natureCode()));
        r.verifierTitulaire(p.placeId(), p.place().date(), p.ressourceId(), depots.repercussion().creneau(r.ctx.tenantId(), cible));
    }

    private void requalifier(Rapport r, DepotRepercussionPort.PlaceFuture p) {
        String corps = r.poste.forme().corpsEmploiCode();
        depots.repercussion().requalifier(r.ctx.tenantId(), p.placeId(), corps);
        r.requalifiees++;
        r.tracer("place.repercussion.requalifiee", p.placeId(), Map.of("corpsEmploiCode", p.place().corpsEmploiCode()), Map.of("corpsEmploiCode", corps));
        r.verifierTitulaire(p.placeId(), p.place().date(), p.ressourceId(), depots.repercussion().creneau(r.ctx.tenantId(), p.equipageId()));
    }

    /** Les jours que le nouveau cycle ajoute, dans les périodes où le poste avait déjà des quarts générés. */
    private void ajouter(Rapport r, Set<String> periodeIds) {
        RepercussionPoste.FormePoste f = r.poste.forme();
        for (String periodeId : periodeIds) {
            DepotRepercussionPort.Bornes b = depots.repercussion().bornes(r.ctx.tenantId(), periodeId);
            if (!b.uniteCode().equals(f.uniteCode())) {
                continue;
            }
            Set<String> tenus = depots.repercussion().datesTenues(r.ctx.tenantId(), r.poste.id(), periodeId);
            RepercussionPoste.joursAAjouter(f, Planification.joursEntre(b.debut(), b.fin()), r.ctx.aujourdhui(), tenus, r.plages.keySet())
                    .forEach(j -> ajouterJour(r, periodeId, b.uniteCode(), j));
        }
    }

    private void ajouterJour(Rapport r, String periodeId, String uniteCode, RepercussionPoste.JourAAjouter j) {
        RepercussionPoste.FormePoste f = r.poste.forme();
        String cible = equipageCible(r, new DepotRepercussionPort.Cle(periodeId, uniteCode, j.date(), j.plageCode(), f.milieuId(), f.natureCode()));
        String placeId = sequence.suivant("epla");
        depots.equipages().insererPlace(r.ctx.tenantId(), placeId, cible, accueillir(r.ctx.tenantId(), cible),
                new DepotEquipagesPort.NouvellePlace(f.corpsEmploiCode(), r.poste.id(), r.poste.titulaireId()), r.ctx.maintenant());
        r.ajoutees++;
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("equipageId", cible);
        apres.put("date", j.date());
        apres.put("plageCode", j.plageCode());
        apres.put("ressourceId", r.poste.titulaireId());
        r.tracer("place.repercussion.ajoutee", placeId, Map.of(), apres);
        r.verifierTitulaire(placeId, j.date(), r.poste.titulaireId(), depots.repercussion().creneau(r.ctx.tenantId(), cible));
    }

    /** Répercute la forme actuelle d'un poste sur ses quarts futurs déjà générés ; rend ce qui a changé. */
    public Repercussion repercuter(Contexte ctx, PosteRepercute poste) {
        Rapport r = new Rapport(ctx, poste, plagesHoraires(ctx));
        List<DepotRepercussionPort.PlaceFuture> places = depots.repercussion().placesFutures(ctx.tenantId(), poste.id(), ctx.aujourdhui());
        Set<String> periodes = new LinkedHashSet<>();
        for (DepotRepercussionPort.PlaceFuture p : places) {
            periodes.add(p.periodeId());
            RepercussionPoste.Decision d = RepercussionPoste.decision(p.place(), poste.forme(), r.plages.keySet());
            switch (d.action()) {
                case IGNORER_POINTEE -> r.pointeesIgnorees++;
                case RETIRER -> retirer(r, p);
                case DEPLACER -> deplacer(r, p, d.plageCode());
                case AJUSTER_CORPS -> requalifier(r, p);
                case AUCUNE -> { }
            }
        }
        ajouter(r, periodes);
        return r.resultat();
    }
}
