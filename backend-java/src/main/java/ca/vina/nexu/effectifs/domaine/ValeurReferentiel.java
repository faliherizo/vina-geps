package ca.vina.nexu.effectifs.domaine;

import java.util.Map;

/** Une version datée d'une valeur de référentiel, telle que la base la garde. {@code type} est le code du type. */
public record ValeurReferentiel(String id, String type, String code, String libelleFr, String libelleEn, String comportement, int ordre,
                                Map<String, Object> attributs, String effectifDepuis, String effectifJusqua) implements Referentiels.Version { }
