package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * <strong>Relances et cascades</strong> (ST-G04 ; critères 15, 16, 17, 19, 25, 32) — la part pure. Une
 * {@link Politique} est une suite d'étapes ; une {@link Cascade} est l'état d'une alerte dans sa politique.
 *
 * <p>L'idempotence tient ici : une étape ne s'exécute que si la cascade attend <strong>cette</strong> étape. Une
 * tâche reprise après un redémarrage, ou prise deux fois, trouve la cascade déjà avancée et ne fait rien.
 */
public final class Cascades {

    public static final int ETAPES_MAX = 10;
    public static final int DELAI_MAX_MINUTES = 1_440;
    private static final long MS_MINUTE = 60_000L;

    private Cascades() {
    }

    /** Quand une cascade s'arrête d'elle-même. */
    public enum CritereArret {
        /** Dès qu'une personne répond « prise en charge ». */
        PRISE_EN_CHARGE("prise_en_charge"),
        /** Dès que tous les destinataires ont lu. */
        TOUTES_LUES("toutes_lues");

        private final String code;

        CritereArret(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static CritereArret de(String code) {
            return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("« arret » : prise_en_charge ou toutes_lues"));
        }

        /** Le critère est-il atteint par l'alerte telle qu'elle est maintenant ? */
        public boolean atteint(Alerte a) {
            Alertes.Bilan b = a.bilan();
            return switch (this) {
                case PRISE_EN_CHARGE -> b.premierePriseEnCharge().isPresent();
                case TOUTES_LUES -> b.lues() == b.destinataires();
            };
        }
    }

    /** Qui une étape vise. */
    public enum Cible {
        /** Les destinataires de l'alerte qui n'ont pas encore répondu : une relance. */
        INITIAUX("initiaux", false),
        /** Tous les membres d'un groupe : une escalade. */
        GROUPE("groupe", true),
        /** Une personne nommée (un chef de garde, une coordonnatrice). */
        PERSONNE("personne", true),
        /** Le premier membre disponible d'un groupe, selon le calendrier interne (critère 25). */
        PROCHAIN_DISPONIBLE("prochain_disponible", true);

        private final String code;
        private final boolean exigeCibleId;

        Cible(String code, boolean exigeCibleId) {
            this.code = code;
            this.exigeCibleId = exigeCibleId;
        }

        public String code() {
            return code;
        }

        public boolean exigeCibleId() {
            return exigeCibleId;
        }

        public static Cible de(String code) {
            return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("« cible » : initiaux, groupe, personne ou prochain_disponible"));
        }
    }

    /** Une étape : qui viser, par quels canaux, combien de minutes après l'étape précédente (ou l'envoi). */
    public record Etape(Cible cible, String cibleId, List<Priorite.Canal> canaux, int delaiMinutes) {

        public Etape {
            canaux = List.copyOf(canaux);
        }

        Etape valide(int rang) {
            String nom = "étape " + (rang + 1);
            if (cible == null) {
                throw RefusAlerte.invalide(nom + " : « cible » est requise");
            }
            if (cible.exigeCibleId() && (cibleId == null || cibleId.isBlank())) {
                throw RefusAlerte.invalide(nom + " : « cibleId » est requis pour la cible « " + cible.code() + " »");
            }
            if (canaux.isEmpty()) {
                throw RefusAlerte.invalide(nom + " : au moins un canal");
            }
            if (delaiMinutes < 1 || delaiMinutes > DELAI_MAX_MINUTES) {
                throw RefusAlerte.invalide(nom + " : « delaiMinutes » entre 1 et " + DELAI_MAX_MINUTES);
            }
            return new Etape(cible, cible.exigeCibleId() ? cibleId.trim() : null, canaux.stream().distinct().toList(), delaiMinutes);
        }

        /** Les codes de canaux, dans le vocabulaire du port d'avis. */
        public List<String> codesCanaux() {
            return canaux.stream().map(Priorite.Canal::code).toList();
        }
    }

    /** Une politique : ses étapes, son critère d'arrêt. Modifiée, elle fait une <strong>version</strong> nouvelle. */
    public record Politique(String id, String nom, List<Etape> etapes, CritereArret arret, int version, boolean actif) {

        public Politique {
            etapes = List.copyOf(etapes);
        }

        public static Politique nouvelle(String id, String nom, List<Etape> etapes, CritereArret arret) {
            return new Politique(id, Alertes.texte("nom", nom, Alertes.TITRE_MAX), valides(etapes), Alertes.exiger(arret, "arret"), 1, true);
        }

        public Politique modifiee(String nouveauNom, List<Etape> nouvelles, CritereArret nouvelArret) {
            return new Politique(id, Alertes.texte("nom", nouveauNom, Alertes.TITRE_MAX), valides(nouvelles), Alertes.exiger(nouvelArret, "arret"), version + 1, actif);
        }

        public Politique avecActif(boolean a) {
            return new Politique(id, nom, etapes, arret, version, a);
        }

        private static List<Etape> valides(List<Etape> etapes) {
            if (etapes.isEmpty() || etapes.size() > ETAPES_MAX) {
                throw RefusAlerte.invalide("une politique compte de 1 à " + ETAPES_MAX + " étapes");
            }
            return java.util.stream.IntStream.range(0, etapes.size()).mapToObj(i -> etapes.get(i).valide(i)).toList();
        }
    }

    /** Où en est une cascade. */
    public enum Etat {
        /** Une étape attend son échéance. */
        ARMEE("armee"),
        /** Le critère d'arrêt est atteint : plus rien ne part. */
        ARRETEE("arretee"),
        /** Toutes les étapes sont parties sans que le critère soit atteint. */
        EPUISEE("epuisee");

        private final String code;

        Etat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Etat de(String code) {
            return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("« etat » : armee, arretee ou epuisee"));
        }
    }

    /**
     * L'état d'une alerte dans sa politique : la version retenue à l'armement (une politique modifiée après coup ne
     * change pas une cascade en cours), l'étape attendue, son échéance.
     */
    public record Cascade(String alerteId, Politique politique, int etape, String echeanceLe, Etat etat) {

        public static Cascade armer(String alerteId, Politique politique, String envoyeeLe) {
            if (!politique.actif()) {
                throw RefusAlerte.invalide("la politique « " + politique.nom() + " » est désactivée");
            }
            return new Cascade(alerteId, politique, 0, plus(envoyeeLe, politique.etapes().getFirst().delaiMinutes()), Etat.ARMEE);
        }

        /** Attend-elle cette étape-là ? Sinon, une tâche en double ou reprise ne fait rien. */
        public boolean attend(int etapeAttendue) {
            return etat == Etat.ARMEE && etape == etapeAttendue;
        }

        public Etape etapeCourante() {
            return politique.etapes().get(etape);
        }

        /** Après l'étape courante : la suivante à son échéance, ou la cascade épuisée. */
        public Cascade apresEtape(String instant) {
            int suivante = etape + 1;
            if (suivante >= politique.etapes().size()) {
                return new Cascade(alerteId, politique, suivante, null, Etat.EPUISEE);
            }
            return new Cascade(alerteId, politique, suivante, plus(instant, politique.etapes().get(suivante).delaiMinutes()), Etat.ARMEE);
        }

        public Cascade arretee() {
            return new Cascade(alerteId, politique, etape, null, Etat.ARRETEE);
        }

        /** La reprise manuelle : l'étape choisie part tout de suite, quel que soit l'état (critère 17). */
        public Cascade reprise(int etapeChoisie, String instant) {
            if (etapeChoisie < 0 || etapeChoisie >= politique.etapes().size()) {
                throw RefusAlerte.invalide("« etape » : de 1 à " + politique.etapes().size());
            }
            return new Cascade(alerteId, politique, etapeChoisie, instant, Etat.ARMEE);
        }

        public Optional<String> prochaineEcheance() {
            return Optional.ofNullable(echeanceLe);
        }
    }

    /** Ce que la trace d'une cascade retient (critère 19) : chaque geste, horodaté et attribué. */
    public enum Evenement {
        ARMEE("armee"), ETAPE("etape"), ARRETEE("arretee"), EPUISEE("epuisee"), REPRISE("reprise");

        private final String code;

        Evenement(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Evenement de(String code) {
            return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("événement de cascade inconnu : " + code));
        }
    }

    /** Une ligne de la trace : l'étape (base 0), les personnes visées, qui ou quel mécanisme. */
    public record Trace(String alerteId, Evenement evenement, int etape, List<String> personnes, String instant, String par) {
        public Trace {
            personnes = List.copyOf(personnes);
        }
    }

    static String plus(String instant, int minutes) {
        return Instant.ofEpochMilli(Instant.parse(instant).toEpochMilli() + minutes * MS_MINUTE).toString();
    }
}
