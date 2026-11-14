package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;

/** L'écriture des alertes. Une réponse ou un modèle s'écrit en entier (création ou remplacement). */
public interface EcritureAlertesPort {

    void enregistrer(String tenantId, ReponsePredefinie reponse);

    void enregistrer(String tenantId, Modele modele);

    /** L'alerte, ses réponses copiées et ses remises, en une fois. */
    void creer(Alerte alerte);

    void mettreAJour(String tenantId, String alerteId, Remise remise);
}
