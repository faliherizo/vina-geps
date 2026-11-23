package ca.vina.nexu.alertes.domaine;

import java.util.Optional;

/** Port : retrouver l'alerte à laquelle un numéro répond, et tenir la trace des textos reçus (contre le rejeu). */
public interface TextosEntrantsPort {

    /** La remise sans réponse la plus récente dont le destinataire a ce numéro (E.164), tous organismes confondus. */
    Optional<TextosEntrants.RemiseOuverte> derniereRemiseOuverte(String numeroE164);

    boolean dejaRecu(String messageSid);

    void consigner(TextosEntrants.Recu recu);
}
