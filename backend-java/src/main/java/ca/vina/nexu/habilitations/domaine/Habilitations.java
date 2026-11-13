package ca.vina.nexu.habilitations.domaine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <strong>Périodes d'effectivité et consultation centralisée des droits</strong> — AI-2026-03,
 * exigences 84, 85, 86, 90, 91.
 *
 * <p>Jumeau de {@code backend/src/habilitations/domaine.ts} : mêmes refus, mêmes messages.
 *
 * <p>Le RBAC du socle satisfait déjà l'exigence 88. Trois choses manquaient, et elles se
 * ressemblent moins qu'il n'y paraît. <strong>La période d'effectivité (84)</strong> n'est pas un
 * raffinement : un remplacement de congé donne des droits qui doivent cesser d'eux-mêmes, sans
 * quoi personne ne saura depuis combien de temps la personne avait accès. <strong>La consultation
 * par objet (90)</strong> répond à une question que l'écran de rôles ne sait pas poser — un
 * auditeur ne déduit pas, il demande la liste. <strong>La vue centralisée (91)</strong> distingue
 * « les droits sont gérables » de « les droits sont gérés ».
 */
public final class Habilitations {

    private Habilitations() {
    }

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    /** Au-delà, un gestionnaire ne prépare plus rien : il découvre l'expiration le jour même. */
    private static final int HORIZON_ECHEANCE_JOURS = 30;

    public static class HabilitationInvalideException extends RuntimeException {
        public HabilitationInvalideException(String message) {
            super(message);
        }
    }

    /**
     * Les trois niveaux que l'avis nomme (exigence 85).
     *
     * <p>{@code aucun} est <strong>déclaré</strong> et non déduit de l'absence : un refus explicite
     * se distingue d'un oubli de configuration, et cette distinction est exactement ce qu'un
     * auditeur vient vérifier.
     */
    public enum Permission {
        AUCUN("aucun", "aucun accès", 0),
        LECTURE("lecture", "lecture seule", 1),
        LECTURE_ECRITURE("lecture_ecriture", "lecture et écriture", 2);

        private final String code;
        private final String libelle;
        private final int force;

        Permission(String code, String libelle, int force) {
            this.code = code;
            this.libelle = libelle;
            this.force = force;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }

        /** L'ordre de force : c'est lui qui départage deux octrois qui se rencontrent. */
        public int force() {
            return force;
        }

        public static Permission de(String code) {
            return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst()
                    .orElseThrow(() -> new HabilitationInvalideException("permission inconnue : « " + code
                            + " » (attendu : " + String.join(", ", codes()) + ")"));
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Permission::code).toList();
        }
    }

    /** Les types d'objets que l'exigence 90 énumère. */
    public enum TypeObjet {
        FONCTIONNALITE("fonctionnalite"),
        FORMULAIRE("formulaire"),
        RAPPORT("rapport"),
        COMPOSANTE("composante");

        private final String code;

        TypeObjet(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static TypeObjet de(String code) {
            return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst()
                    .orElseThrow(() -> new HabilitationInvalideException("type d'objet inconnu : « " + code
                            + " » (attendu : " + String.join(", ", codes()) + ")"));
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(TypeObjet::code).toList();
        }
    }

    /** Le bénéficiaire d'un octroi : un profil (un groupe) ou un utilisateur nommé. */
    public enum Sujet {
        PROFIL("profil"),
        UTILISATEUR("utilisateur");

        private final String code;

        Sujet(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Sujet de(String code) {
            return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                    .orElseThrow(() -> new HabilitationInvalideException("sujet inconnu : « " + code
                            + " » (attendu : profil, utilisateur)"));
        }
    }

    /** Une date au format du domaine, ou un refus nommé. Publique : l'application la réutilise. */
    public static String date(String v, String sujet) {
        if (v == null || !DATE.matcher(v).matches()) {
            throw new HabilitationInvalideException(sujet + " s'écrit AAAA-MM-JJ");
        }
        return v;
    }

    /** Un champ obligatoire, ou un refus nommé. Publique : l'application la réutilise. */
    public static String texte(Object v, String sujet) {
        String t = v == null ? "" : v.toString().trim();
        if (t.isEmpty()) {
            throw new HabilitationInvalideException("« " + sujet + " » est obligatoire");
        }
        return t;
    }

    /** Une composante appartient à un formulaire : c'est ce qui rend l'exigence 90 traçable. */
    public record Objet(TypeObjet type, String code, String libelle, String parentCode) { }

    public record Octroi(Sujet sujetType, String sujetId, TypeObjet objetType, String objetCode,
                         Permission permission, String effectifDu, String effectifAu, String motif) { }

    public static void verifierOctroi(Octroi o) {
        texte(o.sujetId(), "sujetId");
        texte(o.objetCode(), "objetCode");
        date(o.effectifDu(), "la prise d'effet de « " + o.sujetId() + " »");
        if (o.effectifAu() != null && !o.effectifAu().isBlank()) {
            date(o.effectifAu(), "la fin d'effet de « " + o.sujetId() + " »");
            if (o.effectifAu().compareTo(o.effectifDu()) < 0) {
                throw new HabilitationInvalideException("l'octroi à « " + o.sujetId() + " » se termine ("
                        + o.effectifAu() + ") avant de commencer (" + o.effectifDu() + ")");
            }
        }
    }

    /** Un octroi est-il effectif à cette date ? C'est la seule question que pose une garde d'accès. */
    public static boolean effectifLe(Octroi o, String le) {
        String jour = date(le, "la date d'observation");
        boolean ouvert = o.effectifAu() == null || o.effectifAu().isBlank()
                || o.effectifAu().compareTo(jour) >= 0;
        return o.effectifDu().compareTo(jour) <= 0 && ouvert;
    }

    /** D'où vient la permission retenue : c'est ce qu'un auditeur demande après le « quoi ». */
    public enum Origine {
        UTILISATEUR("utilisateur"), PROFIL("profil"), DEFAUT("defaut");

        private final String code;

        Origine(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Resolution(String utilisateurId, TypeObjet objetType, String objetCode,
                             Permission permission, Origine origine, String origineId,
                             List<String> motifs) { }

    /**
     * <strong>Résoudre la permission d'un utilisateur sur un objet, à une date donnée.</strong>
     *
     * <p>Deux règles, et l'ordre compte.
     *
     * <p>1. <strong>Un octroi nominatif l'emporte sur le profil</strong>, quel qu'il soit — y
     * compris pour <em>retirer</em> un droit. Sans cela, on ne pourrait pas exclure une personne
     * d'un profil sans la sortir du profil, ce qui lui retirerait tout le reste.
     *
     * <p>2. <strong>Entre profils, le plus permissif l'emporte.</strong> Ajouter un profil doit
     * <em>ajouter</em> un accès ; prendre le plus restrictif ferait qu'ajouter un profil retirerait
     * des droits — un comportement que personne n'anticipe.
     *
     * <p><strong>Le défaut est {@code aucun}</strong> : ce qui n'est pas octroyé est refusé
     * (exigence 86).
     */
    public static Resolution resoudre(String utilisateurId, List<String> profils, List<Octroi> octrois,
                                      TypeObjet type, String objetCode, String le) {
        String id = texte(utilisateurId, "utilisateurId");
        String jour = date(le, "la date d'observation");
        String code = texte(objetCode, "objetCode");

        List<Octroi> pertinents = octrois.stream()
                .filter(o -> o.objetType() == type && o.objetCode().equals(code) && effectifLe(o, jour)).toList();

        Octroi nominatif = pertinents.stream()
                .filter(o -> o.sujetType() == Sujet.UTILISATEUR && o.sujetId().equals(id)).findFirst().orElse(null);
        if (nominatif != null) {
            String terme = nominatif.effectifAu() == null || nominatif.effectifAu().isBlank()
                    ? "terme indéterminé" : nominatif.effectifAu();
            String motif = "Octroi nominatif : " + nominatif.permission().libelle() + " jusqu'au " + terme
                    + (nominatif.motif() == null || nominatif.motif().isBlank() ? "" : " — " + nominatif.motif());
            return new Resolution(id, type, code, nominatif.permission(), Origine.UTILISATEUR, id, List.of(motif));
        }

        List<Octroi> parProfil = pertinents.stream()
                .filter(o -> o.sujetType() == Sujet.PROFIL && profils.contains(o.sujetId())).toList();
        if (parProfil.isEmpty()) {
            return new Resolution(id, type, code, Permission.AUCUN, Origine.DEFAUT, null,
                    List.of("Aucun octroi effectif à cette date : le refus est le défaut, et non un oubli"));
        }

        Octroi retenu = parProfil.stream().reduce((a, b) -> b.permission().force() > a.permission().force() ? b : a)
                .orElseThrow();
        List<String> motifs = new ArrayList<>();
        motifs.add("Par le profil « " + retenu.sujetId() + " » : " + retenu.permission().libelle());
        if (parProfil.size() > 1) {
            motifs.add(parProfil.size() + " profils accordent un droit sur cet objet ; le plus permissif l'emporte, "
                    + "parce qu'ajouter un profil doit ajouter un accès, jamais en retirer");
        }
        return new Resolution(id, type, code, retenu.permission(), Origine.PROFIL, retenu.sujetId(), motifs);
    }

    public record SujetDroit(Sujet sujetType, String sujetId, Permission permission, String effectifAu) { }

    public record DroitParObjet(Objet objet, List<SujetDroit> sujets) { }

    /**
     * <strong>Qui peut accéder à cet objet ?</strong> (exigence 90)
     *
     * <p>Un tableau rôle × capacité y répond en le faisant déduire ; un auditeur ne déduit pas — il
     * demande la liste, et il la veut <strong>à une date précise</strong>, parce que la question
     * porte en général sur le passé.
     */
    public static DroitParObjet droitsSurObjet(List<Objet> objets, List<Octroi> octrois, String objetCode, String le) {
        String code = texte(objetCode, "objetCode");
        Objet objet = objets.stream().filter(o -> o.code().equals(code)).findFirst()
                .orElseThrow(() -> new HabilitationInvalideException("objet inconnu : « " + code + " » (déclarés : "
                        + String.join(", ", objets.stream().map(Objet::code).toList()) + ")"));
        String jour = date(le, "la date d'observation");

        return new DroitParObjet(objet, octrois.stream()
                .filter(o -> o.objetCode().equals(code) && effectifLe(o, jour) && o.permission() != Permission.AUCUN)
                .map(o -> new SujetDroit(o.sujetType(), o.sujetId(), o.permission(), o.effectifAu()))
                .sorted(Comparator.comparing(SujetDroit::sujetId)).toList());
    }

    public record DroitFiche(Objet objet, Permission permission, Origine origine, String origineId) { }

    /** Les octrois qui cesseront d'eux-mêmes : c'est ce qu'un gestionnaire doit anticiper. */
    public record Echeance(String objetCode, String effectifAu, long joursRestants) { }

    public record FicheUtilisateur(String utilisateurId, String le, List<String> profils,
                                   List<DroitFiche> droits, List<Echeance> echeances, List<String> motifs) { }

    /**
     * <strong>Tout ce à quoi un utilisateur a droit, dans une seule vue</strong> (exigence 91).
     *
     * <p>Les <strong>échéances</strong> ne sont pas un ornement. Un droit qui expire dans trois
     * jours est une interruption de service programmée : la montrer à l'avance permet de la
     * préparer, la découvrir le matin même produit un appel au soutien.
     */
    public static FicheUtilisateur ficheUtilisateur(List<Objet> objets, List<Octroi> octrois,
                                                    String utilisateurId, List<String> profils, String le) {
        String id = texte(utilisateurId, "utilisateurId");
        String jour = date(le, "la date d'observation");

        List<DroitFiche> droits = objets.stream()
                .map(objet -> {
                    Resolution r = resoudre(id, profils, octrois, objet.type(), objet.code(), jour);
                    return new DroitFiche(objet, r.permission(), r.origine(), r.origineId());
                })
                .filter(d -> d.permission() != Permission.AUCUN).toList();

        String limite = LocalDate.parse(jour).plusDays(HORIZON_ECHEANCE_JOURS).toString();
        List<Echeance> echeances = octrois.stream()
                .filter(o -> o.effectifAu() != null && !o.effectifAu().isBlank() && effectifLe(o, jour)
                        && o.permission() != Permission.AUCUN && o.effectifAu().compareTo(limite) <= 0
                        && (o.sujetType() == Sujet.UTILISATEUR ? o.sujetId().equals(id) : profils.contains(o.sujetId())))
                .map(o -> new Echeance(o.objetCode(), o.effectifAu(),
                        ChronoUnit.DAYS.between(LocalDate.parse(jour), LocalDate.parse(o.effectifAu()))))
                .sorted(Comparator.comparing(Echeance::effectifAu)).toList();

        List<String> motifs = new ArrayList<>();
        motifs.add(droits.isEmpty()
                ? "Aucun droit effectif au " + jour + " : l'utilisateur ne voit rien, et c'est le défaut"
                : droits.size() + " objet(s) accessible(s) au " + jour + ", par " + profils.size()
                        + " profil(s) et les octrois nominatifs");
        if (!echeances.isEmpty()) {
            motifs.add(echeances.size() + " droit(s) expirent dans les " + HORIZON_ECHEANCE_JOURS
                    + " jours : les préparer évite une interruption découverte le matin même");
        }
        return new FicheUtilisateur(id, jour, profils, droits, echeances, motifs);
    }
}
