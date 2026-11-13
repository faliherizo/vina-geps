import { test, expect } from "@playwright/test";
import { authedContext } from "./helpers/auth";

const API = `http://localhost:${process.env.E2E_API_PORT ?? "4000"}`;

/**
 * E2E — MFA FIDO2 / WebAuthn (AO-5338 #93). Cérémonies d'enregistrement et d'authentification
 * d'une passkey (mode mock : la vérification cryptographique est branchée au déploiement).
 */
test("webauthn : enregistrement + authentification d'une passkey (mock)", async ({}) => {
  const api = await authedContext();
  const credId = `cred-${Date.now()}`;
  try {
    // Enregistrement.
    const regOpts = await (await api.post(`${API}/api/auth/webauthn/register/begin`)).json();
    expect(regOpts.challenge).toBeTruthy();
    const reg = await api.post(`${API}/api/auth/webauthn/register/finish`, { data: { credentialId: credId, publicKey: "pk-e2e", challenge: regOpts.challenge, counter: 0 } });
    expect(reg.status()).toBe(201);

    // La passkey apparaît dans la liste.
    const creds = await (await api.get(`${API}/api/auth/webauthn/credentials`)).json();
    expect(creds.some((c: any) => c.credentialId === credId)).toBe(true);

    // Authentification avec compteur croissant.
    const authOpts = await (await api.post(`${API}/api/auth/webauthn/authenticate/begin`)).json();
    const auth = await api.post(`${API}/api/auth/webauthn/authenticate/finish`, { data: { credentialId: credId, challenge: authOpts.challenge, counter: 1 } });
    expect(auth.status()).toBe(200);
    expect((await auth.json()).ok).toBe(true);
  } finally {
    await api.dispose();
  }
});
