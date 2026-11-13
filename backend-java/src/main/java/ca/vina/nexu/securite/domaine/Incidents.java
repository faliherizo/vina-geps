package ca.vina.nexu.securite.domaine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Registre des incidents de confidentialité (Loi 25, art. 3.5 et 3.8 ; écran ÉC-8) — port fidèle
 * de {@code securite/incidents.ts}. Validation nommée champ par champ, notifications, ce qu'un
 * incident clos laisse modifier, la notification attendue (écrite, jamais bloquante) et le CSV du
 * registre. Aucune E/S.
 */
public final class Incidents {

    private Incidents() {
    }

    public record Code(String code, String libelle) { }

    public static final List<Code> NATURES = List.of(
            new Code("acces_non_autorise", "Accès non autorisé"), new Code("utilisation_non_autorisee", "Utilisation non autorisée"),
            new Code("communication_non_autorisee", "Communication non autorisée"), new Code("perte", "Perte"), new Code("autre", "Autre"));
    public static final List<Code> RENSEIGNEMENTS = List.of(
            new Code("identite", "Identité"), new Code("coordonnees", "Coordonnées"), new Code("emploi", "Emploi"), new Code("absences", "Absences"),
            new Code("remuneration", "Rémunération"), new Code("documents", "Documents"), new Code("autre", "Autre"));
    public static final List<Code> GRAVITES = List.of(
            new Code("faible", "Faible"), new Code("moderee", "Modérée"),
            new Code("elevee", "Élevée — risque de préjudice sérieux : notification à la CAI et aux personnes"));
    public static final List<String> CHAMPS_MODIFIABLES_CLOS = List.of("notifieCai", "notifieCaiLe", "notifiePersonnes", "notifiePersonnesLe");
    public static final int CONSERVATION_ANNEES = 5;

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** Port : le registre des incidents de confidentialité et les noms d'établissements qui l'accompagnent. */
    public interface Registre {
        List<Incident> lister(String tenantId, String statut, String gravite);

        Map<String, String> nomsEtablissements();

        Incident consigner(String tenantId, String acteurId, Map<String, Object> saisie, String instant);

        Incident modifier(String tenantId, String id, Map<String, Object> saisie);

        Incident clore(String tenantId, String id, String instant);
    }

    public static final String MODULE = "confidentialite";
    public static final String[] ROLES_ADMIN = {"admin", "superadmin"};

    /** Le nom du fichier remis : le registre, filtré par statut et gravité s'il y a lieu. */
    public static String nomFichier(String statut, String gravite, String ext) {
        return "registre-incidents" + (statut == null || statut.isBlank() ? "" : "-" + statut) + (gravite == null || gravite.isBlank() ? "" : "-" + gravite) + "." + ext;
    }

    public record Incident(String id, String tenantId, String survenuLe, String decouvertLe, String nature, String description,
                           List<String> renseignements, Integer personnesTouchees, String gravite, String mesures,
                           String responsableId, String responsableNom, boolean notifieCai, String notifieCaiLe,
                           boolean notifiePersonnes, String notifiePersonnesLe, String statut, String closLe,
                           String consigneLe, String consignePar) { }

    public record Attendue(boolean cai, boolean personnes) { }

    private static List<String> codes(List<Code> liste) {
        return liste.stream().map(Code::code).toList();
    }

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean estDate(Object v) {
        return v instanceof String s && DATE.matcher(s).matches();
    }

    /** Les manquements d'une saisie, nommés, dans l'ordre du Node. */
    public static List<String> valider(Map<String, Object> i) {
        List<String> erreurs = new ArrayList<>();
        if (!estDate(i.get("survenuLe"))) {
            erreurs.add("survenuLe : date AAAA-MM-JJ requise");
        }
        if (!estDate(i.get("decouvertLe"))) {
            erreurs.add("decouvertLe : date AAAA-MM-JJ requise");
        } else if (estDate(i.get("survenuLe")) && texte(i.get("decouvertLe")).compareTo(texte(i.get("survenuLe"))) < 0) {
            erreurs.add("la découverte ne peut précéder la survenue");
        }
        if (!codes(NATURES).contains(texte(i.get("nature")))) {
            erreurs.add("nature requise : " + String.join(", ", codes(NATURES)));
        }
        int description = texte(i.get("description")).trim().length();
        if (description < 20 || description > 5000) {
            erreurs.add("description requise (20 à 5000 caractères) : ce qui s'est passé, pour la CAI");
        }
        if (!(i.get("renseignements") instanceof List<?> rens) || rens.isEmpty()) {
            erreurs.add("renseignements visés : au moins un");
        } else {
            List<String> inconnus = rens.stream().map(String::valueOf).filter(r -> !codes(RENSEIGNEMENTS).contains(r)).toList();
            if (!inconnus.isEmpty()) {
                erreurs.add("renseignements inconnus : " + String.join(", ", inconnus));
            }
        }
        Object personnes = i.get("personnesTouchees");
        if (personnes != null && !(personnes instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue()) && n.doubleValue() >= 0)) {
            erreurs.add("personnesTouchees : entier ≥ 0, ou null si inconnu");
        }
        if (!codes(GRAVITES).contains(texte(i.get("gravite")))) {
            erreurs.add("gravite requise : " + String.join(", ", codes(GRAVITES)));
        }
        int mesures = texte(i.get("mesures")).trim().length();
        if (mesures < 10 || mesures > 5000) {
            erreurs.add("mesures prises requises (10 à 5000 caractères)");
        }
        erreurs.addAll(validerNotifications(i));
        return erreurs;
    }

    /** Une date mal formée, ou une date sans la case, sont refusées. */
    public static List<String> validerNotifications(Map<String, Object> i) {
        List<String> erreurs = new ArrayList<>();
        for (String[] paire : new String[][] {{"notifieCai", "notifieCaiLe"}, {"notifiePersonnes", "notifiePersonnesLe"}}) {
            Object date = i.get(paire[1]);
            boolean posee = date != null && !texte(date).isEmpty();
            if (posee && !estDate(date)) {
                erreurs.add(paire[1] + " : date AAAA-MM-JJ attendue");
            }
            if (posee && !Boolean.TRUE.equals(i.get(paire[0]))) {
                erreurs.add(paire[1] + " : une date sans notification déclarée");
            }
        }
        return erreurs;
    }

    /** Ce qu'un incident clos refuse (règle INC-3) : tout sauf ses notifications. */
    public static List<String> champsInterditsSiClos(Map<String, Object> saisie) {
        return saisie.keySet().stream().filter(k -> !CHAMPS_MODIFIABLES_CLOS.contains(k)).toList();
    }

    /** Règle INC-2 : gravité élevée sans notification → attendue, écrite, jamais bloquante. */
    public static Attendue notificationAttendue(String gravite, boolean notifieCai, boolean notifiePersonnes) {
        boolean elevee = "elevee".equals(gravite);
        return new Attendue(elevee && !notifieCai, elevee && !notifiePersonnes);
    }

    public static String libelle(List<Code> liste, String code) {
        return liste.stream().filter(c -> c.code().equals(code)).map(Code::libelle).findFirst().orElse(code);
    }

    public static String libelleGravite(String code) {
        return libelle(GRAVITES, code).split(" — ")[0];
    }

    private static String cellule(Object v) {
        return "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"";
    }

    /** Le registre en CSV (UTF-8 avec BOM, « ; ») : ce qu'on remet à la CAI — règle INC-4. */
    /** Un incident en trois lignes, telles que le registre PDF les écrit ({@code exportPdf} du Node). */
    public static List<String> lignesRegistre(Incident i, String nomEtablissement, boolean avecEtablissement) {
        Attendue a = notificationAttendue(i.gravite(), i.notifieCai(), i.notifiePersonnes());
        String titre = i.survenuLe() + " — " + libelle(NATURES, i.nature()) + " — gravité " + libelleGravite(i.gravite()).toLowerCase() + " — " + i.statut()
                + (i.closLe() != null ? " le " + i.closLe() : "") + (avecEtablissement ? " — " + (nomEtablissement == null ? i.tenantId() : nomEtablissement) : "");
        String details = "Découvert le " + i.decouvertLe() + " · renseignements : " + String.join(", ", i.renseignements().stream().map(r -> libelle(RENSEIGNEMENTS, r)).toList())
                + " · personnes touchées : " + (i.personnesTouchees() == null ? "inconnu" : String.valueOf(i.personnesTouchees())) + " · responsable : " + (i.responsableNom() == null ? "—" : i.responsableNom());
        String notifications = "CAI notifiée : " + (i.notifieCai() ? "oui" + (i.notifieCaiLe() != null ? " le " + i.notifieCaiLe() : "") : "non" + (a.cai() ? " — notification attendue" : ""))
                + " · personnes notifiées : " + (i.notifiePersonnes() ? "oui" + (i.notifiePersonnesLe() != null ? " le " + i.notifiePersonnesLe() : "") : "non" + (a.personnes() ? " — notification attendue" : ""));
        return List.of(titre, details, "Description : " + i.description(), "Mesures : " + i.mesures(), notifications);
    }

    public static String registreCsv(List<Incident> incidents, Map<String, String> nomsEtablissements, boolean avecEtablissement) {
        List<String> entetes = new ArrayList<>();
        if (avecEtablissement) {
            entetes.add("Établissement");
        }
        entetes.addAll(List.of("Survenu le", "Découvert le", "Nature", "Gravité", "Renseignements visés", "Personnes touchées", "Description", "Mesures prises",
                "Responsable", "Notifié CAI", "Notifié CAI le", "Personnes notifiées", "Personnes notifiées le", "Statut", "Clos le", "Consigné le"));
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(";", entetes.stream().map(Incidents::cellule).toList()));
        for (Incident i : incidents) {
            List<String> cellules = new ArrayList<>();
            if (avecEtablissement) {
                cellules.add(cellule(nomsEtablissements.getOrDefault(i.tenantId(), i.tenantId())));
            }
            for (Object v : List.of(i.survenuLe(), i.decouvertLe(), libelle(NATURES, i.nature()), libelleGravite(i.gravite()),
                    String.join(", ", i.renseignements().stream().map(r -> libelle(RENSEIGNEMENTS, r)).toList()),
                    i.personnesTouchees() == null ? "inconnu" : String.valueOf(i.personnesTouchees()), i.description(), i.mesures(),
                    i.responsableNom() == null ? "" : i.responsableNom(), i.notifieCai() ? "oui" : "non", i.notifieCaiLe() == null ? "" : i.notifieCaiLe(),
                    i.notifiePersonnes() ? "oui" : "non", i.notifiePersonnesLe() == null ? "" : i.notifiePersonnesLe(), i.statut(),
                    i.closLe() == null ? "" : i.closLe(), i.consigneLe().substring(0, 10))) {
                cellules.add(cellule(v));
            }
            sb.append("\r\n").append(String.join(";", cellules));
        }
        return sb.append("\r\n").toString();
    }

    /** La forme servie par l'API : l'incident, le nom de l'établissement, la notification attendue. */
    public static Map<String, Object> vue(Incident i, String nomEtablissement) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.id()); m.put("tenantId", i.tenantId()); m.put("survenuLe", i.survenuLe()); m.put("decouvertLe", i.decouvertLe());
        m.put("nature", i.nature()); m.put("description", i.description()); m.put("renseignements", i.renseignements());
        m.put("personnesTouchees", i.personnesTouchees()); m.put("gravite", i.gravite()); m.put("mesures", i.mesures());
        m.put("responsableId", i.responsableId()); m.put("responsableNom", i.responsableNom());
        m.put("notifieCai", i.notifieCai()); m.put("notifieCaiLe", i.notifieCaiLe()); m.put("notifiePersonnes", i.notifiePersonnes()); m.put("notifiePersonnesLe", i.notifiePersonnesLe());
        m.put("statut", i.statut()); m.put("closLe", i.closLe()); m.put("consigneLe", i.consigneLe()); m.put("consignePar", i.consignePar());
        m.put("nomEtablissement", nomEtablissement);
        Attendue a = notificationAttendue(i.gravite(), i.notifieCai(), i.notifiePersonnes());
        m.put("attendue", Map.of("cai", a.cai(), "personnes", a.personnes()));
        return m;
    }
}
