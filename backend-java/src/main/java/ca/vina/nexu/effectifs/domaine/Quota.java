package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Un quota de comblement d'une unité — pour une plage, une nature, ou toutes — avec ses versions datées et
 * ses surcharges ; celui en vigueur se calcule à la lecture.
 */
public record Quota(String cle, String uniteCode, String plageCode, String natureCode, List<Version> versions, List<SurchargeMotivee> surcharges) {

    public record Version(String id, int quota, String effectifDepuis, String effectifJusqua) implements Referentiels.Version { }

    public record SurchargeMotivee(String id, int valeur, String depuis, String jusqua, String creeLe, String motif, String auteur) { }

    public static String cleDe(String unite, String plage, String nature) {
        return unite + "|" + (plage == null ? "*" : plage) + "|" + (nature == null ? "*" : nature);
    }

    public java.util.Optional<Planification.QuotaEffectif> enVigueur(String instant) {
        return Planification.quotaEffectif(
                versions.stream().map(v -> new Planification.VersionQuota(v.quota(), v.effectifDepuis(), v.effectifJusqua())).toList(),
                surcharges.stream().map(s -> new Planification.Surcharge(s.valeur(), s.depuis(), s.jusqua(), s.creeLe())).toList(), instant);
    }
}
