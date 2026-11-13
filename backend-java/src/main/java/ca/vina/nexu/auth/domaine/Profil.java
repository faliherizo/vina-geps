package ca.vina.nexu.auth.domaine;

import java.text.Collator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Le profil d'une personne connectée, tel que {@code /api/auth/profile} le rend : deux blocs
 * volontairement séparés — ce qu'elle peut <strong>changer</strong>, et ce qu'elle peut seulement
 * <strong>consulter</strong>. Les mélanger produit des formulaires où l'on saisit des champs qui
 * seront refusés ; le serveur reste l'autorité ({@link #MODIFIABLES}).
 */
public final class Profil {

    private Profil() {
    }

    public static final Set<String> MODIFIABLES = Set.of("firstName", "lastName", "email", "phone", "address", "dateOfBirth");
    private static final Pattern COURRIEL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** La fiche employé, telle que la paie la tient : identité modifiable et dossier de l'employeur. */
    public record Employe(String id, String firstName, String lastName, String email, String phone, String address, String dateOfBirth, String jobTitle,
                          String role, String departmentId, String managerId, String hireDate, String status, Double weeklyCapacityHours, String workerType,
                          String payType, Double annualSalary, Double hourlyRate, String sin, boolean isTeacher) {
    }

    /** Un lien hiérarchique : qui relève de qui. */
    public record Lien(String id, String managerId) {
    }

    private static String ou(String v) {
        return v == null ? "" : v;
    }

    /** Ce que la personne peut changer. */
    public static Map<String, Object> modifiable(Employe e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", e.id());
        m.put("firstName", ou(e.firstName()));
        m.put("lastName", ou(e.lastName()));
        m.put("email", ou(e.email()));
        m.put("phone", ou(e.phone()));
        m.put("address", ou(e.address()));
        m.put("dateOfBirth", ou(e.dateOfBirth()));
        m.put("jobTitle", ou(e.jobTitle()));
        return m;
    }

    /**
     * Ce qu'elle consulte sans pouvoir le modifier : l'organigramme, la rémunération, le statut
     * relèvent de l'employeur. Le numéro d'assurance sociale est <strong>masqué</strong> — le
     * titulaire n'a pas besoin de le relire en clair, et l'afficher entier multiplierait les fuites.
     */
    public static Map<String, Object> lectureSeule(Employe e, String departement, String gestionnaire) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("function", e.role());
        m.put("department", departement);
        m.put("managerName", gestionnaire);
        m.put("hireDate", e.hireDate());
        m.put("status", e.status());
        m.put("weeklyCapacityHours", e.weeklyCapacityHours());
        m.put("workerType", e.workerType());
        m.put("payType", e.payType());
        m.put("annualSalary", e.annualSalary());
        m.put("hourlyRate", e.hourlyRate());
        m.put("sinMasked", masquerNas(e.sin()));
        m.put("isTeacher", e.isTeacher());
        return m;
    }

    public static String masquerNas(String sin) {
        String chiffres = ou(sin).replaceAll("\\D", "");
        return chiffres.length() >= 3 ? "***-***-" + chiffres.substring(chiffres.length() - 3) : null;
    }

    /** Les erreurs d'une modification : un champ hors liste, un nom vide, un courriel mal formé. */
    public static List<String> validerModification(Map<String, Object> patch) {
        List<String> erreurs = new ArrayList<>();
        for (String cle : patch.keySet()) {
            if (!MODIFIABLES.contains(cle)) {
                erreurs.add("field '" + cle + "' is not editable");
            }
        }
        if (patch.containsKey("firstName") && String.valueOf(patch.get("firstName")).trim().isEmpty()) {
            erreurs.add("firstName required");
        }
        if (patch.containsKey("lastName") && String.valueOf(patch.get("lastName")).trim().isEmpty()) {
            erreurs.add("lastName required");
        }
        Object email = patch.get("email");
        if (patch.containsKey("email") && email != null && !"".equals(email) && !COURRIEL.matcher(String.valueOf(email)).matches()) {
            erreurs.add("invalid email");
        }
        return erreurs;
    }

    private static String texte(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** La fiche après modification : seuls les champs présents changent, les noms sont ébarbés, une date vide devient nulle. */
    public static Employe appliquer(Employe e, Map<String, Object> patch) {
        String prenom = patch.containsKey("firstName") ? String.valueOf(patch.get("firstName")).trim() : e.firstName();
        String nom = patch.containsKey("lastName") ? String.valueOf(patch.get("lastName")).trim() : e.lastName();
        String email = patch.containsKey("email") ? texte(patch.get("email")) : e.email();
        String phone = patch.containsKey("phone") ? texte(patch.get("phone")) : e.phone();
        String address = patch.containsKey("address") ? texte(patch.get("address")) : e.address();
        String naissance = patch.containsKey("dateOfBirth") ? ("".equals(patch.get("dateOfBirth")) ? null : texte(patch.get("dateOfBirth"))) : e.dateOfBirth();
        return new Employe(e.id(), prenom, nom, email, phone, address, naissance, e.jobTitle(), e.role(), e.departmentId(), e.managerId(), e.hireDate(), e.status(),
                e.weeklyCapacityHours(), e.workerType(), e.payType(), e.annualSalary(), e.hourlyRate(), e.sin(), e.isTeacher());
    }

    /** Nombre de personnes relevant de cet employé (toute la branche, pas les seuls directs). */
    public static int subordonnes(String employeeId, List<Lien> liens) {
        if (employeeId == null) {
            return 0;
        }
        int n = 0;
        Deque<String> file = new ArrayDeque<>(List.of(employeeId));
        Set<String> vus = new HashSet<>();
        while (!file.isEmpty()) {
            String id = file.poll();
            for (Lien l : liens) {
                if (id.equals(l.managerId()) && vus.add(l.id())) {
                    n++;
                    file.add(l.id());
                }
            }
        }
        return n;
    }

    /**
     * La ligne hiérarchique : gestionnaire direct en tête, puis ses supérieurs ; un cycle s'arrête là
     * où il se referme. {@code gestionnaireDe} : employé → son gestionnaire (vide s'il n'en a pas).
     */
    public static List<String> chaineHierarchique(String employeeId, Map<String, String> gestionnaireDe) {
        List<String> chaine = new ArrayList<>();
        Set<String> vus = new HashSet<>(Set.of(employeeId));
        String courant = ou(gestionnaireDe.get(employeeId));
        while (!courant.isEmpty() && vus.add(courant)) {
            if (!gestionnaireDe.containsKey(courant)) {
                break;
            }
            chaine.add(courant);
            courant = ou(gestionnaireDe.get(courant));
        }
        return chaine;
    }

    /**
     * Les approbateurs. Si {@code employeeId} est fourni, la question est « à qui cette personne
     * soumet-elle ? » : sa ligne hiérarchique, dans l'ordre ; les administrateurs ne réapparaissent
     * que si elle n'a aucun gestionnaire. Sans {@code employeeId}, l'annuaire général (gestionnaires
     * et administrateurs), trié, gestionnaire direct en tête s'il y figure.
     */
    public static List<Map<String, Object>> approbateurs(List<Compte> perimetre, String employeeId, List<String> chaine) {
        List<Compte> tous = perimetre.stream().filter(u -> u.active() && !"employee".equals(u.role()) && !"superadmin".equals(u.role())).toList();
        String prefere = chaine.isEmpty() ? null : chaine.get(0);
        List<Compte> liste = tous;
        if (employeeId != null && !employeeId.isEmpty()) {
            // La relation hiérarchique est portée par la fiche employé, pas par le rôle du compte : un
            // encadrant resté « employee » compte aussi — d'où la recherche dans tout le périmètre.
            List<Compte> enChaine = chaine.stream()
                    .map(id -> perimetre.stream().filter(u -> u.active() && !"superadmin".equals(u.role()) && id.equals(u.employeeId())).findFirst())
                    .flatMap(Optional::stream).toList();
            liste = enChaine.isEmpty() ? tous.stream().filter(u -> "admin".equals(u.role())).toList() : enChaine;
        }
        boolean ordreDeLaChaine = employeeId != null && !employeeId.isEmpty() && !chaine.isEmpty() && !liste.isEmpty() && liste.stream().allMatch(u -> chaine.contains(ou(u.employeeId())));
        List<Compte> triee = ordreDeLaChaine ? liste : liste.stream().sorted(Comparator
                .comparingInt((Compte u) -> prefere != null && prefere.equals(u.employeeId()) ? 0 : 1)
                .thenComparing(Compte::nomDeConnexion, Collator.getInstance(Locale.ROOT))).toList();
        return triee.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.id());
            m.put("username", u.nomDeConnexion());
            m.put("role", u.role());
            m.put("employeeId", u.employeeId());
            return m;
        }).toList();
    }
}
