package ca.vina.nexu.partage.journal.infrastructure;

import ca.vina.nexu.partage.journal.Masquage;
import ca.vina.nexu.partage.journal.Niveau;
import ca.vina.nexu.partage.securite.ContexteRequete;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Le filtre du journal (ADR 0041), miroir du middleware Node : un identifiant par requête
 * ({@code X-Requete-Id}), le contexte MDC pour les lignes émises pendant la requête, une ligne
 * {@code http} à la fin — niveau selon la méthode et le statut — et, pour une requête mutante d'un
 * compte authentifié, une ligne du journal des actions. Il ne lit ni le corps ni les en-têtes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltreJournalisation extends OncePerRequestFilter {

    static final String EN_TETE = "X-Requete-Id";
    static final String MDC_REQUETE = "requete";
    static final String MDC_UTILISATEUR = "utilisateur";
    static final String MDC_ORGANISME = "organisme";
    static final String MDC_ACTEUR_REEL = "acteurReel";
    static final String EVENEMENT_HTTP = "http";
    static final String EVENEMENT_JOURNAL = "journal";
    static final String EVENEMENT_ERREUR = "erreur";
    static final int STATUT_ERREUR_SERVEUR = 500;
    static final String PREFIXE_API = "/api/";
    private static final int OCTETS_IDENTIFIANT = 6;
    private static final Logger JOURNAL = LoggerFactory.getLogger(FiltreJournalisation.class);
    private static final SecureRandom ALEA = new SecureRandom();

    private final DepotJournalActions actions;
    private final Clock horloge;
    private final int retentionJours;

    public FiltreJournalisation(DepotJournalActions actions, Clock horloge, @Value("${JOURNAL_ACTIONS_RETENTION_JOURS:}") String retention) {
        this.actions = actions;
        this.horloge = horloge;
        this.retentionJours = DepotJournalActions.retentionLue(retention);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite) throws ServletException, IOException {
        byte[] octets = new byte[OCTETS_IDENTIFIANT];
        ALEA.nextBytes(octets);
        String identifiant = HexFormat.of().formatHex(octets);
        long debut = System.nanoTime();
        reponse.setHeader(EN_TETE, identifiant);
        MDC.put(MDC_REQUETE, identifiant);
        boolean echec = true;
        try {
            suite.doFilter(requete, reponse);
            echec = false;
        } catch (IOException | ServletException | RuntimeException e) {
            consignerEchec(requete, e);
            throw e;
        } finally {
            conclure(requete, reponse, new Issue(identifiant, (System.nanoTime() - debut) / 1_000_000, echec));
            MDC.clear();
        }
    }

    /** Ce que la requête a donné : son identifiant, sa durée, et si une exception l'a interrompue. */
    private record Issue(String identifiant, long dureeMs, boolean echec) { }

    /**
     * Une exception qu'aucun bord n'a traduite remonte jusqu'ici : le conteneur rendra une page
     * d'erreur, mais la cause n'est connue que maintenant — elle se consigne avec sa pile.
     */
    private void consignerEchec(HttpServletRequest requete, Exception e) {
        situer(contexteDe(requete));
        JOURNAL.atError().addKeyValue("evenement", EVENEMENT_ERREUR).addKeyValue("methode", requete.getMethod())
                .addKeyValue("chemin", Masquage.masquerChemin(requete.getRequestURI(), requete.getQueryString()))
                .addKeyValue("type", e.getClass().getName()).setCause(e).log("exception non traitée : {}", e.getClass().getName());
    }

    private static ContexteRequete contexteDe(HttpServletRequest requete) {
        return requete.getAttribute(ContexteRequete.ATTRIBUT) instanceof ContexteRequete c ? c : null;
    }

    private void conclure(HttpServletRequest requete, HttpServletResponse reponse, Issue issue) {
        ContexteRequete contexte = contexteDe(requete);
        situer(contexte);
        String chemin = Masquage.masquerChemin(requete.getRequestURI(), requete.getQueryString());
        // Interrompue par une exception, la réponse porte encore le 200 d'origine : ce n'est pas ce que reçoit le client.
        int statut = issue.echec() && reponse.getStatus() < STATUT_ERREUR_SERVEUR ? STATUT_ERREUR_SERVEUR : reponse.getStatus();
        LoggingEventBuilder ligne = selon(Masquage.niveauDeLaRequete(requete.getMethod(), statut))
                .addKeyValue("evenement", EVENEMENT_HTTP).addKeyValue("methode", requete.getMethod()).addKeyValue("chemin", chemin)
                .addKeyValue("statut", statut).addKeyValue("dureeMs", issue.dureeMs());
        String motif = Masquage.libelleDuStatut(statut);
        if (motif != null) {
            ligne = ligne.addKeyValue("motif", motif);
        }
        ligne.log("{} {} → {}", requete.getMethod(), chemin, statut);
        if (contexte != null && Masquage.estMutante(requete.getMethod()) && requete.getRequestURI().startsWith(PREFIXE_API)) {
            consignerAction(contexte, requete.getMethod(), chemin, statut, issue);
        }
    }

    /** Une requête mutante d'un compte authentifié laisse une ligne au journal des actions, qui se purge au passage. */
    private void consignerAction(ContexteRequete contexte, String methode, String chemin, int statut, Issue issue) {
        String sansRequete = chemin.contains("?") ? chemin.substring(0, chemin.indexOf('?')) : chemin;
        actions.consigner(new DepotJournalActions.Action(null, Instant.now(horloge).toString(), contexte.tenantId(), contexte.userId(), contexte.emprunteur(),
                methode, sansRequete, statut, issue.dureeMs(), issue.identifiant()));
        int purgees = actions.purgerSiDu(retentionJours);
        if (purgees > 0) {
            JOURNAL.atInfo().addKeyValue("evenement", EVENEMENT_JOURNAL).addKeyValue("purgees", purgees).addKeyValue("retentionJours", retentionJours)
                    .log("journal des actions : {} ligne(s) de plus de {} jours purgée(s)", purgees, retentionJours);
        }
    }

    /** Le contexte MDC apprend qui agit — le filtre de session est passé — pour la ligne http et celles qui suivraient. */
    private static void situer(ContexteRequete contexte) {
        if (contexte == null) {
            return;
        }
        MDC.put(MDC_UTILISATEUR, contexte.userId());
        if (contexte.tenantId() != null) {
            MDC.put(MDC_ORGANISME, contexte.tenantId());
        }
        if (contexte.emprunteur() != null) {
            MDC.put(MDC_ACTEUR_REEL, contexte.emprunteur());
        }
    }

    private static LoggingEventBuilder selon(Niveau niveau) {
        return switch (niveau) {
            case DEBUG -> JOURNAL.atDebug();
            case INFO -> JOURNAL.atInfo();
            case WARN -> JOURNAL.atWarn();
            case ERROR -> JOURNAL.atError();
        };
    }
}
