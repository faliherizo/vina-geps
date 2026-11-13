import { test, expect } from "@playwright/test";
import { superadminContext } from "./helpers/auth";
import { createTenant, uniq } from "./helpers/organisme";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * E2E — Clés d'API / OAuth2 client-credentials (NF-08).
 * Un admin crée un client d'API (tenant-scoped) ; une intégration échange id+secret contre un jeton
 * via le point OAuth2 public, puis un secret erroné est refusé.
 */
test("OAuth2 client_credentials : création de client + délivrance de jeton", async ({ page }) => {
  const tenantId = await createTenant(page.request, uniq("Api"));
  const sa = await superadminContext();
  const H = { "X-Tenant-Id": tenantId };
  try {
    // Création du client d'API (secret remis une seule fois).
    const created = await (await sa.post(`${API}/api/integrations/api-clients`, { headers: H, data: { name: "ERP externe", scopes: ["read", "write"] } })).json();
    expect(created.clientId).toMatch(/^cid_/);
    expect(created.clientSecret).toMatch(/^csec_/);

    // La liste ne divulgue pas le secret.
    const list = await (await sa.get(`${API}/api/integrations/api-clients`, { headers: H })).json();
    expect(list[0].clientSecret).toBeUndefined();

    // Grant client_credentials → jeton d'accès (public, sans session).
    const tokenRes = await page.request.post(`${API}/api/oauth/token`, {
      data: { grant_type: "client_credentials", client_id: created.clientId, client_secret: created.clientSecret },
    });
    expect(tokenRes.status()).toBe(200);
    const token = await tokenRes.json();
    expect(token.token_type).toBe("Bearer");
    expect(token.access_token).toBeTruthy();
    expect(token.scope).toContain("read");

    // Secret erroné → 401 invalid_client.
    const bad = await page.request.post(`${API}/api/oauth/token`, {
      data: { grant_type: "client_credentials", client_id: created.clientId, client_secret: "wrong" },
    });
    expect(bad.status()).toBe(401);
    expect((await bad.json()).error).toBe("invalid_client");
  } finally {
    await sa.dispose();
  }
});
