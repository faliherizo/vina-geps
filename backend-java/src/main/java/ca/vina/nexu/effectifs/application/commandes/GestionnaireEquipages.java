package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Les équipages : quarts ponctuels, génération depuis les postes, ajustements (E-GN-3, E-CH-2, E-CH-3, E-GR-5,
 * E-GR-6, E-GR-13) — jumeau de la partie écriture de {@code serviceEquipages.ts}, hors attribution des places
 * ({@link GestionnairePlaces}).
 */
public class GestionnaireEquipages {

    /** Les lectures dont un équipage a besoin. */
    public record Lectures(LectureEquipages equipages, LecturePeriodes periodes, LecturePostes postes, LectureReferentiels referentiels,
                           LectureMilieux milieux) { }

    private final DepotEquipagesPort depot;
    private final Lectures lire;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireEquipages(DepotEquipagesPort depot, Lectures lire, JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.lire = lire;
        this.journal = journal;
        this.sequence = sequence;
    }

    /** La période, si l'acteur peut la planifier et qu'elle n'est pas close. */
    Periode periodeModifiable(Contexte ctx, String periodeId) {
        Periode p = lire.periodes().exiger(ctx, periodeId, Perimetres.Niveau.PLANIFICATION);
        if (!Planification.modifiable(p.etat())) {
            throw new EffectifsInvalideException("la période « " + p.nom() + " » est close : elle ne se modifie plus");
        }
        return p;
    }

    private void exiger(Contexte ctx, TypeReferentiel type, String code) {
        lire.referentiels().exigerValeur(ctx.tenantId(), type, code, ctx.aujourdhui());
    }

    private String inserer(Contexte ctx, Periode p, DepotEquipagesPort.Entete e, List<DepotEquipagesPort.NouvellePlace> places) {
        String id = sequence.suivant("eeqp");
        depot.inserer(ctx.tenantId(), id, p, e, ctx.maintenant());
        IntStream.range(0, places.size()).forEach(i -> depot.insererPlace(ctx.tenantId(), sequence.suivant("epla"), id, i + 1, places.get(i), ctx.maintenant()));
        return id;
    }

    /** Un quart ponctuel ou ad hoc (E-GN-3, E-GR-6, E-GR-13) : aucun poste, une composition libre. */
    public Equipage creer(Contexte ctx, Map<String, Object> entree) {
        Periode p = periodeModifiable(ctx, entree.get("periodeId") == null ? "" : String.valueOf(entree.get("periodeId")));
        String date = Planification.dateDe(entree.get("date"), "date");
        if (date.compareTo(p.debut()) < 0 || date.compareTo(p.fin()) > 0) {
            throw new EffectifsInvalideException("la date doit être dans la période (" + p.debut() + " au " + p.fin() + ")");
        }
        Planification.Plage plage = Planification.creneauDe(entree.get("debut"), entree.get("fin"));
        List<Planification.Composition> composition = Planification.compositionDe(entree.get("composition"));
        composition.forEach(l -> exiger(ctx, TypeReferentiel.CORPS_EMPLOI, l.corpsEmploiCode()));
        String natureCode = entree.get("natureCode") == null ? "" : String.valueOf(entree.get("natureCode"));
        exiger(ctx, TypeReferentiel.NATURES_QUART, natureCode);
        String plageCode = entree.get("plageCode") instanceof String s && !s.isEmpty() ? s : null;
        if (plageCode != null) {
            exiger(ctx, TypeReferentiel.PLAGES_HORAIRES, plageCode);
        }
        String milieuId = lire.milieux().exiger(ctx, entree.get("milieuId") == null ? "" : String.valueOf(entree.get("milieuId"))).id();
        int total = composition.stream().mapToInt(Planification.Composition::nombre).sum();
        DepotEquipagesPort.Entete e = new DepotEquipagesPort.Entete(date, plage.debut(), plage.fin(), natureCode, plageCode, milieuId,
                Planification.modeAttributionDe(entree.getOrDefault("modeAttribution", Planification.ModeAttribution.MANUEL.name())),
                Planification.minimumDe(entree.get("minimumOperationnel"), total), noteDe(entree.get("note")));
        List<DepotEquipagesPort.NouvellePlace> places = new ArrayList<>();
        composition.forEach(l -> IntStream.range(0, l.nombre()).forEach(i -> places.add(new DepotEquipagesPort.NouvellePlace(l.corpsEmploiCode(), null, null))));
        String id = inserer(ctx, p, e, places);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("equipage.cree", "equipage", id, p.uniteCode(), null, Map.of("entete", e, "composition", composition)));
        return lire.equipages().lire(ctx, id);
    }

    private static String noteDe(Object v) {
        return v instanceof String s && !s.trim().isEmpty() ? s.trim() : null;
    }

    private record Groupe(String plageCode, String milieuId, String natureCode) { }

    /**
     * Génère les quarts d'une période depuis les postes publiés de son unité (E-CH-2). Les postes de même milieu,
     * même plage et même nature le même jour forment <strong>un</strong> équipage. Idempotent.
     */
    public Map<String, Object> genererDepuisPostes(Contexte ctx, String periodeId) {
        Periode p = periodeModifiable(ctx, periodeId);
        List<Poste> postes = lire.postes().lister(ctx, null, p.uniteCode()).stream().filter(x -> x.etat() != Planification.EtatPoste.BROUILLON).toList();
        Map<String, Planification.Plage> plages = new HashMap<>();
        lire.referentiels().lister(ctx, TypeReferentiel.PLAGES_HORAIRES.code(), null, false).forEach(v -> plages.put(v.code(),
                new Planification.Plage(((Number) v.attributs().get("debut")).intValue(), ((Number) v.attributs().get("fin")).intValue())));
        int[] comptes = {0, 0};
        for (String jour : Planification.joursEntre(p.debut(), p.fin())) {
            groupesDuJour(ctx, postes, plages, jour).forEach((g, places) -> {
                Planification.Plage plage = plages.get(g.plageCode());
                inserer(ctx, p, new DepotEquipagesPort.Entete(jour, plage.debut(), plage.fin(), g.natureCode(), g.plageCode(), g.milieuId(),
                        Planification.ModeAttribution.AUTOMATISE, places.size(), null), places);
                comptes[0]++;
                comptes[1] += places.size();
            });
        }
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("equipagesCrees", comptes[0]);
        resultat.put("placesCreees", comptes[1]);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("periode.generee", "periode", periodeId, p.uniteCode(), null, resultat));
        return resultat;
    }

    private Map<Groupe, List<DepotEquipagesPort.NouvellePlace>> groupesDuJour(Contexte ctx, List<Poste> postes, Map<String, Planification.Plage> plages, String jour) {
        Map<Groupe, List<DepotEquipagesPort.NouvellePlace>> groupes = new LinkedHashMap<>();
        for (Poste poste : postes) {
            String plage = Planification.plageDuJour(poste.cycle(), poste.debutCycle(), jour);
            if (plage == null || !plages.containsKey(plage) || depot.posteDejaGenere(ctx.tenantId(), poste.id(), jour)) {
                continue;
            }
            groupes.computeIfAbsent(new Groupe(plage, poste.milieuId(), poste.natureCode()), k -> new ArrayList<>())
                    .add(new DepotEquipagesPort.NouvellePlace(poste.corpsEmploiCode(), poste.id(), poste.titulaireId()));
        }
        return groupes;
    }

    /** Les changements demandés ; seules les clés présentes changent. */
    private record Changements(Planification.Plage plage, String milieuId, Planification.ModeAttribution mode, Double minimum, boolean noteFournie,
                               String note) { }

    private Changements changementsDe(Contexte ctx, Map<String, Object> brut) {
        Planification.Plage plage = brut.containsKey("debut") || brut.containsKey("fin") ? Planification.creneauDe(brut.get("debut"), brut.get("fin")) : null;
        String milieuId = brut.containsKey("milieuId") ? lire.milieux().exiger(ctx, String.valueOf(brut.get("milieuId"))).id() : null;
        Planification.ModeAttribution mode = brut.containsKey("modeAttribution") ? Planification.modeAttributionDe(brut.get("modeAttribution")) : null;
        Double minimum = brut.containsKey("minimumOperationnel") ? PlanificationEntrees.nombre(brut.get("minimumOperationnel")) : null;
        if (plage == null && milieuId == null && mode == null && minimum == null && !brut.containsKey("note")) {
            throw new EffectifsInvalideException("aucun changement demandé");
        }
        return new Changements(plage, milieuId, mode, minimum, brut.containsKey("note"), noteDe(brut.get("note")));
    }

    /** Applique des changements, sans journaliser (l'appelant le fait). */
    private Equipage appliquer(Contexte ctx, Equipage e, Changements c) {
        periodeModifiable(ctx, e.periodeId());
        int minimum = c.minimum() == null ? e.minimumOperationnel() : Planification.minimumDe(c.minimum(), e.places().size());
        int debut = c.plage() == null ? e.debut() : c.plage().debut();
        int fin = c.plage() == null ? e.fin() : c.plage().fin();
        if (c.plage() != null) {
            verifierPersonnes(ctx, e, new Planification.Creneau(e.date(), debut, fin));
        }
        depot.modifier(ctx.tenantId(), e.id(), new DepotEquipagesPort.Entete(e.date(), debut, fin, e.natureCode(), e.plageCode(),
                c.milieuId() == null ? e.milieuId() : c.milieuId(), c.mode() == null ? e.modeAttribution() : c.mode(), minimum,
                c.noteFournie() ? c.note() : e.note()));
        return lire.equipages().lire(ctx, e.id());
    }

    /** De nouvelles heures ne doivent pas faire chevaucher un quart déjà tenu par une des personnes affectées. */
    private void verifierPersonnes(Contexte ctx, Equipage e, Planification.Creneau nouveau) {
        for (Equipage.Place pl : e.places()) {
            if (pl.ressourceId() != null && Planification.chevaucheUnQuart(depot.creneauxDe(ctx.tenantId(), pl.ressourceId(), pl.id()), nouveau)) {
                throw new ConflitException(pl.ressourceNom() + " tient déjà un quart qui chevaucherait ces heures");
            }
        }
    }

    /** Ajustement journalier d'un quart (E-GR-5), y compris d'une période publiée. */
    public Equipage modifier(Contexte ctx, String id, Map<String, Object> entree) {
        Equipage avant = lire.equipages().lire(ctx, id);
        Changements c = changementsDe(ctx, entree);
        Equipage apres = appliquer(ctx, avant, c);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("equipage.modifie", "equipage", id, avant.uniteCode(), sansPlaces(avant), entree));
        return apres;
    }

    /** Modification en lot (E-CH-3) : tout ou rien — la transaction de l'appel annule un lot à moitié appliqué. */
    @SuppressWarnings("unchecked")
    public List<Equipage> modifierEnLot(Contexte ctx, Map<String, Object> entree) {
        if (!(entree.get("ids") instanceof List<?> ids) || ids.isEmpty()) {
            throw new EffectifsInvalideException("« ids » : au moins un quart");
        }
        Map<String, Object> brut = entree.get("changements") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Changements c = changementsDe(ctx, brut);
        List<Equipage> equipages = ids.stream().map(id -> lire.equipages().lire(ctx, String.valueOf(id))).toList();
        List<Equipage> resultat = new ArrayList<>();
        for (Equipage e : equipages) {
            resultat.add(appliquer(ctx, e, c));
            journal.ecrire(ctx, JournalEffectifs.Consignation.de("equipage.modifie.lot", "equipage", e.id(), e.uniteCode(), sansPlaces(e), brut));
        }
        return resultat;
    }

    private static Map<String, Object> sansPlaces(Equipage e) {
        Map<String, Object> m = LectureEquipages.vue(e);
        m.remove("places");
        return m;
    }
}
