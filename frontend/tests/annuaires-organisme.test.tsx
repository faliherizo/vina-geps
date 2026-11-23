/**
 * P-TN-2 vu de l'administration : la liste dit ce qui manque (secret non déployé, aucun défaut),
 * le formulaire envoie des règles ordonnées, et un refus du serveur s'affiche motif par motif.
 */
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AnnuairesOrganisme, { ecrireRegles, lireRegles, type Annuaire } from "../src/admin/AnnuairesOrganisme";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const SIEGE: Annuaire = {
  id: "ann-1", nom: "Siège", locataireEntra: "0f8fad5b-d9cb-469f-a165-70867728950e", clientId: "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  variableSecret: "ANNUAIRE_SIEGE", secretDeploye: false, domaines: ["urgences-sante.qc.ca"], revendication: "groups",
  correspondances: [{ groupe: "g-admin", role: "admin" }], roleParDefaut: null, actif: true,
};

const KEYCLOAK: Annuaire = {
  ...SIEGE, id: "ann-2", nom: "Keycloak", fournisseur: "oidc", locataireEntra: "", emetteur: "http://localhost:8180/realms/vina-geps", clientId: "vina-geps",
  revendication: "roles", secretDeploye: true,
};

const reponse = (corps: unknown, status = 200) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(corps) } as Response);

describe("lireRegles / ecrireRegles", () => {
  it("lit une règle par ligne, coupe au dernier « = », garde l'ordre et ignore les lignes vides", () => {
    expect(lireRegles(" g-admin = admin \n\n g=b = manager\nsans-role")).toEqual([
      { groupe: "g-admin", role: "admin" }, { groupe: "g=b", role: "manager" }, { groupe: "sans-role", role: "" }]);
    expect(ecrireRegles([{ groupe: "g1", role: "admin" }, { groupe: "g2", role: "employee" }])).toBe("g1 = admin\ng2 = employee");
  });
});

describe("AnnuairesOrganisme", () => {
  let conteneur: HTMLDivElement;
  let racine: Root;
  let apiFetch: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    conteneur = document.createElement("div");
    document.body.appendChild(conteneur);
    racine = createRoot(conteneur);
    apiFetch = vi.fn((chemin: string, init?: RequestInit) => {
      if (chemin === "/api/auth/assignable-roles") return reponse([{ role: "admin", label: "Administrateur" }, { role: "employee", label: "Employé" }]);
      if (chemin === "/api/auth/annuaires" && !init) return reponse({ annuaires: [SIEGE, KEYCLOAK], rappel: "https://app/api/auth/sso/annuaires/callback" });
      if (init?.method === "POST") return reponse({ errors: ["déclarer au moins un domaine de courriel admis", "le nom est requis (80 caractères au plus)"] }, 400);
      if (init?.method === "DELETE") return reponse(null, 204);
      return reponse({}, 200);
    });
  });
  afterEach(() => { act(() => racine.unmount()); conteneur.remove(); });
  const tid = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const rendre = () => act(async () => { racine.render(<AnnuairesOrganisme apiFetch={apiFetch} />); await new Promise((r) => setTimeout(r, 5)); });
  const attendre = () => act(async () => { await new Promise((r) => setTimeout(r, 5)); });

  it("liste les annuaires dans un tableau accessible et dit que le secret n'est pas déployé", async () => {
    await rendre();
    expect(tid("annuaires-rappel")!.textContent).toBe("https://app/api/auth/sso/annuaires/callback");
    const ligne = tid("annuaire-ann-1")!;
    expect(ligne.querySelector('th[scope="row"]')!.textContent).toBe("Siège");
    expect(ligne.textContent).toContain("✗ ANNUAIRE_SIEGE absente");
    expect(ligne.textContent).toContain("1 règle(s), sans défaut");
    expect(document.querySelector("caption")!.textContent).toBe("Annuaires de l'organisme");
    expect(tid("annuaire-ann-1-modifier")!.getAttribute("aria-label")).toBe("Modifier l'annuaire Siège");
  });

  it("affiche chaque motif du refus dans le formulaire de déclaration", async () => {
    await rendre();
    await act(async () => { tid("annuaire-nouveau-ouvrir")!.click(); });
    expect(document.querySelector('label[for="annuaire-nouveau-nom"]')).not.toBeNull();
    await act(async () => { (tid("annuaire-nouveau-regles") as HTMLTextAreaElement).value = "g-admin = admin"; });
    await act(async () => { (tid("annuaire-nouveau-formulaire") as HTMLFormElement).requestSubmit(); });
    await attendre();
    const envoi = apiFetch.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "POST")!;
    expect(JSON.parse(String((envoi[1] as RequestInit).body))).toMatchObject({ correspondances: [{ groupe: "g-admin", role: "admin" }], actif: true, roleParDefaut: null });
    const erreurs = tid("annuaire-nouveau-erreurs")!;
    expect([...erreurs.querySelectorAll("li")].map((li) => li.textContent)).toEqual([
      "déclarer au moins un domaine de courriel admis", "le nom est requis (80 caractères au plus)"]);
  });

  it("pré-remplit la modification et retire après confirmation", async () => {
    await rendre();
    await act(async () => { tid("annuaire-ann-1-modifier")!.click(); });
    expect((tid("annuaire-modification-regles") as HTMLTextAreaElement).value).toBe("g-admin = admin");
    expect((tid("annuaire-modification-variable") as HTMLInputElement).value).toBe("ANNUAIRE_SIEGE");
    await act(async () => { (tid("annuaire-modification-formulaire") as HTMLFormElement).requestSubmit(); });
    await attendre();
    expect(apiFetch).toHaveBeenCalledWith("/api/auth/annuaires/ann-1", expect.objectContaining({ method: "PUT" }));
    expect(tid("annuaires-info")!.textContent).toContain("Annuaire « Siège » modifié.");
    await act(async () => { tid("annuaire-ann-1-retirer")!.click(); });
    await act(async () => { tid("annuaire-retrait-confirm")!.click(); });
    await attendre();
    expect(apiFetch).toHaveBeenCalledWith("/api/auth/annuaires/ann-1", { method: "DELETE" });
    expect(tid("annuaires-info")!.textContent).toContain("Annuaire « Siège » retiré.");
  });

  it("OIDC générique : le fournisseur choisi remplace le locataire par l'émetteur, envoyé avec le corps ; la liste le nomme", async () => {
    await rendre();
    expect(tid("annuaire-ann-2")!.textContent).toContain("OpenID Connect (Keycloak, Okta…) — http://localhost:8180/realms/vina-geps");
    expect(tid("annuaire-ann-1")!.textContent).toContain("Microsoft Entra ID");
    await act(async () => { tid("annuaire-nouveau-ouvrir")!.click(); });
    expect(tid("annuaire-nouveau-emetteur")).toBeNull();
    const choix = tid("annuaire-nouveau-fournisseur") as HTMLSelectElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")!.set!.call(choix, "oidc");
      choix.dispatchEvent(new Event("change", { bubbles: true }));
    });
    expect(tid("annuaire-nouveau-locataire")).toBeNull();
    expect(document.querySelector('label[for="annuaire-nouveau-emetteur"]')!.textContent).toContain("Émetteur OIDC");
    await act(async () => { (tid("annuaire-nouveau-emetteur") as HTMLInputElement).value = "http://localhost:8180/realms/vina-geps"; });
    await act(async () => { (tid("annuaire-nouveau-formulaire") as HTMLFormElement).requestSubmit(); });
    await attendre();
    const envoi = apiFetch.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "POST")!;
    expect(JSON.parse(String((envoi[1] as RequestInit).body))).toMatchObject({ fournisseur: "oidc", emetteur: "http://localhost:8180/realms/vina-geps", locataireEntra: "" });
    await act(async () => { tid("annuaire-ann-2-modifier")!.click(); });
    expect((tid("annuaire-modification-emetteur") as HTMLInputElement).value).toBe("http://localhost:8180/realms/vina-geps");
  });

  it("LDAPS : l'adresse ldaps:// et le DN du compte de service remplacent l'émetteur et le client ; le corps porte le fournisseur", async () => {
    await rendre();
    await act(async () => { tid("annuaire-nouveau-ouvrir")!.click(); });
    const choix = tid("annuaire-nouveau-fournisseur") as HTMLSelectElement;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value")!.set!.call(choix, "ldaps");
      choix.dispatchEvent(new Event("change", { bubbles: true }));
    });
    expect(tid("annuaire-nouveau-locataire")).toBeNull();
    expect(document.querySelector('label[for="annuaire-nouveau-ldaps"]')!.textContent).toContain("Adresse LDAPS et base de recherche");
    expect(document.querySelector('label[for="annuaire-nouveau-client"]')!.textContent).toContain("DN du compte de service");
    expect(document.getElementById("annuaire-nouveau-ldaps-aide")!.textContent).toContain("ldap:// en clair est refusé");
    await act(async () => { (tid("annuaire-nouveau-emetteur") as HTMLInputElement).value = "ldaps://ad.us.local:636/OU=Personnel,DC=us,DC=local"; });
    await act(async () => { (tid("annuaire-nouveau-client") as HTMLInputElement).value = "CN=svc-geps,DC=us,DC=local"; });
    await act(async () => { (tid("annuaire-nouveau-formulaire") as HTMLFormElement).requestSubmit(); });
    await attendre();
    const envoi = apiFetch.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "POST")!;
    expect(JSON.parse(String((envoi[1] as RequestInit).body))).toMatchObject({ fournisseur: "ldaps",
      emetteur: "ldaps://ad.us.local:636/OU=Personnel,DC=us,DC=local", clientId: "CN=svc-geps,DC=us,DC=local" });
  });
});
