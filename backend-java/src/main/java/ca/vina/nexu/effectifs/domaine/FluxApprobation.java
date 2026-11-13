package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <strong>Les flux d'approbation des heures</strong> (E-GP-6, feuilles de temps et pointages) — le pur de
 * {@code backend/src/effectifs/approbations/domaine.ts}. Les effectifs se <strong>branchent</strong> sur le moteur
 * d'approbation générique au lieu d'en refaire un ; ce fichier dit seulement ce qui leur est propre : les champs de routage
 * d'une feuille ou d'un pointage, la désignation par niveau de périmètre (encodée {@code perimetre:<NIVEAU>} dans le rôle
 * d'approbateur du moteur), et le flux par défaut quand l'organisme n'a rien configuré.
 */
public final class FluxApprobation {

    public static final String PREFIXE_PERIMETRE = "perimetre:";
    public static final int PALIER_MAX = 10;
    /** Le champ d'un palier inconditionnel : « toujours = 1 », le moteur générique exigeant une condition par règle. */
    public static final String TOUJOURS = "toujours";
    private static final Pattern ROLE = Pattern.compile("^[a-z][a-z0-9_-]{1,39}$");

    private FluxApprobation() {
    }

    /** Le type d'un champ de routage. */
    public enum TypeChamp {
        NOMBRE("nombre"), TEXTE("texte");

        private final String code;

        TypeChamp(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record ChampFlux(String code, String libelle, TypeChamp type) { }

    /** Ce qu'un flux approuve, et le type de document sous lequel le moteur générique range ses règles et ses demandes. */
    public enum ObjetFlux {
        FEUILLE("effectifs.feuille"), POINTAGE("effectifs.pointage");

        private final String typeDocument;

        ObjetFlux(String typeDocument) {
            this.typeDocument = typeDocument;
        }

        public String typeDocument() {
            return typeDocument;
        }

        /** Les champs sur lesquels un palier se route, dans l'ordre de l'écran. */
        public List<ChampFlux> champs() {
            return switch (this) {
                case FEUILLE -> List.of(c(TOUJOURS, "Toujours (1)"), c("heuresDeclarees", "Heures payées déclarées"),
                        c("heuresSupplementaires", "Heures supplémentaires"), c("anomalies", "Nombre d'anomalies à la soumission"),
                        c("anomaliesNonCorroborees", "Jours non corroborés par une source tierce"),
                        c("segmentsExternes", "Segments tirés d'un horodatage externe"), c("segmentsCorriges", "Segments corrigés"),
                        c("ecartPlanifieMaxMinutes", "Plus grand écart au planifié (minutes)"), new ChampFlux("uniteCode", "Unité", TypeChamp.TEXTE));
                case POINTAGE -> List.of(c(TOUJOURS, "Toujours (1)"), c("ecartDebutMinutes", "Écart à l'heure prévue (minutes, valeur absolue)"),
                        c("saisieAPosteriori", "Déclaré après coup (1) ou pointé (0)"), c("horsMilieu", "Écart de lieu ou de preuve (1) ou non (0)"),
                        c("corrobore", "Confirmé par une source tierce (1) ou non (0)"), new ChampFlux("uniteCode", "Unité", TypeChamp.TEXTE));
            };
        }

        private static ChampFlux c(String code, String libelle) {
            return new ChampFlux(code, libelle, TypeChamp.NOMBRE);
        }
    }

    public enum TypeApprobateur { PERIMETRE, ROLE, PERSONNE }

    /** Les opérateurs du moteur générique, tels quels : le flux se juge là-bas. */
    public enum OperateurFlux {
        GTE("gte"), LTE("lte"), EQ("eq"), NEQ("neq");

        private final String code;

        OperateurFlux(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Optional<OperateurFlux> de(Object code) {
            return Arrays.stream(values()).filter(o -> o.code.equals(code)).findFirst();
        }

        boolean compareDesTextes() {
            return this == EQ || this == NEQ;
        }
    }

    public record Approbateur(TypeApprobateur type, String valeur) { }

    /** Une règle de flux, dans les mots des effectifs ; {@code valeur} : un nombre ou un texte selon le champ. */
    public record RegleFlux(String id, ObjetFlux objet, int palier, String champ, OperateurFlux operateur, Object valeur, Approbateur approbateur) {
        public RegleFlux avecId(String nouvelId) {
            return new RegleFlux(nouvelId, objet, palier, champ, operateur, valeur, approbateur);
        }
    }

    /** Une règle telle que le moteur générique la range (sa valeur est un texte). */
    public record RegleApprobation(String id, String field, OperateurFlux operator, String value, int level, String approverRole, String approverId) { }

    // ── Validation d'une règle saisie ────────────────────────────────────────

    private static <E extends Enum<E>> E enumDe(E[] valeurs, Function<E, String> code, Object v, String nom) {
        return Arrays.stream(valeurs).filter(e -> code.apply(e).equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« " + nom + " » : " + Arrays.stream(valeurs).map(code).collect(Collectors.joining(", "))));
    }

    public static ObjetFlux objetDe(Object v) {
        return enumDe(ObjetFlux.values(), ObjetFlux::name, v, "objet");
    }

    /** Valide une règle saisie à l'écran : objet, palier borné, champ connu de l'objet, opérateur compatible, valeur, approbateur. */
    public static RegleFlux regleDeFlux(Map<String, Object> brut) {
        ObjetFlux objet = objetDe(brut.get("objet"));
        double palier = TempsEffectifs.nombre(brut.get("palier"));
        if (palier != Math.rint(palier) || palier < 1 || palier > PALIER_MAX) {
            throw new EffectifsInvalideException("« palier » : entier de 1 à " + PALIER_MAX);
        }
        ChampFlux champ = objet.champs().stream().filter(c -> c.code().equals(brut.get("champ"))).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« champ » : " + objet.champs().stream().map(ChampFlux::code).collect(Collectors.joining(", "))));
        OperateurFlux operateur = enumDe(OperateurFlux.values(), OperateurFlux::code, brut.get("operateur"), "operateur");
        if (champ.type() == TypeChamp.TEXTE && !operateur.compareDesTextes()) {
            throw new EffectifsInvalideException("« " + champ.code() + " » se compare par eq ou neq");
        }
        return new RegleFlux(null, objet, (int) palier, champ.code(), operateur, valeurDe(champ, brut.get("valeur")), approbateurDe(brut.get("approbateur")));
    }

    private static Object valeurDe(ChampFlux champ, Object brut) {
        return switch (champ.type()) {
            case NOMBRE -> {
                double n = brut == null || "".equals(brut) ? Double.NaN : TempsEffectifs.nombre(brut);
                if (!Double.isFinite(n)) {
                    throw new EffectifsInvalideException("« valeur » : un nombre pour « " + champ.code() + " »");
                }
                yield nombre(n);
            }
            case TEXTE -> {
                String t = brut == null ? "" : texte(brut).strip();
                if (t.isEmpty()) {
                    throw new EffectifsInvalideException("« valeur » : un texte pour « " + champ.code() + " »");
                }
                yield t;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Approbateur approbateurDe(Object brut) {
        Map<String, Object> a = brut instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        TypeApprobateur type = enumDe(TypeApprobateur.values(), TypeApprobateur::name, a.get("type"), "approbateur.type");
        String valeur = a.get("valeur") instanceof String s ? s.strip() : "";
        return switch (type) {
            case PERIMETRE -> new Approbateur(type, enumDe(Perimetres.Niveau.values(), Perimetres.Niveau::name, valeur, "approbateur.valeur").name());
            case ROLE -> {
                if (!ROLE.matcher(valeur).matches()) {
                    throw new EffectifsInvalideException("« approbateur.valeur » : le code d'un rôle (ex. manager, director)");
                }
                yield new Approbateur(type, valeur);
            }
            case PERSONNE -> {
                if (valeur.isEmpty()) {
                    throw new EffectifsInvalideException("« approbateur.valeur » : l'identifiant de la personne désignée");
                }
                yield new Approbateur(type, valeur);
            }
        };
    }

    // ── Traduction vers et depuis le moteur générique ────────────────────────

    /** Un nombre comme le JSON du Node : 4, pas 4.0. */
    public static Number nombre(double v) {
        return v == Math.rint(v) && !Double.isInfinite(v) ? (Number) (long) v : (Number) v;
    }

    /** Une valeur comme le {@code String(v)} du Node. */
    static String texte(Object v) {
        return v instanceof Double d ? String.valueOf(nombre(d)) : String.valueOf(v);
    }

    /** La règle telle que le moteur générique la range : la désignation par périmètre s'encode dans le rôle d'approbateur. */
    public static RegleApprobation versRegleApprobation(RegleFlux r) {
        Approbateur a = r.approbateur();
        String role = switch (a.type()) {
            case PERIMETRE -> PREFIXE_PERIMETRE + a.valeur();
            case ROLE -> a.valeur();
            case PERSONNE -> null;
        };
        return new RegleApprobation(r.id(), r.champ(), r.operateur(), texte(r.valeur()), r.palier(), role, a.type() == TypeApprobateur.PERSONNE ? a.valeur() : null);
    }

    /** Ce qu'une étape du moteur générique désigne, dans les mots des effectifs. */
    public static Approbateur approbateurDe(String approverRole, String approverId) {
        if (approverId != null && !approverId.isEmpty()) {
            return new Approbateur(TypeApprobateur.PERSONNE, approverId);
        }
        String role = approverRole == null ? "" : approverRole;
        return role.startsWith(PREFIXE_PERIMETRE) ? new Approbateur(TypeApprobateur.PERIMETRE, role.substring(PREFIXE_PERIMETRE.length()))
                : new Approbateur(TypeApprobateur.ROLE, role);
    }

    public static RegleFlux depuisRegleApprobation(ObjetFlux objet, RegleApprobation r) {
        boolean texte = objet.champs().stream().anyMatch(c -> c.code().equals(r.field()) && c.type() == TypeChamp.TEXTE);
        Object valeur = texte ? r.value() : nombreDuTexte(r.value());
        return new RegleFlux(r.id(), objet, r.level(), r.field(), r.operator(), valeur, approbateurDe(r.approverRole(), r.approverId()));
    }

    private static Number nombreDuTexte(String v) {
        double n = TempsEffectifs.nombre(v);
        return Double.isNaN(n) ? null : nombre(n);
    }

    /** Le flux d'avant la configuration : N paliers au niveau GESTION de l'unité, toujours. */
    public static List<RegleApprobation> fluxParDefaut(int paliers) {
        return IntStream.rangeClosed(1, paliers).mapToObj(i -> new RegleApprobation("defaut-" + i, TOUJOURS, OperateurFlux.EQ, "1", i,
                PREFIXE_PERIMETRE + Perimetres.Niveau.GESTION.name(), null)).toList();
    }
}
