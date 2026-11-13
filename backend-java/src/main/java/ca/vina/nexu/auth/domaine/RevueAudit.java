package ca.vina.nexu.auth.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Revue indépendante des registres (AO 2026-PLAT-GA, exigence 39) — la part décisionnelle de
 * {@code backend/src/auth/audit.ts} : validation d'une revue, échéance à 90 jours, vérification
 * de la chaîne d'empreintes sur des lignes fournies.
 */
public final class RevueAudit {

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Duration PERIODE = Duration.ofDays(90);

    private RevueAudit() {
    }

    /** Une période validée, ses bornes ISO et le constat rogné. */
    public record Demande(String du, String au, String constat) {
        public String debutIso() {
            return du + "T00:00:00.000Z";
        }

        public String finIso() {
            return au + "T23:59:59.999Z";
        }
    }

    /** Valide une demande de revue ; les erreurs sont jointes par « ; », mot pour mot celles du Node. */
    public static Demande valider(String du, String au, String constat) {
        List<String> erreurs = new ArrayList<>();
        boolean duOk = du != null && DATE.matcher(du).matches();
        boolean auOk = au != null && DATE.matcher(au).matches();
        if (!duOk) {
            erreurs.add("du : date AAAA-MM-JJ attendue");
        }
        if (!auOk) {
            erreurs.add("au : date AAAA-MM-JJ attendue");
        }
        if (duOk && auOk && au.compareTo(du) < 0) {
            erreurs.add("la fin de période précède son début");
        }
        String c = constat == null ? "" : constat.trim();
        if (c.length() < 10) {
            erreurs.add("constat requis (10 caractères au moins) : ce qui a été vérifié et ce qui a été trouvé");
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        return new Demande(du, au, c);
    }

    /** Une revue est due tous les 90 jours ; sans revue, depuis la première entrée du journal. */
    public static Map<String, Object> echeance(String derniereRevue, String premiereEntree, Instant maintenant) {
        Map<String, Object> r = new LinkedHashMap<>();
        String reference = derniereRevue != null ? derniereRevue : premiereEntree;
        r.put("derniere", derniereRevue);
        if (reference == null) {
            r.put("prochaineAvant", null);
            r.put("enRetard", false);
            return r;
        }
        String prochaine = Instant.parse(reference).plus(PERIODE).toString();
        if (!prochaine.contains(".")) {
            prochaine = prochaine.replace("Z", ".000Z");
        }
        r.put("prochaineAvant", prochaine);
        r.put("enRetard", prochaine.compareTo(maintenant.toString()) < 0);
        return r;
    }

    /** Une ligne du journal avec l'empreinte qu'elle porte ({@code null} pour une ligne d'avant la chaîne). */
    public record Ligne(EvenementAudit evenement, String empreinte) {
    }

    /** Recalcule la chaîne ; la première ligne dont l'empreinte ne correspond pas est nommée. */
    public static Map<String, Object> integrite(List<Ligne> lignes, java.util.function.BiFunction<String, EvenementAudit, String> empreinteDe) {
        String precedente = null;
        int chainees = 0;
        int nonChainees = 0;
        Map<String, Object> rupture = null;
        for (Ligne l : lignes) {
            if (l.empreinte() == null) {
                nonChainees++;
                continue;
            }
            String attendue = empreinteDe.apply(precedente, l.evenement());
            if (!attendue.equals(l.empreinte()) && rupture == null) {
                rupture = new LinkedHashMap<>();
                rupture.put("id", l.evenement().id());
                rupture.put("at", l.evenement().at());
            }
            precedente = l.empreinte();
            chainees++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("entrees", lignes.size());
        r.put("chainees", chainees);
        r.put("nonChainees", nonChainees);
        r.put("intacte", rupture == null);
        r.put("premiereRupture", rupture);
        r.put("derniereEmpreinte", precedente);
        return r;
    }
}
