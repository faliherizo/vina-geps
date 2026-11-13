import { test, expect } from "@playwright/test";
import { authedContext } from "./helpers/auth";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * E2E — Console SIEM (événements enrichis sévérité/niveau + synthèse) + rétention/effacement Loi 25.
 */
test("Console SIEM : synthèse par niveau + purge de rétention + effacement", async () => {
  const api = await authedContext();
  try {
    // La connexion admin a généré des événements d'audit → la console les classe.
    const console = await (await api.get(`${API}/api/auth/security/siem-console`)).json();
    expect(console.summary).toHaveProperty("high");
    expect(console.summary).toHaveProperty("total");
    expect(Array.isArray(console.events)).toBe(true);
    if (console.events.length) expect(console.events[0]).toHaveProperty("severity");

    // Filtre par niveau minimum élevé → ne renvoie que des événements « high ».
    const high = await (await api.get(`${API}/api/auth/security/siem-console?minLevel=high`)).json();
    expect(high.events.every((e: any) => e.level === "high")).toBe(true);

    // Rétention : purge avec une fenêtre très large → aucune entrée récente supprimée.
    const purge = await (await api.post(`${API}/api/auth/security/retention/purge`, { data: { retentionDays: 3650 } })).json();
    expect(purge.retentionDays).toBe(3650);
    expect(typeof purge.purged).toBe("number");

    // Rétention invalide → 400.
    const bad = await api.post(`${API}/api/auth/security/retention/purge`, { data: { retentionDays: 0 } });
    expect(bad.status()).toBe(400);

    // Effacement (droit à l'oubli) d'un utilisateur inexistant → 0 anonymisé, sans erreur.
    const erase = await (await api.post(`${API}/api/auth/security/retention/erase-user`, { data: { username: "nobody-xyz" } })).json();
    expect(erase.anonymized).toBe(0);
  } finally {
    await api.dispose();
  }
});
