package ca.vina.nexu.partage.courrier;

/**
 * Port : envoyer un courriel. L'expéditeur est celui du déploiement ({@code MAIL_FROM}), jamais
 * écrit en dur — un expéditeur étranger au domaine se fait rejeter par les filtres. Deux
 * adaptateurs : SMTP dès que {@code SMTP_HOST} est réglé, sinon un journal qui garde ce qui
 * aurait dû partir — et qui se déclare comme tel, pour qu'un écran ne fasse pas croire à un envoi.
 */
public interface Courrier {

    record Message(String to, String subject, String text) {
    }

    /** Ce que l'écran dit du transport : {@code smtp} avec son hôte, ou {@code journal}. */
    record Transport(String mode, String hote, String expediteur) {
    }

    /** Vrai si le message est parti (ou a été accepté par la passerelle). */
    boolean envoyer(Message message);

    default Transport transport() {
        return new Transport("journal", null, EXPEDITEUR_PAR_DEFAUT);
    }

    String EXPEDITEUR_PAR_DEFAUT = "ne-pas-repondre@nexuvina.example";
}
