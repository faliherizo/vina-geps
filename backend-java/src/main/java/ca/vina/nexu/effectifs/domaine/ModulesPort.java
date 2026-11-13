package ca.vina.nexu.effectifs.domaine;

/** Les modules activés d'un établissement : chaque lot des effectifs a le sien (un module coupé ferme ses routes). */
public interface ModulesPort {
    boolean estActive(String tenantId, String module);
}
