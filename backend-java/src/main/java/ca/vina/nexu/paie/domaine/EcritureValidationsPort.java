package ca.vina.nexu.paie.domaine;

/** Port d'écriture des validations des temps : chaque décision s'ajoute, la plus récente fait foi. */
public interface EcritureValidationsPort {

    void consigner(String tenantId, ValidationTemps validation);
}
