/**
 * **Profils de pointage** — configuration, côté encadrement (AO 26-015-DO : E-GP-1, E-GP-2, P-GP-1).
 *
 * Trois décisions d'interface :
 *
 * - **La portée est choisie avant son code**, et le code ne s'affiche que si la portée en exige un.
 *   Un champ « code de portée » toujours visible se remplit pour une portée « organisme », où il ne
 *   veut rien dire.
 * - **Ce que le profil interdit est écrit en clair**, à côté du mode : « refuse la déclaration
 *   après coup » se lit, « TEMPS_REEL » se devine.
 * - **L'absence de fenêtre est un état affiché**, pas un champ vide. « Aucune limite » et « je n'ai
 *   pas rempli » ne se ressemblent pas, et c'est la première qui est le défaut.
 */
import { useCallback, useEffect, useState } from "react";
import { liste, objet } from "../../formeRecue";
import { Badge, EtatVide, Message, Tableau, type Colonne } from "../../composants";
import { useApiEffectifs } from "../api";
import { LIBELLE_NATURE, type ModeSaisie, type NatureNonPayee, type ProfilPointage } from "./types";

const PORTEES = ["ORGANISME", "UNITE", "CONVENTION", "STATUT_EMPLOI", "CORPS_EMPLOI", "TITRE_EMPLOI"] as const;
type Portee = (typeof PORTEES)[number];

const LIBELLE_PORTEE: Record<Portee, string> = {
  ORGANISME: "Tout l'organisme", UNITE: "Une unité (et ses descendants)",
  CONVENTION: "Une convention collective", STATUT_EMPLOI: "Un statut d'emploi",
  CORPS_EMPLOI: "Un corps d'emploi", TITRE_EMPLOI: "Un titre d'emploi",
};

/** Ce que chaque mode **interdit** : c'est cela que l'écran doit dire, pas le code du mode. */
const EFFET_MODE: Record<ModeSaisie, string> = {
  TEMPS_REEL: "refuse la déclaration après coup",
  A_POSTERIORI: "refuse le pointage en temps réel",
  MIXTE: "admet les deux",
};
const LIBELLE_MODE: Record<ModeSaisie, string> = {
  TEMPS_REEL: "Temps réel", A_POSTERIORI: "Après coup", MIXTE: "Les deux",
};

interface Referentiels { modes: ModeSaisie[]; naturesNonPayees: NatureNonPayee[] }

const VIDE = {
  code: "", libelle: "", mode: "MIXTE" as ModeSaisie, porteeType: "ORGANISME" as Portee,
  porteeCode: "", naturesNonPayees: ["PAUSE", "REPAS", "REPOS"] as NatureNonPayee[],
  pausesManuelles: false, presencePrealableMinutes: "15", fenetreAPosterioriJours: "",
};

export default function ProfilsPointage() {
  const { lire, envoyer, supprimer, erreur, setErreur } = useApiEffectifs();
  const [profils, setProfils] = useState<ProfilPointage[]>([]);
  const [ref, setRef] = useState<Referentiels | null>(null);
  const [form, setForm] = useState({ ...VIDE });
  const [info, setInfo] = useState("");

  const charger = useCallback(async () => {
    setProfils(liste<ProfilPointage>(await lire<ProfilPointage[]>("/api/effectifs/presence/profils")));
  }, [lire]);

  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => {
    void lire<Referentiels>("/api/effectifs/presence/referentiels-profils")
      .then((r) => setRef(objet<Referentiels>(r)));
  }, [lire]);

  const poser = async () => {
    setInfo(""); setErreur("");
    const r = await envoyer<ProfilPointage>("/api/effectifs/presence/profils", {
      code: form.code, libelle: form.libelle, mode: form.mode,
      porteeType: form.porteeType,
      porteeCode: form.porteeType === "ORGANISME" ? null : form.porteeCode,
      naturesNonPayees: form.naturesNonPayees,
      pausesManuelles: form.pausesManuelles,
      presencePrealableMinutes: Number(form.presencePrealableMinutes),
      // Un champ vide vaut **aucune limite**, et non zéro : zéro refuserait toute déclaration.
      fenetreAPosterioriJours: form.fenetreAPosterioriJours === ""
        ? null : Number(form.fenetreAPosterioriJours),
    }, "PUT");
    if (!r) return;
    setInfo(`Profil « ${r.libelle} » enregistré : il ${EFFET_MODE[r.mode]}.`);
    setForm({ ...VIDE });
    await charger();
  };

  const retirer = async (p: ProfilPointage) => {
    setInfo(""); setErreur("");
    const r = await supprimer(`/api/effectifs/presence/profils/${encodeURIComponent(p.code)}`);
    if (!r) return;
    // Retirer un profil **rend la capacité** : le défaut admet les deux modes.
    setInfo(`Profil « ${p.libelle} » retiré : les personnes visées reviennent au profil par défaut, qui admet les deux modes.`);
    await charger();
  };

  const basculerNature = (n: NatureNonPayee) => setForm((f) => ({
    ...f,
    naturesNonPayees: f.naturesNonPayees.includes(n)
      ? f.naturesNonPayees.filter((x) => x !== n) : [...f.naturesNonPayees, n],
  }));

  const colonnes: ReadonlyArray<Colonne<ProfilPointage>> = [
    { id: "code", libelle: "Profil", tri: (p) => p.code },
    { id: "mode", libelle: "Mode de saisie", tri: (p) => p.mode },
    { id: "portee", libelle: "S'applique à" },
    { id: "natures", libelle: "Temps non payé admis" },
    { id: "fenetre", libelle: "Fenêtre de déclaration", numerique: true },
    { id: "actions", libelle: "Actions", actions: true },
  ];

  return (
    <section data-testid="pfl-panneau">
      <h3>Profils de pointage</h3>
      <p>
        Un profil dit <strong>comment</strong> une population déclare son temps, et ce qu'elle ne
        peut pas faire. Le profil <strong>le plus particulier</strong> s'applique : titre d'emploi
        avant corps d'emploi, avant statut, avant convention, avant unité, avant l'organisme.
        Sans profil, les deux modes de saisie sont admis.
      </p>

      {erreur && <Message variante="erreur" testId="pfl-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="pfl-info">{info}</Message>}

      <Tableau
        legende="Profils de pointage : code, mode de saisie, population visée, natures de temps non payé admises et fenêtre de déclaration"
        colonnes={colonnes} lignes={profils}
        testId="pfl-table" tbodyTestId="pfl-corps"
        vide="Aucun profil : les deux modes de saisie sont admis pour tout le monde."
        rendre={(p) => (
          <tr key={p.code} data-testid={`pfl-${p.code}`}>
            <td>{p.libelle}<br /><small className="muted"><code>{p.code}</code></small></td>
            <td>
              <Badge ton={p.mode === "MIXTE" ? "info" : "avertissement"}>{LIBELLE_MODE[p.mode]}</Badge>
              <br />
              {/* Le mot dit ce que le mode interdit : le code du mode se devine, pas son effet. */}
              <small>{EFFET_MODE[p.mode]}</small>
            </td>
            <td>
              {LIBELLE_PORTEE[p.porteeType as Portee] ?? p.porteeType}
              {p.porteeCode && <><br /><small className="muted">{p.porteeCode}</small></>}
            </td>
            <td>{p.naturesNonPayees.map((n) => LIBELLE_NATURE[n]).join(", ")}</td>
            <td className="numerique">
              {p.fenetreAPosterioriJours == null
                ? <span className="muted">aucune limite</span>
                : `${p.fenetreAPosterioriJours} j`}
            </td>
            <td>
              <button type="button" className="danger" data-testid="pfl-retirer"
                aria-label={`Retirer le profil ${p.libelle}`}
                onClick={() => void retirer(p)}>Retirer</button>
            </td>
          </tr>
        )} />

      {profils.length === 0 && (
        <EtatVide titre="Aucun profil configuré" compact testId="pfl-vide">
          Le profil par défaut admet le pointage en temps réel comme la déclaration après coup,
          sans limite de date. Posez un profil pour restreindre l'un ou l'autre.
        </EtatVide>
      )}

      <fieldset>
        <legend>Poser un profil</legend>
        <p>
          <label htmlFor="pfl-code">Code <span className="req-star" aria-hidden="true">*</span></label>
          <input id="pfl-code" data-testid="pfl-code" required value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="PARA-TR" />
        </p>
        <p>
          <label htmlFor="pfl-libelle">Libellé</label>
          <input id="pfl-libelle" data-testid="pfl-libelle" value={form.libelle}
            onChange={(e) => setForm({ ...form, libelle: e.target.value })}
            placeholder="Paramédics — temps réel" />
        </p>
        <p>
          <label htmlFor="pfl-mode">Mode de saisie</label>
          <select id="pfl-mode" data-testid="pfl-mode" value={form.mode}
            onChange={(e) => setForm({ ...form, mode: e.target.value as ModeSaisie })}>
            {(ref?.modes ?? []).map((m) => (
              <option key={m} value={m}>{LIBELLE_MODE[m]} — {EFFET_MODE[m]}</option>
            ))}
          </select>
        </p>
        <p>
          <label htmlFor="pfl-portee">S'applique à</label>
          <select id="pfl-portee" data-testid="pfl-portee" value={form.porteeType}
            onChange={(e) => setForm({ ...form, porteeType: e.target.value as Portee, porteeCode: "" })}>
            {PORTEES.map((x) => <option key={x} value={x}>{LIBELLE_PORTEE[x]}</option>)}
          </select>
        </p>
        {/* Le code de portée ne paraît que si la portée en exige un. */}
        {form.porteeType !== "ORGANISME" && (
          <p>
            <label htmlFor="pfl-portee-code">
              Code de {LIBELLE_PORTEE[form.porteeType].toLowerCase()}{" "}
              <span className="req-star" aria-hidden="true">*</span>
            </label>
            <input id="pfl-portee-code" data-testid="pfl-portee-code" required value={form.porteeCode}
              onChange={(e) => setForm({ ...form, porteeCode: e.target.value })} placeholder="PARA" />
          </p>
        )}
        <fieldset>
          <legend>Temps non payé admis</legend>
          {(ref?.naturesNonPayees ?? []).map((n) => (
            <p className="eff-case" key={n}>
              <input id={`pfl-nature-${n}`} type="checkbox" data-testid={`pfl-nature-${n}`}
                checked={form.naturesNonPayees.includes(n)} onChange={() => basculerNature(n)} />
              <label htmlFor={`pfl-nature-${n}`}>{LIBELLE_NATURE[n]}</label>
            </p>
          ))}
        </fieldset>
        <p className="eff-case">
          <input id="pfl-pauses" type="checkbox" data-testid="pfl-pauses"
            checked={form.pausesManuelles}
            onChange={(e) => setForm({ ...form, pausesManuelles: e.target.checked })} />
          <label htmlFor="pfl-pauses">
            La personne déclare ses pauses elle-même (sinon elles se déduisent du pointage)
          </label>
        </p>
        <p>
          <label htmlFor="pfl-prealable">Présence préalable rémunérée (minutes)</label>
          <input id="pfl-prealable" type="number" min="0" max="240" data-testid="pfl-prealable"
            value={form.presencePrealableMinutes}
            onChange={(e) => setForm({ ...form, presencePrealableMinutes: e.target.value })} />
        </p>
        <p>
          <label htmlFor="pfl-fenetre">Fenêtre de déclaration après coup (jours)</label>
          <input id="pfl-fenetre" type="number" min="0" max="90" data-testid="pfl-fenetre"
            value={form.fenetreAPosterioriJours}
            onChange={(e) => setForm({ ...form, fenetreAPosterioriJours: e.target.value })}
            placeholder="vide = aucune limite" />
          <small className="muted">
            Laissée vide, aucune limite. Quatorze jours — une période de paie — est le réglage
            usuel : au-delà, une déclaration arrive après la paie.
          </small>
        </p>
        <p>
          <button type="button" data-testid="pfl-poser" onClick={() => void poser()}
            disabled={!form.code.trim() || form.naturesNonPayees.length === 0
              || (form.porteeType !== "ORGANISME" && !form.porteeCode.trim())}>
            Enregistrer le profil
          </button>
        </p>
      </fieldset>
    </section>
  );
}
