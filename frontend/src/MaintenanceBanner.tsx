/**
 * Bannière de maintenance (AO-5338 Global-19) : affiche le message système actif (configuré par le
 * superadmin) en tête de l'application. Rôle `alert` pour l'accessibilité.
 */
import { useEffect, useState } from "react";

interface Maintenance {
  message: string; level: "info" | "warning" | "critical"; active: boolean;
  /** La prochaine maintenance annoncée (grille scolaire 29 : 72 h de préavis), montrée à tous. */
  prochaine?: { debut: string; fin: string; description: string } | null;
}
const quand = (iso: string) => new Date(iso).toLocaleString("fr-CA", { dateStyle: "long", timeStyle: "short" });
const BG: Record<string, string> = { info: "#eff6ff", warning: "#fef3c7", critical: "#fee2e2" };
const FG: Record<string, string> = { info: "#1e40af", warning: "#92400e", critical: "#991b1b" };

export default function MaintenanceBanner() {
  const [m, setM] = useState<Maintenance | null>(null);
  useEffect(() => {
    let alive = true;
    fetch("/api/saas/maintenance").then((r) => (r.ok ? r.json() : null)).then((d) => { if (alive) setM(d); }).catch(() => {});
    return () => { alive = false; };
  }, []);
  if (!m) return null;
  if (!m.active || !m.message) {
    if (!m.prochaine) return null;
    return (
      <div role="status" data-testid="maintenance-annonce"
        style={{ background: BG.info, color: FG.info, border: `1px solid ${FG.info}33`, borderRadius: 10, padding: "10px 14px", marginBottom: 16 }}>
        <span aria-hidden="true">🛠️</span> Maintenance annoncée du {quand(m.prochaine.debut)} au {quand(m.prochaine.fin)} : {m.prochaine.description}
      </div>
    );
  }
  return (
    <div role="alert" data-testid="maintenance-banner" tabIndex={0}
      style={{ background: BG[m.level], color: FG[m.level], border: `1px solid ${FG[m.level]}33`, borderRadius: 10, padding: "10px 14px", marginBottom: 16, fontWeight: 600 }}>
      <span aria-hidden="true">{m.level === "critical" ? "⛔" : m.level === "warning" ? "⚠️" : "ℹ️"}</span> {m.message}
    </div>
  );
}
