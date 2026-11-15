package ca.vina.nexu.saas.domaine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <strong>Catalogue des modules</strong> d'Urgences-santé : ce qu'un organisme peut activer, ce que cela suppose, et la résolution pure des dépendances
 * (activer un sous-module ajoute son prérequis, retirer un prérequis emporte ses dépendants, et
 * l'appelant est informé de ce qui a changé — jamais de modification silencieuse). L'ordre est
 * celui de l'affichage, famille par famille.
 */
public final class CatalogueModules {

    private CatalogueModules() {
    }

    public record Famille(String cle, String libelle) { }

    public record Definition(String code, String label, String famille, String requiert, String debloque) { }

    public record Resolution(List<String> modules, List<String> ajoutes, List<String> retires) { }

    public static final List<Famille> FAMILLES = List.of(
            new Famille("coeur", "Cœur du produit"),
            // Le nom du client et celui de l'avis sont ce qu'un superadmin tape dans le filtre.
            new Famille("effectifs", "Santé — Urgences-santé (AO 26-015-DO)"),
            new Famille("communications", "Santé — communications cliniques (AO FX-AO-20260810)"),
            new Famille("paie", "Paie québécoise (ADR 0076)"),
            new Famille("infrastructure", "Infrastructure et options"));

    private static Definition m(String code, String label, String famille, String debloque) {
        return new Definition(code, label, famille, null, debloque);
    }

    private static Definition m(String code, String label, String famille, String requiert, String debloque) {
        return new Definition(code, label, famille, requiert, debloque);
    }

    public static final List<Definition> MODULES = List.of(
            m("approvals", "Approbations", "coeur", "Menu Approbations"),
            m("notifications", "Notifications", "coeur", "Menu Notifications"),
            m("integrations", "Intégrations et webhooks sortants", "coeur", "Menu Administration › Intégrations : cartes d'état, webhooks sortants signés (routes /api/integrations/etat et /webhooks)"),
            m("confidentialite", "Registre des incidents de confidentialité (Loi 25)", "coeur", "Onglet Administration › Confidentialité, exports CSV/PDF (routes /api/securite/incidents)"),
            m("effectifs", "Urgences-santé — socle (gestion des effectifs)", "effectifs", "Menu Urgences-santé › Administration des effectifs (Référentiels, Milieux, Périmètres, Journal) : référentiels datés, unités et périmètres, milieux physiques et preuves de présence, journal des décisions (routes /api/effectifs)"),
            m("effectifs_regles", "Urgences-santé — moteur de règles", "effectifs", "effectifs", "Menu Urgences-santé › Administration des effectifs › Règles : règles versionnées par contexte et par portée, simulateur, décisions tracées (routes /api/effectifs/regles)"),
            m("effectifs_planification", "Urgences-santé — planification", "effectifs", "effectifs_regles", "Menu Urgences-santé › Gestion des quarts (Comblement, Quarts et équipages, Postes, Périodes, Ressources, Quotas) : périodes à collecte, postes, quarts et équipages, attribution contrôlée, comblement, quotas (routes /api/effectifs/planification)"),
            m("effectifs_disponibilites", "Urgences-santé — disponibilités", "effectifs", "effectifs_planification", "Menu Mon espace › Mes disponibilités et Urgences-santé › Disponibilités de la période : dépôt par période soumis aux règles, approbation, bassin de candidats motivé, attribution de fin de collecte (routes /api/effectifs/disponibilites)"),
            m("effectifs_absences", "Urgences-santé — absences", "effectifs", "effectifs_planification", "Catégorie « effectifs » du formulaire Absences › Déclarer et Urgences-santé › Absences et comblement : décision automatique conservée, impact sur le comblement, dérogation motivée, places rendues vacantes (routes /api/absences, /api/effectifs/absences)"),
            m("effectifs_echanges", "Urgences-santé — échanges de quarts", "effectifs", "effectifs_planification", "Menu Mon espace › Mes échanges et Urgences-santé › Demandes et échanges : échanges jugés dans les deux sens (repos, semaine, contiguïté), politique entre périodes par règle, validation du planificateur (routes /api/effectifs/echanges)"),
            m("effectifs_agph", "Urgences-santé — AGPH et remplacements long terme", "effectifs", "effectifs_planification", "Menu Mon espace › Mon AGPH et Urgences-santé › Confection annuelle (AGPH) : campagne annuelle à rangs d'ancienneté figés, choix unique minuté, attribution automatique, statuts TC / TPR / TP, remplacements long terme à manifestation d'intérêt (routes /api/effectifs/agph)"),
            m("effectifs_presence", "Urgences-santé — présence et pointage", "effectifs", "effectifs_planification", "Menu Mon espace › Mon pointage et Urgences-santé › Pointage et présence (console) : pointage au quart publié prouvé par distance au milieu, code QR de lieu ou réseau (la position n'est jamais conservée), présence préalable rémunérée, saisie a posteriori justifiée, approbation des écarts, import d'horodatages externes (routes /api/effectifs/presence)"),
            m("effectifs_temps", "Urgences-santé — feuilles de temps", "effectifs", "effectifs_presence", "Menu Mon espace › Ma feuille de temps et Urgences-santé › Feuilles de temps (console) : feuille générée depuis l'horaire publié et les pointages, segments typés et versionnés, écart planifié / déclaré, anomalies par règles en temps réel, heures supplémentaires par seuils (jour, semaine), approbation à paliers, verrouillage, lot de paie codé et signé, relevés regroupés (routes /api/effectifs/temps)"),
            m("effectifs_communication", "Urgences-santé — communication du personnel", "effectifs", "effectifs", "Menu Urgences-santé › Communication (Messagerie du personnel, Appeler un collègue, Listes de distribution) : messagerie sécurisée — fil de conversation, réponse privée, pièces jointes, appels, diffusions (routes /api/communication)"),
            m("effectifs_integrations", "Urgences-santé — intégrations", "effectifs", "effectifs_temps", "Menu Urgences-santé › Administration des effectifs › Intégrations : connecteurs RAO, SAAV, PFT, BD Corpo, Microsoft Graph, bus (ESB) et entrepôt de données, chacun en simulateur ou en HTTP (https, jeton par variable d'environnement), boîte d'envoi transactionnelle retentée et rejouable, relais du journal vers le bus et l'entrepôt (routes /api/effectifs/integrations)"),
            m("alertes", "Communications cliniques — alertes (socle)", "communications", "Menu Alertes (Nouvelle alerte, Suivi, Mes alertes) et Administration › Paramètres des alertes : alertes priorisées à des personnes ou des groupes, réponses prédéfinies, modèles, suivi partagé des lectures et des prises en charge (routes /api/alertes, ADR 0074)"),
            m("paie", "Paie québécoise", "paie", "effectifs_temps", "Menu Paie (Cycles de paie, Dossiers de rémunération, Remises et relevés, Mes bulletins de paie) et Administration › Paramètres de la paie : paie calculée sur les seules feuilles de temps approuvées, retenues fédérales et québécoises (RRQ, AE, RQAP), cotisations de l'employeur (FSS, CNT, CNESST), remises mensuelles, RL-1 et T4 ; une feuille payée est figée (routes /api/paie, ADR 0076)"),
            m("byok", "Clé de chiffrement du client (BYOK)", "infrastructure", "Option d'hébergement — aucun écran ; se règle au déploiement"),
            m("dedicated", "Infrastructure dédiée", "infrastructure", "Option d'hébergement — aucun écran ; se règle au déploiement"));

    private static final Map<String, Definition> PAR_CODE = new LinkedHashMap<>();

    static {
        MODULES.forEach(d -> PAR_CODE.put(d.code(), d));
    }

    public static final List<String> CODES = MODULES.stream().map(Definition::code).toList();

    public static Optional<Definition> definitionDe(String code) {
        return Optional.ofNullable(PAR_CODE.get(code));
    }

    /** Les modules qui dépendent directement de celui-ci. */
    public static List<String> dependantsDe(String code) {
        return MODULES.stream().filter(m -> code.equals(m.requiert())).map(Definition::code).toList();
    }

    /**
     * Rend une sélection cohérente, et dit ce qu'elle a dû changer. L'état précédent
     * ({@code actuels}, facultatif) est ce qui distingue un ajout d'un retrait : ce que l'appelant
     * a retiré emporte ses dépendants (d'abord, sinon la règle des prérequis rendrait le parent
     * que l'on vient d'ôter), puis ce qui reste obtient ses prérequis.
     */
    public static Resolution resoudreDependances(List<String> demandes, List<String> actuels) {
        Set<String> retenus = new LinkedHashSet<>(demandes.stream().filter(PAR_CODE::containsKey).toList());
        List<String> ajoutes = new ArrayList<>();
        List<String> retires = new ArrayList<>();
        if (actuels != null) {
            Deque<String> aPropager = new ArrayDeque<>(actuels.stream().filter(c -> PAR_CODE.containsKey(c) && !retenus.contains(c)).toList());
            while (!aPropager.isEmpty()) {
                String parent = aPropager.poll();
                for (String enfant : dependantsDe(parent)) {
                    if (retenus.remove(enfant)) {
                        retires.add(enfant);
                        aPropager.add(enfant);
                    }
                }
            }
        }
        boolean progresse = true;
        while (progresse) {
            progresse = false;
            for (String code : new ArrayList<>(retenus)) {
                String requis = PAR_CODE.get(code).requiert();
                if (requis != null && retenus.add(requis)) {
                    ajoutes.add(requis);
                    progresse = true;
                }
            }
        }
        // Un module demandé mais absent du catalogue n'est pas « retiré » : il n'a jamais existé.
        Set<String> tousRetires = new LinkedHashSet<>(retires);
        demandes.stream().filter(c -> PAR_CODE.containsKey(c) && !retenus.contains(c)).forEach(tousRetires::add);
        return new Resolution(MODULES.stream().map(Definition::code).filter(retenus::contains).toList(), List.copyOf(new LinkedHashSet<>(ajoutes)), List.copyOf(tousRetires));
    }

    /** Les codes inconnus d'une demande — ce que l'appelant doit refuser. */
    public static List<String> codesInconnus(List<String> demandes) {
        return List.copyOf(new LinkedHashSet<>(demandes.stream().filter(c -> !PAR_CODE.containsKey(c)).toList()));
    }
}
