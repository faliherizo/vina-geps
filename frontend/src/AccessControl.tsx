/**
 * **Accès et habilitations** — qui a le droit de quoi, et jusqu'à quand.
 *
 * La page portait jusqu'ici une seule matrice : rôle × permission pour le module de paie
 * variable. Elle en garde le contenu et gagne trois onglets, parce qu'un usager qui cherche
 * « qui peut voir ce rapport ? » ne sait pas, lui, si la réponse vit dans une matrice de rôles ou
 * dans un octroi daté. **Deux pages pour une question, c'est une page que personne ne trouve.**
 *
 * Trois décisions d'interface :
 *
 * - **L'absence de droit est affichée, pas déduite d'un vide.** `aucun` est une permission
 *   déclarée ; un tableau qui la montrerait comme une case vide confondrait un refus explicite
 *   avec un oubli de saisie.
 * - **La date d'observation est explicite.** « Qui accède à ce rapport ? » n'a pas de réponse sans
 *   elle : un octroi expiré hier et un octroi qui commence demain donnent tous deux une liste
 *   vide aujourd'hui, pour des raisons opposées.
 * - **Les échéances proches sont nommées.** Un droit qui cesse dans trois jours est la seule
 *   information que personne ne pense à chercher avant qu'elle ne manque.
 */
import { useCallback, useEffect, useState } from "react";
import MifiTabPage from "./MifiTabPage";
import { Badge, EtatVide, Message, Onglets, Tableau, type Colonne, type Onglet } from "./composants";
import { useAuth } from "./AuthContext";
import { useT } from "./i18n";
import { useOnglet } from "./navigation";

type OngletAcces = "objets" | "octrois" | "qui";
const ONGLETS: OngletAcces[] = ["objets", "octrois", "qui"];

interface ObjetProtege { code: string; type: string; libelle: string }
interface Octroi {
  sujetType: string; sujetId: string; objetCode: string; permission: string;
  effectifDu: string; effectifAu: string | null; motif: string | null;
}
interface DroitSurObjet {
  sujetType: string; sujetId: string; permission: string; origine: string; explication: string;
}
interface LigneFiche {
  objetCode: string; objetLibelle: string; permission: string; origine: string;
  effectifAu: string | null; joursRestants: number | null;
}
interface Fiche {
  utilisateurId: string; le: string; lignes: LigneFiche[]; echeancesProches: LigneFiche[];
  motifs: string[];
}

const PERMISSIONS: Record<string, string> = {
  aucun: "Aucun accès", lecture: "Lecture", lecture_ecriture: "Lecture et écriture",
};
const TYPES: Record<string, string> = {
  fonctionnalite: "Fonctionnalité", formulaire: "Formulaire",
  rapport: "Rapport", composante: "Composante",
};
const mot = (t: Record<string, string>, c: string) => t[c] ?? c;

/** Le ton d'une permission. `aucun` est **rouge**, jamais gris : un refus n'est pas une absence. */
const tonPermission = (p: string): "succes" | "info" | "erreur" =>
  p === "lecture_ecriture" ? "succes" : p === "lecture" ? "info" : "erreur";

const aujourdhui = () => new Date().toISOString().slice(0, 10);

function Habilitations({ tenantId, setError, setSuccess }: {
  tenantId: string; setError: (m: string) => void; setSuccess: (m: string) => void;
}) {
  const { apiFetch } = useAuth();
  const [onglet, setOnglet] = useOnglet<OngletAcces>("access", "objets",
    (s): s is OngletAcces => (ONGLETS as string[]).includes(s));

  const [objets, setObjets] = useState<ObjetProtege[]>([]);
  const [octrois, setOctrois] = useState<Octroi[]>([]);
  const [types, setTypes] = useState<string[]>([]);
  const [permissions, setPermissions] = useState<string[]>([]);
  const [nouvelObjet, setNouvelObjet] = useState({ code: "", type: "rapport", libelle: "" });
  const [nouvelOctroi, setNouvelOctroi] = useState({
    sujetType: "utilisateur", sujetId: "", objetCode: "", permission: "lecture",
    effectifDu: "", effectifAu: "", motif: "",
  });
  const [objetInterroge, setObjetInterroge] = useState("");
  const [personneInterrogee, setPersonneInterrogee] = useState("");
  const [profilAJoindre, setProfilAJoindre] = useState("");
  const [le, setLe] = useState(aujourdhui());
  const [droits, setDroits] = useState<DroitSurObjet[] | null>(null);
  const [fiche, setFiche] = useState<Fiche | null>(null);
  const [profils, setProfils] = useState<string[]>([]);
  const [permissionUnique, setPermissionUnique] = useState<{ permission: string; explication: string } | null>(null);

  const lire = useCallback(async <T,>(chemin: string): Promise<T | null> => {
    const r = await apiFetch(chemin);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setError(j.errors?.join(", ") || `Erreur ${r.status}`);
      return null;
    }
    return r.json();
  }, [apiFetch, setError]);

  const envoyer = useCallback(async <T,>(chemin: string, corps: unknown, message: string,
    methode = "POST"): Promise<T | null> => {
    setError("");
    const r = await apiFetch(chemin, {
      method: methode, headers: { "Content-Type": "application/json" }, body: JSON.stringify(corps),
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) { setError(j.errors?.join(", ") || `Erreur ${r.status}`); return null; }
    setSuccess(message);
    return j as T;
  }, [apiFetch, setError, setSuccess]);

  const chargerObjets = useCallback(async () => {
    const o = await lire<ObjetProtege[]>("/api/habilitations/objets");
    if (o) { setObjets(o); setObjetInterroge((x) => x || o[0]?.code || ""); }
  }, [lire]);

  const chargerOctrois = useCallback(async () => {
    const o = await lire<Octroi[]>("/api/habilitations/octrois");
    if (o) setOctrois(o);
  }, [lire]);

  useEffect(() => {
    void lire<string[]>("/api/habilitations/types-objet").then((t) => t && setTypes(t));
    void lire<string[]>("/api/habilitations/permissions").then((p) => p && setPermissions(p));
    void chargerObjets();
    void chargerOctrois();
  }, [lire, chargerObjets, chargerOctrois]);

  useEffect(() => {
    if (!personneInterrogee.trim()) { setProfils([]); return; }
    void lire<string[]>(`/api/habilitations/utilisateurs/${personneInterrogee}/profils`)
      .then((p) => p && setProfils(p));
  }, [personneInterrogee, lire]);

  const onglets: ReadonlyArray<Onglet<OngletAcces>> = [
    { id: "objets", libelle: "Objets protégés", testId: "access-tab-objets" },
    { id: "octrois", libelle: "Octrois datés", testId: "access-tab-octrois" },
    { id: "qui", libelle: "Qui accède à quoi", testId: "access-tab-qui" },
  ];

  const colonnesOctrois: ReadonlyArray<Colonne<Octroi>> = [
    { id: "sujet", libelle: "Sujet", tri: (o) => `${o.sujetType}:${o.sujetId}` },
    { id: "objetCode", libelle: "Objet", tri: (o) => o.objetCode },
    { id: "permission", libelle: "Permission" },
    { id: "periode", libelle: "Période d'effet" },
    { id: "motif", libelle: "Motif" },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <>
      <Onglets onglets={onglets} actif={onglet} onChange={setOnglet}
        libelle="Vues des accès et des habilitations" testId="access-tabs" prefixeIds="access" />

      {onglet === "objets" && (
        <div id="access-tabpanel-objets" role="tabpanel" aria-labelledby="access-tabbtn-objets">
          <p>
            Un objet protégé est ce sur quoi un droit se pose : une fonctionnalité, un formulaire,
            un rapport, une composante d'écran. Déclarer l'objet précède l'octroi — un octroi sur un
            objet inconnu est refusé par son nom, plutôt qu'enregistré sans effet.
          </p>
          <div className="champ">
            <label htmlFor="access-objet-code">Code de l'objet</label>
            <input id="access-objet-code" data-testid="access-objet-code" type="text"
              value={nouvelObjet.code}
              onChange={(e) => setNouvelObjet({ ...nouvelObjet, code: e.target.value })} />
          </div>
          <div className="champ">
            <label htmlFor="access-objet-type">Type</label>
            <select id="access-objet-type" data-testid="access-objet-type" value={nouvelObjet.type}
              onChange={(e) => setNouvelObjet({ ...nouvelObjet, type: e.target.value })}>
              {types.map((t) => <option key={t} value={t}>{mot(TYPES, t)}</option>)}
            </select>
          </div>
          <div className="champ">
            <label htmlFor="access-objet-libelle">Libellé</label>
            <input id="access-objet-libelle" data-testid="access-objet-libelle" type="text"
              value={nouvelObjet.libelle}
              onChange={(e) => setNouvelObjet({ ...nouvelObjet, libelle: e.target.value })} />
          </div>
          <button type="button" data-testid="access-declarer-objet" disabled={!nouvelObjet.code.trim()}
            onClick={async () => {
              if (await envoyer("/api/habilitations/objets", nouvelObjet,
                `Objet « ${nouvelObjet.code} » déclaré.`, "PUT")) {
                setNouvelObjet({ code: "", type: "rapport", libelle: "" });
                await chargerObjets();
              }
            }}>
            Déclarer l'objet
          </button>

          <Tableau
            colonnes={[
              { id: "code", libelle: "Code", tri: (o: ObjetProtege) => o.code },
              { id: "type", libelle: "Type", tri: (o: ObjetProtege) => o.type },
              { id: "libelle", libelle: "Libellé" },
            ]}
            lignes={objets}
            legende="Objets protégés déclarés par l'organisme, sur lesquels un droit peut être octroyé"
            testId="access-table-objets"
            vide={<EtatVide titre="Aucun objet protégé">
              Déclarez un rapport, un formulaire ou une composante avant d'y poser un droit.
            </EtatVide>}
            rendre={(o) => (
              <tr key={o.code} data-testid={`access-objet-${o.code}`}>
                <td>{o.code}</td>
                <td>{mot(TYPES, o.type)}</td>
                <td>{o.libelle}</td>
              </tr>
            )} />
        </div>
      )}

      {onglet === "octrois" && (
        <div id="access-tabpanel-octrois" role="tabpanel" aria-labelledby="access-tabbtn-octrois">
          <p>
            Un octroi porte une <strong>période d'effet</strong> : il cesse de lui-même, sans que
            personne ait à y penser. C'est ce qui distingue un droit temporaire — un remplacement,
            un mandat — d'un droit qu'on oublie de retirer.
          </p>
          <div className="champ">
            <label htmlFor="access-octroi-sujettype">Type de sujet</label>
            <select id="access-octroi-sujettype" data-testid="access-octroi-sujettype"
              value={nouvelOctroi.sujetType}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, sujetType: e.target.value })}>
              <option value="utilisateur">Une personne</option>
              <option value="profil">Un profil</option>
            </select>
          </div>
          <div className="champ">
            <label htmlFor="access-octroi-sujet">Identifiant du sujet</label>
            <input id="access-octroi-sujet" data-testid="access-octroi-sujet" type="text"
              value={nouvelOctroi.sujetId}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, sujetId: e.target.value })} />
          </div>
          <div className="champ">
            <label htmlFor="access-octroi-objet">Objet</label>
            <select id="access-octroi-objet" data-testid="access-octroi-objet"
              value={nouvelOctroi.objetCode}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, objetCode: e.target.value })}>
              <option value="">— Choisir —</option>
              {objets.map((o) => <option key={o.code} value={o.code}>{o.code} — {o.libelle}</option>)}
            </select>
          </div>
          <div className="champ">
            <label htmlFor="access-octroi-permission">Permission</label>
            <select id="access-octroi-permission" data-testid="access-octroi-permission"
              value={nouvelOctroi.permission}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, permission: e.target.value })}>
              {permissions.map((p) => <option key={p} value={p}>{mot(PERMISSIONS, p)}</option>)}
            </select>
          </div>
          <div className="champ">
            <label htmlFor="access-octroi-du">À partir du</label>
            <input id="access-octroi-du" data-testid="access-octroi-du" type="date"
              value={nouvelOctroi.effectifDu}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, effectifDu: e.target.value })} />
          </div>
          <div className="champ">
            <label htmlFor="access-octroi-au">Jusqu'au (facultatif)</label>
            <input id="access-octroi-au" data-testid="access-octroi-au" type="date"
              value={nouvelOctroi.effectifAu}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, effectifAu: e.target.value })} />
          </div>
          <div className="champ">
            <label htmlFor="access-octroi-motif">Motif</label>
            <input id="access-octroi-motif" data-testid="access-octroi-motif" type="text"
              value={nouvelOctroi.motif}
              onChange={(e) => setNouvelOctroi({ ...nouvelOctroi, motif: e.target.value })} />
          </div>
          <button type="button" data-testid="access-octroyer"
            disabled={!nouvelOctroi.sujetId.trim() || !nouvelOctroi.objetCode}
            onClick={async () => {
              if (await envoyer("/api/habilitations/octrois", {
                ...nouvelOctroi,
                effectifDu: nouvelOctroi.effectifDu || undefined,
                effectifAu: nouvelOctroi.effectifAu || undefined,
                motif: nouvelOctroi.motif || undefined,
              }, `Droit octroyé à ${nouvelOctroi.sujetId}.`)) {
                setNouvelOctroi({ ...nouvelOctroi, sujetId: "", motif: "" });
                await chargerOctrois();
              }
            }}>
            Octroyer
          </button>

          <Tableau colonnes={colonnesOctrois} lignes={octrois}
            legende="Octrois en vigueur et à venir, avec leur période d'effet et le motif qui les justifie"
            testId="access-table-octrois"
            vide={<EtatVide titre="Aucun octroi">
              Un objet sans octroi n'est accessible à personne : l'absence de mention vaut refus.
            </EtatVide>}
            rendre={(o) => (
              <tr key={`${o.sujetType}-${o.sujetId}-${o.objetCode}-${o.effectifDu}`}
                data-testid={`access-octroi-${o.sujetId}-${o.objetCode}`}>
                <td>{o.sujetType === "profil" ? "Profil" : "Personne"} {o.sujetId}</td>
                <td>{o.objetCode}</td>
                <td><Badge ton={tonPermission(o.permission)}>{mot(PERMISSIONS, o.permission)}</Badge></td>
                <td>{o.effectifDu} → {o.effectifAu ?? "sans terme"}</td>
                <td>{o.motif ?? "—"}</td>
                <td>
                  <button type="button" data-testid={`access-revoquer-${o.sujetId}-${o.objetCode}`}
                    onClick={async () => {
                      const q = new URLSearchParams({
                        sujetType: o.sujetType, sujetId: o.sujetId,
                        objetCode: o.objetCode, effectifDu: o.effectifDu,
                      });
                      if (await envoyer(`/api/habilitations/octrois?${q}`, {},
                        `Droit de ${o.sujetId} sur ${o.objetCode} révoqué.`, "DELETE")) {
                        await chargerOctrois();
                      }
                    }}>
                    Révoquer le droit de {o.sujetId} sur {o.objetCode}
                  </button>
                </td>
              </tr>
            )} />
        </div>
      )}

      {onglet === "qui" && (
        <div id="access-tabpanel-qui" role="tabpanel" aria-labelledby="access-tabbtn-qui">
          <p>
            La <strong>date d'observation</strong> est explicite : un octroi expiré hier et un
            octroi qui commence demain donnent tous deux une liste vide aujourd'hui, pour des
            raisons opposées. Sans la date, on ne peut pas les distinguer.
          </p>
          <div className="champ">
            <label htmlFor="access-le">Date d'observation</label>
            <input id="access-le" data-testid="access-le" type="date" value={le}
              onChange={(e) => setLe(e.target.value)} />
          </div>

          <h2>Qui accède à cet objet ?</h2>
          <div className="champ">
            <label htmlFor="access-objet-interroge">Objet</label>
            <select id="access-objet-interroge" data-testid="access-objet-interroge"
              value={objetInterroge} onChange={(e) => setObjetInterroge(e.target.value)}>
              <option value="">— Choisir —</option>
              {objets.map((o) => <option key={o.code} value={o.code}>{o.code} — {o.libelle}</option>)}
            </select>
          </div>
          <button type="button" data-testid="access-droits" disabled={!objetInterroge}
            onClick={async () => {
              const d = await lire<DroitSurObjet[]>(
                `/api/habilitations/objets/${objetInterroge}/droits?le=${le}`);
              if (d) setDroits(d);
            }}>
            Lister les accès à {objetInterroge}
          </button>
          {droits && (
            <Tableau
              colonnes={[
                { id: "sujet", libelle: "Sujet", tri: (d: DroitSurObjet) => d.sujetId },
                { id: "permission", libelle: "Permission" },
                { id: "origine", libelle: "Origine" },
                { id: "explication", libelle: "Pourquoi" },
              ]}
              lignes={droits}
              legende={`Accès à l'objet choisi au ${le}, avec l'origine de chaque droit`}
              testId="access-table-droits"
              vide={<EtatVide titre="Personne n'y accède à cette date">
                Aucun octroi n'est en vigueur le {le} — ce qui n'est pas la même chose qu'aucun octroi.
              </EtatVide>}
              rendre={(d) => (
                <tr key={`${d.sujetType}-${d.sujetId}`} data-testid={`access-droit-${d.sujetId}`}>
                  <td>{d.sujetType === "profil" ? "Profil" : "Personne"} {d.sujetId}</td>
                  <td><Badge ton={tonPermission(d.permission)}>{mot(PERMISSIONS, d.permission)}</Badge></td>
                  <td>{d.origine}</td>
                  <td>{d.explication}</td>
                </tr>
              )} />
          )}

          <h2>Quels droits a cette personne ?</h2>
          <div className="champ">
            <label htmlFor="access-personne">Identifiant de la personne</label>
            <input id="access-personne" data-testid="access-personne" type="text"
              value={personneInterrogee} onChange={(e) => setPersonneInterrogee(e.target.value)} />
          </div>
          <button type="button" data-testid="access-fiche" disabled={!personneInterrogee.trim()}
            onClick={async () => {
              const f = await lire<Fiche>(
                `/api/habilitations/utilisateurs/${personneInterrogee}/fiche?le=${le}`);
              if (f) setFiche(f);
            }}>
            Fiche de {personneInterrogee}
          </button>
          {objetInterroge && personneInterrogee.trim() && (
            <button type="button" data-testid="access-permission-unique"
              onClick={async () => {
                const p = await lire<{ permission: string; explication: string }>(
                  `/api/habilitations/utilisateurs/${personneInterrogee}/permission?objetCode=${objetInterroge}&le=${le}`);
                if (p) setPermissionUnique(p);
              }}>
              Permission de {personneInterrogee} sur {objetInterroge}
            </button>
          )}
          {permissionUnique && (
            <Message variante="info" testId="access-permission-resultat">
              <Badge ton={tonPermission(permissionUnique.permission)}>
                {mot(PERMISSIONS, permissionUnique.permission)}
              </Badge>{" "}
              {permissionUnique.explication}
            </Message>
          )}

          {fiche && (
            <div data-testid="access-fiche-resultat">
              {fiche.motifs.map((m, i) => (
                <Message key={i} variante="info" testId={`access-fiche-motif-${i}`}>{m}</Message>
              ))}
              {/* Les échéances proches sont **nommées** : un droit qui cesse dans trois jours est
                  la seule information que personne ne pense à chercher avant qu'elle ne manque. */}
              {fiche.echeancesProches.length > 0 && (
                <Message variante="avertissement" testId="access-echeances">
                  {fiche.echeancesProches.length} droit(s) cessent bientôt :{" "}
                  {fiche.echeancesProches.map((l) => `${l.objetCode} (dans ${l.joursRestants} j)`).join(", ")}
                </Message>
              )}
              <Tableau
                colonnes={[
                  { id: "objetCode", libelle: "Objet", tri: (l: LigneFiche) => l.objetCode },
                  { id: "permission", libelle: "Permission" },
                  { id: "origine", libelle: "Origine" },
                  { id: "echeance", libelle: "Jusqu'au" },
                ]}
                lignes={fiche.lignes}
                legende={`Tous les droits de la personne au ${fiche.le}, avec leur origine et leur échéance`}
                testId="access-table-fiche"
                vide={<EtatVide titre="Aucun droit à cette date">
                  Cette personne n'a aucun accès en vigueur le {fiche.le}.
                </EtatVide>}
                rendre={(l) => (
                  <tr key={l.objetCode} data-testid={`access-fiche-${l.objetCode}`}>
                    <td>{l.objetCode} — {l.objetLibelle}</td>
                    <td><Badge ton={tonPermission(l.permission)}>{mot(PERMISSIONS, l.permission)}</Badge></td>
                    <td>{l.origine}</td>
                    <td>{l.effectifAu ?? "sans terme"}</td>
                  </tr>
                )} />
            </div>
          )}

          <h2>Profils de la personne</h2>
          <p>
            Un profil porte des droits que toutes ses personnes héritent. Un retrait
            <strong> nominatif</strong> prime sur le droit du profil : c'est ce qui permet de
            retirer un accès à une personne sans défaire le profil de tout le monde.
          </p>
          <div className="champ">
            <label htmlFor="access-profil">Profil à joindre</label>
            <input id="access-profil" data-testid="access-profil" type="text" value={profilAJoindre}
              onChange={(e) => setProfilAJoindre(e.target.value)} />
          </div>
          <button type="button" data-testid="access-joindre-profil"
            disabled={!personneInterrogee.trim() || !profilAJoindre.trim()}
            onClick={async () => {
              if (await envoyer(`/api/habilitations/utilisateurs/${personneInterrogee}/profils`,
                { profilId: profilAJoindre }, `${personneInterrogee} rattaché au profil ${profilAJoindre}.`)) {
                setProfilAJoindre("");
                const p = await lire<string[]>(`/api/habilitations/utilisateurs/${personneInterrogee}/profils`);
                if (p) setProfils(p);
              }
            }}>
            Rattacher au profil
          </button>
          <ul data-testid="access-profils">
            {profils.map((p) => (
              <li key={p}>
                {p}{" "}
                <button type="button" data-testid={`access-detacher-${p}`}
                  onClick={async () => {
                    if (await envoyer(
                      `/api/habilitations/utilisateurs/${personneInterrogee}/profils/${p}`, {},
                      `${personneInterrogee} détaché du profil ${p}.`, "DELETE")) {
                      const l = await lire<string[]>(`/api/habilitations/utilisateurs/${personneInterrogee}/profils`);
                      if (l) setProfils(l);
                    }
                  }}>
                  Détacher {personneInterrogee} du profil {p}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}

export default function AccessControl() {
  const { t } = useT();
  return <MifiTabPage title={t("nav.access")} icon="cadenas" storageKey="access.tenant" testId="access"
    render={(tid, msg) => (
      <Habilitations tenantId={tid} setError={msg.setError} setSuccess={msg.setSuccess} />
    )} />;
}
