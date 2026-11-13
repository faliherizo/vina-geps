package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Quota;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les lectures des équipages et de leurs places, et le <strong>comblement</strong> d'une période (E-GN-2, E-GR-1,
 * E-GR-8) — jumeau de la partie lecture de {@code serviceEquipages.ts}. Tout ce qui se résume se calcule ici.
 */
public class LectureEquipages {

    /** Une place et son équipage. */
    public record PlaceEtEquipage(Equipage.Place place, Equipage equipage) { }

    private final DepotEquipagesPort depot;
    private final LecturePeriodes periodes;
    private final LectureQuotas quotas;

    public LectureEquipages(DepotEquipagesPort depot, LecturePeriodes periodes, LectureQuotas quotas) {
        this.depot = depot;
        this.periodes = periodes;
        this.quotas = quotas;
    }

    public Equipage lire(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("équipage « " + id + " » introuvable"));
    }

    public List<Equipage> lister(Contexte ctx, String periodeId, String date, String statut) {
        periodes.exiger(ctx, periodeId, Perimetres.Niveau.LECTURE);
        String jour = date == null || date.isEmpty() ? null : Planification.dateDe(date, "date");
        return depot.lister(ctx.tenantId(), periodeId, jour).stream()
                .filter(e -> statut == null || statut.isEmpty() || e.statut().name().equals(statut)).toList();
    }

    /** Une place et son équipage — lus par les lots qui cherchent des candidats (R4, R5). */
    public PlaceEtEquipage lirePlace(Contexte ctx, String placeId) {
        String equipageId = depot.equipageDeLaPlace(ctx.tenantId(), placeId)
                .orElseThrow(() -> new IntrouvableException("place « " + placeId + " » introuvable"));
        Equipage e = lire(ctx, equipageId);
        return new PlaceEtEquipage(e.places().stream().filter(p -> p.id().equals(placeId)).findFirst().orElseThrow(), e);
    }

    public List<Planification.Creneau> creneauxDe(Contexte ctx, String ressourceId, String saufPlaceId) {
        return depot.creneauxDe(ctx.tenantId(), ressourceId, saufPlaceId);
    }

    /** La forme rendue d'un équipage : ses champs, ses places, et ce qui s'en calcule. */
    public static Map<String, Object> vue(Equipage e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("periodeId", e.periodeId());
        m.put("uniteCode", e.uniteCode());
        m.put("date", e.date());
        m.put("debut", e.debut());
        m.put("fin", e.fin());
        m.put("natureCode", e.natureCode());
        m.put("plageCode", e.plageCode());
        m.put("milieuId", e.milieuId());
        m.put("modeAttribution", e.modeAttribution());
        m.put("minimumOperationnel", e.minimumOperationnel());
        m.put("note", e.note());
        m.put("places", e.places());
        m.put("pourvues", e.pourvues());
        m.put("statut", e.statut());
        return m;
    }

    /** La forme rendue d'un quota, avec celui qui est en vigueur maintenant. */
    public static Map<String, Object> vue(Quota q, String maintenant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cle", q.cle());
        m.put("uniteCode", q.uniteCode());
        m.put("plageCode", q.plageCode());
        m.put("natureCode", q.natureCode());
        m.put("versions", q.versions());
        m.put("surcharges", q.surcharges());
        m.put("enVigueur", q.enVigueur(maintenant).orElse(null));
        return m;
    }

    public Map<String, Object> comblement(Contexte ctx, String periodeId) {
        Periode p = periodes.exiger(ctx, periodeId, Perimetres.Niveau.LECTURE);
        List<Equipage> equipages = lister(ctx, periodeId, null, null);
        int places = equipages.stream().mapToInt(e -> e.places().size()).sum();
        int pourvues = equipages.stream().mapToInt(Equipage::pourvues).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("periodeId", periodeId);
        m.put("equipages", equipages.size());
        m.put("places", places);
        m.put("pourvues", pourvues);
        m.put("taux", Planification.tauxPourcent(pourvues, places));
        m.put("sousMinimum", equipages.stream().filter(e -> e.statut() == Planification.StatutOperationnel.SOUS_MINIMUM).map(LectureEquipages::sousMinimum).toList());
        m.put("quotas", quotas.deUnite(ctx, p.uniteCode()).stream().map(q -> quotaParJour(q, p, equipages)).toList());
        return m;
    }

    private static Map<String, Object> sousMinimum(Equipage e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("equipageId", e.id());
        m.put("date", e.date());
        m.put("debut", e.debut());
        m.put("fin", e.fin());
        m.put("milieuId", e.milieuId());
        m.put("pourvues", e.pourvues());
        m.put("minimum", e.minimumOperationnel());
        m.put("mode", e.modeAttribution());
        return m;
    }

    private static Map<String, Object> quotaParJour(Quota q, Periode p, List<Equipage> equipages) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cle", q.cle());
        m.put("plageCode", q.plageCode());
        m.put("natureCode", q.natureCode());
        m.put("jours", Planification.joursEntre(p.debut(), p.fin()).stream().map(date -> jour(q, date, equipages)).toList());
        return m;
    }

    private static Map<String, Object> jour(Quota q, String date, List<Equipage> equipages) {
        // Le quota d'un jour se lit à midi : une surcharge posée pour une journée la couvre.
        Optional<Planification.QuotaEffectif> effectif = q.enVigueur(date + "T12:00:00.000Z");
        long operationnels = equipages.stream().filter(e -> e.date().equals(date) && e.statut() == Planification.StatutOperationnel.OPERATIONNEL
                && (q.plageCode() == null || q.plageCode().equals(e.plageCode())) && (q.natureCode() == null || q.natureCode().equals(e.natureCode()))).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("quota", effectif.map(Planification.QuotaEffectif::quota).orElse(null));
        m.put("origine", effectif.map(Planification.QuotaEffectif::origine).orElse(null));
        m.put("operationnels", operationnels);
        m.put("atteint", effectif.isEmpty() || operationnels >= effectif.get().quota());
        return m;
    }
}
