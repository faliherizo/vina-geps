package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.notifications.domaine.Canal;
import ca.vina.nexu.notifications.domaine.CanalJournal;
import ca.vina.nexu.notifications.domaine.Evenements;
import ca.vina.nexu.notifications.domaine.Evenements.Evenement;
import ca.vina.nexu.notifications.domaine.Notifications;
import ca.vina.nexu.notifications.domaine.StatutEnvoi;
import ca.vina.nexu.notifications.domaine.TypeDestinataire;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * **Le dispatcher des avis** (ADR 0029) — port de {@code notifications/avis.ts} et l'adaptateur du port
 * {@link PublicationAvis} : un événement, des personnes, et les canaux que chacune a laissés ouverts —
 * courriel (journalisé), texto (porteur réel, journal), poussée (navigateurs, appareils). Il ne lève
 * jamais vers le métier ; ce qui est parti, refusé ou échoué se lit au journal des notifications.
 */
@Repository
public class NotifierEvenement implements PublicationAvis {

    private final JdbcClient jdbc;
    private final DepotNotifications notifications;
    private final DepotPreferences preferences;
    private final DepotAbonnementsPoussee poussee;
    private final AdressePublique adresse;

    /** Ce que la poussée ouvre quand l'avis ne dit rien : le tableau de bord. */
    private static final String URL_PAR_DEFAUT = "/admin/dashboard";

    public NotifierEvenement(JdbcClient jdbc, DepotNotifications notifications, DepotPreferences preferences, DepotAbonnementsPoussee poussee, AdressePublique adresse) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.preferences = preferences;
        this.poussee = poussee;
        this.adresse = adresse;
    }

    /** Le nom de l'établissement, pour signer le message ; « Nexu VINA » quand l'établissement est le défaut d'essai. */
    private String signature(String tenantId) {
        try {
            return jdbc.sql("SELECT name FROM tenants WHERE id = ?").params(tenantId).query(String.class).optional().orElse("Nexu VINA");
        } catch (RuntimeException e) {
            return "Nexu VINA";
        }
    }

    /**
     * Avise chaque personne par ses canaux effectifs ; un envoi par (personne, canal) demandé. La poussée
     * accompagne le premier canal écrit demandé (une par avis), ou part seule si aucun canal écrit n'est demandé.
     * Un avis d'événement n'emprunte que les canaux effectifs de la personne ; l'envoi manuel nomme les canaux
     * demandés, et un canal fermé y apparaît « désactivé » — l'encadrement doit le savoir.
     */
    @Override
    public List<Envoi> publier(PublicationAvis.Avis avis) {
        Evenement e = Evenements.parCode(avis.evenement()).orElseThrow(() -> new IllegalArgumentException("événement inconnu : " + avis.evenement()));
        boolean manuel = avis.canaux() != null;
        List<Canal> canaux = manuel ? avis.canaux().stream().map(Canal::de).flatMap(Optional::stream).toList() : List.of(Canal.values());
        List<Canal> ecrits = canaux.stream().filter(Canal::ecrit).toList();
        String etablissement = signature(avis.tenantId());
        String url = avis.url() == null ? URL_PAR_DEFAUT : avis.url();
        String sujet = Notifications.rendre(avis.sujet(), avis.variables());
        String corps = Notifications.rendre(avis.corps(), avis.variables());
        List<Envoi> envois = new ArrayList<>();
        for (String employeeId : avis.destinataires()) {
            List<Canal> effectifs = manuel ? ecrits : ecrits.stream().filter(c -> preferences.canalOuvert(avis.tenantId(), employeeId, c, e.code())).toList();
            boolean pousser = manuel ? canaux.contains(Canal.POUSSEE) : preferences.canalOuvert(avis.tenantId(), employeeId, Canal.POUSSEE, e.code());
            for (int rang = 0; rang < effectifs.size(); rang++) {
                Canal canal = effectifs.get(rang);
                String texte = canal == Canal.COURRIEL ? corps + ca.vina.nexu.notifications.domaine.Avis.piedDeCourriel(etablissement, e, adresse.pour(avis.tenantId()), url) : ca.vina.nexu.notifications.domaine.Avis.texteDuTexto(etablissement, corps);
                String objet = canal == Canal.COURRIEL ? "[" + etablissement + "] " + sujet : sujet;
                CanalJournal journal = CanalJournal.pour(canal).orElseThrow();
                Map<String, Object> n = notifications.envoyer(avis.tenantId(),
                        new Notifications.Demande(journal.code(), TypeDestinataire.EMPLOYEE.code(), List.of(employeeId), objet, texte, Map.of(), e.code(), url), pousser && rang == 0).get(0);
                envois.add(new Envoi(employeeId, canal.code(), (String) n.get("status"), (String) n.get("error")));
            }
            if (pousser) {
                boolean ouvert = preferences.canalOuvert(avis.tenantId(), employeeId, Canal.POUSSEE, e.code());
                if (ouvert && effectifs.isEmpty()) {
                    poussee.pousserPourNotification(avis.tenantId(), new DepotAbonnementsPoussee.Poussee(TypeDestinataire.EMPLOYEE.code(), employeeId, sujet, corps, url,
                            Evenements.etiquetteDe(e.code())));
                }
                envois.add(new Envoi(employeeId, Canal.POUSSEE.code(), (ouvert ? StatutEnvoi.SENT : StatutEnvoi.DESACTIVE).code(), ouvert ? null : "poussée fermée par le destinataire"));
            }
        }
        return envois;
    }

    /** Comme {@link #publier}, mais rien ne remonte : l'appelant est un métier qui a déjà écrit son fait. */
    public void publierSansLever(PublicationAvis.Avis avis) {
        try {
            publier(avis);
        } catch (RuntimeException e) {
            // journal absent, base d'essai réduite : l'événement métier est déjà consigné
        }
    }
}
