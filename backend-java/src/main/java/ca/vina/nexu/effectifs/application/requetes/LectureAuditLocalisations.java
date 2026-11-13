package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PointageEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <strong>L'audit des localisations de présence</strong> (E-GP-14) — jumeau de {@code auditLocalisations} et
 * {@code auditLocalisationsCsv} ({@code presence/service.ts}).
 *
 * <p>Ce qui est consigné est le <b>verdict</b> de chaque mécanisme de preuve et la <b>distance</b> au milieu, jamais les
 * coordonnées : la <i>Loi 25</i> n'admet que le nécessaire, la distance suffit à juger le lieu, et la position permettrait de
 * reconstituer des déplacements hors du travail. L'audit porte donc sur où, quand, à quelle distance et avec quel verdict.</p>
 */
public class LectureAuditLocalisations {

    /** Le filtre de l'audit ; chaque critère nul est ignoré. */
    public record Filtre(String du, String au, String milieuId, boolean ecartSeul) { }

    static final String LOI_25 = "La **position n'est pas conservée** : seules la distance au milieu et le verdict de chaque mécanisme le sont "
            + "(Loi 25, minimisation). La distance suffit à juger le lieu ; la position permettrait de reconstituer des déplacements hors du travail.";
    /** Les colonnes du CSV, dans l'ordre du Node : un vérificateur qui compare les deux exports lit les mêmes en-têtes. */
    enum ColonneCsv {
        HORODATAGE("horodatage"), NOM("matriculeOuNom"), SENS("sens"), MILIEU("milieu"), UNITE("unite"), RESULTAT("resultat"),
        ECART("ecart"), DISTANCE("distanceMetres"), MECANISMES("mecanismes"), ORIGINE("origine"), STATUT("statut");

        private final String entete;

        ColonneCsv(String entete) {
            this.entete = entete;
        }

        String entete() {
            return entete;
        }
    }

    /** Ce qu'un mécanisme de preuve a donné : satisfait, fourni mais hors du lieu, ou jamais fourni. */
    enum SortPreuve {
        OK("ok"), HORS("hors"), ABSENTE("absente");

        private final String code;

        SortPreuve(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        static SortPreuve de(boolean satisfaite, boolean fournie) {
            if (satisfaite) {
                return OK;
            }
            return fournie ? HORS : ABSENTE;
        }
    }

    private static final String SEPARATEUR = ";";
    private static final String GUILLEMET = "\"";

    private final LecturePresenceEffectifs presence;

    public LectureAuditLocalisations(LecturePresenceEffectifs presence) {
        this.presence = presence;
    }

    /** Les lignes de l'audit et les trois constats qui les résument. */
    public Map<String, Object> auditer(Contexte ctx, Filtre f) {
        List<Map<String, Object>> lignes = lignes(ctx, f);
        long ecarts = lignes.stream().filter(l -> l.get("ecart") != null).count();
        List<String> motifs = new ArrayList<>();
        motifs.add(lignes.size() + " déclaration(s) de présence auditées"
                + (f.milieuId() == null ? "" : " au milieu « " + presence.libelleMilieu(ctx.tenantId(), f.milieuId()) + " »"));
        motifs.add(ecarts == 0 ? "Aucun écart de lieu" : ecarts + " écart(s) de lieu");
        motifs.add(LOI_25);
        Map<String, Object> vue = new LinkedHashMap<>();
        vue.put("lignes", lignes);
        vue.put("motifs", motifs);
        return vue;
    }

    /** L'audit au format qu'un vérificateur emporte : chaque valeur entre guillemets, le guillemet doublé. */
    public String enCsv(Contexte ctx, Filtre f) {
        Stream<String> corps = lignes(ctx, f).stream().map(LectureAuditLocalisations::ligneCsv);
        return Stream.concat(Stream.of(rangee(Stream.of(ColonneCsv.values()).map(ColonneCsv::entete).toList())), corps).collect(Collectors.joining("\n"));
    }

    private List<Map<String, Object>> lignes(Contexte ctx, Filtre f) {
        return presence.duPerimetre(ctx, new LecturePointagesEffectifsPort.Filtre(null, f.du(), f.au(), f.milieuId()), Set.of()).stream()
                .filter(p -> !f.ecartSeul() || p.constat().ecart() != null).map(p -> ligne(ctx.tenantId(), p)).toList();
    }

    private Map<String, Object> ligne(String tenantId, PointageEffectifs p) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("pointageId", p.id());
        l.put("ressourceId", p.ressourceId());
        l.put("nom", p.nom());
        l.put("sens", p.sens());
        l.put("horodatage", p.horodatage());
        l.put("milieuId", p.lieu().milieuId());
        l.put("milieu", presence.libelleMilieu(tenantId, p.lieu().milieuId()));
        l.put("uniteCode", p.lieu().uniteCode());
        l.put("resultat", p.constat().resultat());
        l.put("ecart", p.constat().ecart());
        l.put("distanceMetres", p.constat().distanceMetres());
        l.put("mecanismes", presence.preuves(tenantId, p.id()).stream().map(LectureAuditLocalisations::mecanisme).toList());
        l.put("origine", p.origine());
        l.put("statut", p.suivi().statut());
        return l;
    }

    private static Map<String, Object> mecanisme(PresenceEffectifs.VerdictPreuve v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mecanisme", v.mecanisme());
        m.put("exigee", v.exigee());
        m.put("fournie", v.fournie());
        m.put("satisfaite", v.satisfaite());
        m.put("detail", v.detail());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static String ligneCsv(Map<String, Object> l) {
        String mecanismes = ((List<Map<String, Object>>) l.get("mecanismes")).stream().map(LectureAuditLocalisations::verdictCsv)
                .collect(Collectors.joining(" "));
        return rangee(Stream.of(l.get("horodatage"), l.get("nom"), l.get("sens"), l.get("milieu"), l.get("uniteCode"), l.get("resultat"),
                l.get("ecart"), l.get("distanceMetres"), mecanismes, l.get("origine"), l.get("statut")).toList());
    }

    /** {@code GEOLOCALISATION:ok}, {@code :hors} (fournie mais non satisfaite) ou {@code :absente}. */
    private static String verdictCsv(Map<String, Object> m) {
        SortPreuve sort = SortPreuve.de(Boolean.TRUE.equals(m.get("satisfaite")), Boolean.TRUE.equals(m.get("fournie")));
        return m.get("mecanisme") + ":" + sort.code();
    }

    private static String rangee(List<?> valeurs) {
        return valeurs.stream().map(LectureAuditLocalisations::echapper).collect(Collectors.joining(SEPARATEUR));
    }

    private static String echapper(Object v) {
        return GUILLEMET + Objects.toString(v, "").replace(GUILLEMET, GUILLEMET + GUILLEMET) + GUILLEMET;
    }
}
