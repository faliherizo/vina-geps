package ca.vina.nexu.alertes.domaine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Les communications cliniques</strong> (ADR 0074) — la part pure : réponses prédéfinies, modèles,
 * alertes et remises, et les règles R1 à R10 de l'analyse métier
 * ({@code docs/FX-AO-20260810_Dossier/07_validation_vina_geps/07_analyse_metier_socle_alertes.md}).
 *
 * <p>Rien ici ne lit l'horloge ni ne tire d'identifiant : l'instant et les identifiants arrivent en
 * paramètre, de sorte que chaque règle s'éprouve sans base ni serveur.
 */
public final class Alertes {

    private Alertes() {
    }

    public static final int TITRE_MAX = 120;
    public static final int CORPS_MAX = 2_000;
    public static final int LIBELLE_MAX = 60;

    /** Un texte requis et borné, élagué ; le refus nomme le champ. */
    static String texte(String champ, String valeur, int max) {
        String v = valeur == null ? "" : valeur.trim();
        if (v.isEmpty()) {
            throw RefusAlerte.invalide("« " + champ + " » est requis");
        }
        if (v.length() > max) {
            throw RefusAlerte.invalide("« " + champ + " » : " + max + " caractères au plus");
        }
        return v;
    }

    /** Un geste proposé au destinataire. Désactivée, elle n'est plus proposée ; elle ne s'efface jamais (R9). */
    public record ReponsePredefinie(String id, String libelle, Semantique semantique, boolean active) {

        public static ReponsePredefinie nouvelle(String id, String libelle, Semantique semantique) {
            return new ReponsePredefinie(id, texte("libelle", libelle, LIBELLE_MAX), exiger(semantique, "semantique"), true);
        }

        public ReponsePredefinie avecActive(boolean actif) {
            return new ReponsePredefinie(id, libelle, semantique, actif);
        }
    }

    /**
     * Ce qu'un modèle dit : son texte, sa priorité, les réponses qu'il propose, et son <strong>périmètre</strong> — les
     * groupes dont les expéditeurs le voient ; vide, tout l'organisme le voit (ST-G02).
     */
    public record ContenuModele(String titre, String corps, Priorite priorite, List<String> reponseIds, List<String> groupeIds) {
        public ContenuModele {
            reponseIds = List.copyOf(reponseIds);
            groupeIds = List.copyOf(groupeIds);
        }

        ContenuModele valide() {
            return new ContenuModele(texte("titre", titre, TITRE_MAX), texte("corps", corps, CORPS_MAX), exiger(priorite, "priorite"),
                    reponseIds.stream().distinct().toList(), groupeIds.stream().distinct().toList());
        }
    }

    /**
     * Ce que l'administration prépare pour qu'une alerte se compose en un geste. Chaque modification en fait une
     * <strong>version</strong> nouvelle : une alerte garde la version qu'elle a envoyée (ST-G02, critère 32).
     */
    public record Modele(String id, ContenuModele contenu, int version, boolean actif) {

        public static Modele nouveau(String id, ContenuModele contenu) {
            return new Modele(id, contenu.valide(), 1, true);
        }

        /** La version suivante ; l'état actif ne change pas. */
        public Modele modifie(ContenuModele nouveau) {
            return new Modele(id, nouveau.valide(), version + 1, actif);
        }

        public Modele avecActif(boolean a) {
            return new Modele(id, contenu, version, a);
        }

        /** Un modèle sans périmètre vaut pour tous ; sinon, pour les membres d'un de ses groupes. */
        public boolean visiblePour(Collection<String> groupesDeLExpediteur) {
            return contenu.groupeIds().isEmpty() || contenu.groupeIds().stream().anyMatch(groupesDeLExpediteur::contains);
        }

        public String titre() {
            return contenu.titre();
        }

        public Priorite priorite() {
            return contenu.priorite();
        }

        public List<String> reponseIds() {
            return contenu.reponseIds();
        }

        public ReferenceModele reference() {
            return new ReferenceModele(id, version);
        }
    }

    /** Le modèle et la version dont une alerte est partie. */
    public record ReferenceModele(String id, int version) {
    }

    /** Les réglages de l'organisme : {@code copieCourriel} envoie aussi une copie par courriel à chaque destinataire (critère 8). */
    public record Reglages(boolean copieCourriel) {
        public static final Reglages DEFAUT = new Reglages(false);
    }

    /**
     * La priorité critique force tous les canaux de chacun : elle se réserve à qui en a reçu le droit
     * ({@code alertes.critique}), pour qu'une alerte d'information ne réveille pas tout un service (ST-G01).
     */
    public static void exigerDroit(Priorite priorite, boolean critiqueAutorisee) {
        if (priorite == Priorite.CRITIQUE && !critiqueAutorisee) {
            throw RefusAlerte.interdit("la priorité critique est réservée aux personnes qui en ont le droit (alertes.critique)");
        }
    }

    /** Une personne visée, nommée, avec le compte par lequel on l'avise. */
    public record Destinataire(String personId, String nom, String compteAvis) {
    }

    /** Ce qu'une alerte est pour un destinataire (R4, R5). */
    public record Remise(Destinataire destinataire, String envoyeeLe, String lueLe, String reponseId, String reponduLe) {

        public boolean lue() {
            return lueLe != null;
        }

        public boolean repondue() {
            return reponseId != null;
        }
    }

    /** Le bilan d'une alerte, tel que l'expéditeur le lit d'un coup d'œil. */
    public record Bilan(int destinataires, int lues, int repondues, Optional<Remise> premierePriseEnCharge) {
    }

    /**
     * Une alerte : son contenu, les réponses qu'elle propose (copiées à l'envoi, R3) et ses remises
     * (le snapshot des destinataires, R2).
     */
    public record Alerte(String id, String tenantId, String titre, String corps, Priorite priorite, String expediteurId,
                         String envoyeeLe, List<ReponsePredefinie> reponses, List<Remise> remises, ReferenceModele modele) {

        public Alerte {
            reponses = List.copyOf(reponses);
            remises = List.copyOf(remises);
        }

        /** La remise de cette personne, si elle est destinataire ; sinon l'alerte lui est introuvable (R6). */
        public Remise remiseDe(String personId) {
            return remises.stream().filter(r -> r.destinataire().personId().equals(personId)).findFirst()
                    .orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
        }

        /** La première lecture compte ; les suivantes ne changent rien (R4). */
        public Alerte lue(String personId, String instant) {
            Remise r = remiseDe(personId);
            return r.lue() ? this : remplacer(new Remise(r.destinataire(), r.envoyeeLe(), instant, r.reponseId(), r.reponduLe()));
        }

        /** Une seule réponse, parmi celles que cette alerte propose ; répondre vaut lecture (R5). */
        public Alerte repondue(String personId, String reponseId, String instant) {
            Remise r = remiseDe(personId);
            if (r.repondue()) {
                throw RefusAlerte.conflit("vous avez déjà répondu à cette alerte");
            }
            reponse(reponseId).orElseThrow(() -> RefusAlerte.invalide("« reponseId » : cette réponse n'est pas proposée par l'alerte"));
            return remplacer(new Remise(r.destinataire(), r.envoyeeLe(), r.lue() ? r.lueLe() : instant, reponseId, instant));
        }

        public Optional<ReponsePredefinie> reponse(String reponseId) {
            return reponses.stream().filter(x -> x.id().equals(reponseId)).findFirst();
        }

        public Bilan bilan() {
            Optional<Remise> prise = remises.stream()
                    .filter(r -> r.repondue() && reponse(r.reponseId()).map(x -> x.semantique() == Semantique.PRISE_EN_CHARGE).orElse(false))
                    .min((a, b) -> a.reponduLe().compareTo(b.reponduLe()));
            return new Bilan(remises.size(), (int) remises.stream().filter(Remise::lue).count(),
                    (int) remises.stream().filter(Remise::repondue).count(), prise);
        }

        private Alerte remplacer(Remise nouvelle) {
            List<Remise> l = new ArrayList<>(remises.size());
            for (Remise r : remises) {
                l.add(r.destinataire().personId().equals(nouvelle.destinataire().personId()) ? nouvelle : r);
            }
            return new Alerte(id, tenantId, titre, corps, priorite, expediteurId, envoyeeLe, reponses, l, modele);
        }
    }

    /** Ce qu'il faut pour composer une alerte, avant résolution des destinataires ; {@code modele} nul : composée librement. */
    public record Composition(String titre, String corps, Priorite priorite, List<String> reponseIds, ReferenceModele modele) {
        public Composition {
            reponseIds = List.copyOf(reponseIds);
        }

        /** Une composition libre, sans modèle. */
        public Composition(String titre, String corps, Priorite priorite, List<String> reponseIds) {
            this(titre, corps, priorite, reponseIds, null);
        }
    }

    /**
     * L'alerte prête à partir (R1, R2, R3) : textes validés, destinataires dédoublonnés sans l'expéditeur,
     * réponses actives copiées.
     */
    public static Alerte composer(String id, String tenantId, String expediteurId, String instant, Composition c,
                                  List<Destinataire> resolus, List<ReponsePredefinie> catalogue) {
        String titre = texte("titre", c.titre(), TITRE_MAX);
        String corps = texte("corps", c.corps(), CORPS_MAX);
        Priorite priorite = exiger(c.priorite(), "priorite");
        List<Remise> remises = remises(resolus, expediteurId, instant);
        if (remises.isEmpty()) {
            throw RefusAlerte.invalide("aucun destinataire : choisissez au moins une personne ou un groupe qui compte des membres");
        }
        return new Alerte(id, tenantId, titre, corps, priorite, expediteurId, instant, reponsesProposees(c.reponseIds(), catalogue), remises, c.modele());
    }

    private static List<Remise> remises(List<Destinataire> resolus, String expediteurId, String instant) {
        Map<String, Destinataire> uniques = new LinkedHashMap<>();
        for (Destinataire d : resolus) {
            if (!d.personId().equals(expediteurId)) {
                uniques.putIfAbsent(d.personId(), d);
            }
        }
        return uniques.values().stream().map(d -> new Remise(d, instant, null, null, null)).toList();
    }

    private static List<ReponsePredefinie> reponsesProposees(List<String> ids, List<ReponsePredefinie> catalogue) {
        List<ReponsePredefinie> l = new ArrayList<>();
        for (String id : ids.stream().distinct().toList()) {
            l.add(catalogue.stream().filter(r -> r.id().equals(id) && r.active()).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("« reponseIds » : la réponse « " + id + " » n'existe pas ou n'est plus active")));
        }
        return l;
    }

    static <T> T exiger(T valeur, String champ) {
        if (valeur == null) {
            throw RefusAlerte.invalide("« " + champ + " » est requis");
        }
        return valeur;
    }
}
