package ca.vina.nexu.alertes.domaine;

/**
 * Ce qui suit une lecture ou une réponse : une cascade s'arrête dès que son critère est atteint, sans attendre sa
 * prochaine échéance. Un organisme sans cascade n'a rien à faire.
 */
@FunctionalInterface
public interface ReactionsAlertes {

    void apresReaction(String tenantId, String alerteId, String instant);

    ReactionsAlertes AUCUNE = (tenantId, alerteId, instant) -> { };
}
