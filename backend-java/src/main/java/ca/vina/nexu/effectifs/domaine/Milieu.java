package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Un milieu physique et les preuves qu'il exige. Le secret du code QR n'y figure jamais. */
public record Milieu(String id, String code, String libelle, String typeCode, String uniteCode, String adresse,
                     Double latitude, Double longitude, Integer rayonMetres, boolean actif, List<Milieux.Preuve> preuves) { }
