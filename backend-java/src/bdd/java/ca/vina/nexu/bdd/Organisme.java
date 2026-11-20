package ca.vina.nexu.bdd;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Un organisme d'Urgences-santé neuf</b>, par scénario : créé par le superadmin avec son
 * administration, les modules du produit, et les comptes des acteurs — rendus utilisables sans
 * franchir le mur de première connexion, qui a ses propres essais.
 */
final class Organisme {

    static final String ADMIN = "admin";
    private static final String MDP_ADMIN = "Zephyr-9!Quartz";
    private static final String FUSEAU_DES_QUARTS = ca.vina.nexu.effectifs.domaine.PresenceEffectifs.FUSEAU;
    static final String MDP_ACTEUR = "Passe!Profil2026";
    private static final List<String> COMMUNS = List.of("superadmin", "anonyme");
    private static final List<String> MODULES = List.of("effectifs", "effectifs_planification", "effectifs_regles", "effectifs_agph",
            "effectifs_absences", "effectifs_echanges", "effectifs_disponibilites", "effectifs_presence", "effectifs_temps",
            "effectifs_integrations", "effectifs_communication", "alertes", "approvals", "notifications", "integrations", "confidentialite");

    /** Le rôle de chaque acteur que les scénarios nomment, en plus de l'administration. */
    static final Map<String, String> ROLES = Map.of("julie", "employee", "marc", "employee", "chef", "manager", "directeur", "director");

    final String tenantId;
    final Map<String, ClientApi.Session> sessions = new LinkedHashMap<>();
    final Map<String, JsonNode> vars = new LinkedHashMap<>();

    private Organisme(String tenantId) {
        this.tenantId = tenantId;
    }

    private static ClientApi.Session superadmin() {
        return ClientApi.connecter("superadmin", "superadmin", "");
    }

    private static JsonNode exiger(ClientApi.Reponse r, String quoi) {
        if (r.statut() >= 300) {
            throw new IllegalStateException(quoi + " : " + r.statut() + " " + r.texte());
        }
        return r.json();
    }

    static Organisme creer() {
        ClientApi.Session sa = superadmin();
        ObjectNode demande = ClientApi.JSON.createObjectNode().put("name", "BDD-" + UUID.randomUUID()).put("type", "government")
                .put("country", "CA").put("region", "QC").put("planCode", "enterprise").put("adminUsername", ADMIN).put("adminPassword", MDP_ADMIN);
        JsonNode cree = exiger(ClientApi.appeler(sa, "POST", "/api/superadmin/tenants", demande), "création de l'organisme");
        Organisme o = new Organisme(cree.get("tenant").get("id").asString());
        o.leverLeMurDePremiereConnexion(sa, cree.get("admin").get("userId").asString());
        o.poserLesModules(sa);
        o.sessions.put(ADMIN, ClientApi.connecter(ADMIN, MDP_ADMIN, o.tenantId));
        o.sessions.put("superadmin", sa);
        o.sessions.put("anonyme", ClientApi.Session.ANONYME);
        o.vars.put("suffixe", ClientApi.JSON.getNodeFactory().stringNode(o.tenantId.substring(o.tenantId.length() - 6)));
        o.vars.put("tenant", ClientApi.JSON.getNodeFactory().stringNode(o.tenantId));
        // Une règle ne prend pas effet dans le passé : une date fixe deviendrait passée et casserait la suite.
        o.vars.put("demain", ClientApi.JSON.getNodeFactory().stringNode(java.time.LocalDate.now().plusDays(1).toString()));
        // Le pointage en temps réel cherche le quart autour de maintenant, dans le fuseau des quarts (PresenceEffectifs.FUSEAU).
        java.time.LocalDate jour = java.time.LocalDate.now(java.time.ZoneId.of(FUSEAU_DES_QUARTS));
        o.vars.put("aujourdhui", ClientApi.JSON.getNodeFactory().stringNode(jour.toString()));
        o.vars.put("hier", ClientApi.JSON.getNodeFactory().stringNode(jour.minusDays(1).toString()));
        // Une absence assez longue pour franchir un seuil de remplacement long terme, à partir daujourdhui (AD-02).
        o.vars.put("dans30jours", ClientApi.JSON.getNodeFactory().stringNode(jour.plusDays(30).toString()));
        ROLES.forEach(o::creerActeur);
        return o;
    }

    /** Les acteurs et les valeurs d'un autre organisme, sous leur nom suffixé ; le superadmin et l'anonyme restent communs. */
    void adjoindre(Organisme autre, String suffixe) {
        autre.sessions.forEach((cle, session) -> {
            if (!COMMUNS.contains(cle)) {
                sessions.put(cle + suffixe, session);
            }
        });
        autre.vars.forEach((cle, valeur) -> vars.put(cle + suffixe, valeur));
    }

    private void leverLeMurDePremiereConnexion(ClientApi.Session qui, String userId) {
        ObjectNode drapeaux = ClientApi.JSON.createObjectNode().put("securitySetupRequired", false).put("mustChangePassword", false);
        exiger(ClientApi.appeler(qui, "PATCH", "/api/auth/users/" + userId, drapeaux), "premier accès de " + userId);
    }

    /** Poser exactement ces modules : on repart de rien, puisqu'un socle retiré emporte ses dépendants. */
    private void poserLesModules(ClientApi.Session sa) {
        for (List<String> liste : List.of(List.<String>of(), MODULES)) {
            ObjectNode corps = ClientApi.JSON.createObjectNode();
            var modules = corps.putArray("modules");
            liste.forEach(modules::add);
            exiger(ClientApi.appeler(sa, "PUT", "/api/saas/tenants/" + tenantId + "/modules", corps), "modules");
        }
    }

    private void creerActeur(String cle, String role) {
        String identifiant = cle + "-" + vars.get("suffixe").asString();
        ObjectNode compte = ClientApi.JSON.createObjectNode().put("username", identifiant).put("password", MDP_ACTEUR)
                .put("role", role).put("mustChangePassword", false);
        JsonNode cree = exiger(ClientApi.appeler(sessions.get(ADMIN), "POST", "/api/auth/users", compte), "compte " + identifiant);
        String id = cree.get("id").asString();
        leverLeMurDePremiereConnexion(sessions.get(ADMIN), id);
        vars.put("u_" + cle, ClientApi.JSON.getNodeFactory().stringNode(id));
        // La messagerie désigne une personne par son nom de connexion (GardeCommunication.acteurDe).
        vars.put("n_" + cle, ClientApi.JSON.getNodeFactory().stringNode(identifiant));
        sessions.put(cle, ClientApi.connecter(identifiant, MDP_ACTEUR, tenantId));
    }
}
