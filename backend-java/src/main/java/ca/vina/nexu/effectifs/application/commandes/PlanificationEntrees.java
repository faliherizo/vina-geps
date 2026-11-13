package ca.vina.nexu.effectifs.application.commandes;

/** La lecture des nombres d'un corps JSON, comme {@code Number(v)} du Node : NaN pour ce qui n'en est pas un. */
final class PlanificationEntrees {

    private PlanificationEntrees() {
    }

    static double nombre(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        try {
            String s = v == null ? "" : v.toString().trim();
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
