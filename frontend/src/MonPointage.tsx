/**
 * **Mon pointage** — la page du module `effectifs_presence` côté employé (AO 26-015-DO, lot R8 —
 * E-GP-1, E-GP-9, E-GP-11, E-GP-15).
 *
 * La personne pointe **son** quart du moment : seuls les gestes permis s'affichent (entrer, prendre
 * sa pause, en revenir, sortir). Sa position est lue **une seule fois, au geste**, et seulement si
 * elle le veut ; le serveur n'en garde que la distance au milieu. Un quart non pointé se déclare
 * après coup, avec une justification, et attend l'approbation.
 *
 * **Le profil de pointage décide de ce que l'écran offre** (E-GP-1, P-GP-1) : un profil à
 * déclaration après coup n'affiche aucun bouton « Entrer », et un profil en temps réel n'offre pas
 * le formulaire de déclaration. Afficher un geste que le serveur refusera, c'est faire porter à la
 * personne un refus qui n'est pas le sien.
 *
 * La **nature** d'une pause — pause, repas ou période de repos — se choisit (E-GP-2) : la
 * convention les plafonne séparément, et un seul « pause » empêcherait de la vérifier.
 *
 * Le **site** où l'on pointe se choisit aussi (E-GP-7) : une personne dépêchée ailleurs au cours
 * de son quart y pointe sa sortie, et son temps s'impute là où il a été fait. Juger sa preuve
 * contre le site *du quart* la déclarerait « hors milieu » pour avoir obéi.
 *
 * L'**activité** se déclare en *commençant* à travailler (E-GP-15) — entrée ou reprise après
 * pause — et jamais en s'arrêtant : elle qualifie le temps qui commence. Une garde de douze heures
 * dont deux en intervention ne se dimensionne pas comme douze heures d'intervention.
 */
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Badge, BadgeStatut, EnTeteEcran, EtatVide, Message, Tableau, type Colonne } from "./composants";
import { heure, useApiEffectifs } from "./effectifs/api";
import {
  duree, horaire, LIBELLE_MECANISME, LIBELLE_SEGMENT, LIBELLE_SENS, LIBELLE_STATUT, resultatEnMots, TON_STATUT,
  LIBELLE_NATURE,
  type MaPresence, type NatureNonPayee, type Pointage, type ProfilPointage, type Sens,
} from "./effectifs/presence/types";
import "./effectifs/effectifs.css";
import { liste, objet } from "./formeRecue";
import PageMaquette from "./composants/PageMaquette";
import { FormulaireModal } from "./Modal";

const COLONNES: ReadonlyArray<Colonne<Pointage>> = [
  { id: "quand", libelle: "Quand", tri: (p) => p.horodatage },
  { id: "sens", libelle: "Pointage", tri: (p) => p.sens },
  { id: "resultat", libelle: "Preuve", tri: (p) => p.resultat },
  { id: "statut", libelle: "Statut", tri: (p) => p.statut },
  { id: "note", libelle: "Détail" },
];

/** La position, lue une fois ; `null` si la personne refuse ou si l'appareil ne la donne pas. */
function positionUneFois(): Promise<{ latitude: number; longitude: number; precisionMetres: number } | null> {
  if (!("geolocation" in navigator)) return Promise.resolve(null);
  return new Promise((resoudre) => {
    navigator.geolocation.getCurrentPosition(
      (p) => resoudre({ latitude: p.coords.latitude, longitude: p.coords.longitude, precisionMetres: Math.round(p.coords.accuracy) }),
      () => resoudre(null),
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 0 },
    );
  });
}

/** Un instant local (date + heure saisies) en ISO UTC. */
const iso = (date: string, heureLocale: string) => new Date(`${date}T${heureLocale}`).toISOString();

/**
 * **La présence telle qu'elle est reçue, rendue sûre avant d'entrer dans l'arbre.**
 *
 * L'écran lisait `quart.parMilieu.length`, `quart.deplacements.length`… sans vérifier qu'ils
 * existent. Le Node les rend ; **le backend Java, non** (mesuré le 8 novembre 2026 : le
 * multi-site et les activités n'y sont pas portés). Sur la pile Java, l'écran s'effondrait donc
 * dès qu'un quart était en cours — « Cannot read properties of undefined (reading 'length') » —
 * et l'audit géométrique mesurait la page d'erreur à sa place, sans rien voir.
 *
 * Un champ absent devient une liste vide : l'écran montre alors le quart sans sa répartition par
 * site ni par activité, ce qui est **vrai** — le serveur ne l'a pas calculée —, au lieu de
 * s'effondrer. La règle est celle du dépôt : la forme se vérifie à l'enregistrement, une fois.
 */
export function presenceRecue(brut: unknown): MaPresence | null {
  const e = objet<MaPresence>(brut);
  if (!e) return null;
  const q = objet<NonNullable<MaPresence["quart"]>>(e.quart);
  return {
    ressource: e.ressource ?? null,
    pointages: liste(e.pointages),
    quart: q && {
      ...q,
      prochains: liste(q.prochains), segments: liste(q.segments),
      parMilieu: liste(q.parMilieu), parActivite: liste(q.parActivite), deplacements: liste(q.deplacements),
    },
  };
}

export default function MonPointage() {
  const { lire, envoyer, erreur, setErreur } = useApiEffectifs();
  const [etat, setEtat] = useState<MaPresence | null>(null);
  const [avecPosition, setAvecPosition] = useState(true);
  const [code, setCode] = useState("");
  const [site, setSite] = useState("");
  const [activite, setActivite] = useState("");
  const [activites, setActivites] = useState<Array<{ code: string; libelleFr: string }>>([]);
  const [milieux, setMilieux] = useState<Array<{ id: string; code: string; libelle: string }>>([]);
  const [enCours, setEnCours] = useState(false);
  const [info, setInfo] = useState("");
  const [saisie, setSaisie] = useState({ date: "", entree: "", sortie: "", pauseDebut: "", pauseFin: "", nature: "PAUSE", activite: "", justification: "" });
  const [profil, setProfil] = useState<ProfilPointage | null>(null);

  const charger = useCallback(async () => { setEtat(presenceRecue(await lire<MaPresence>("/api/effectifs/presence/moi"))); }, [lire]);
  useEffect(() => { void charger(); }, [charger]);
  useEffect(() => {
    void lire<{ profil: ProfilPointage }>("/api/effectifs/presence/mon-profil")
      .then((r) => setProfil(r?.profil ?? null));
  }, [lire]);
  useEffect(() => {
    void lire<Array<{ id: string; code: string; libelle: string }>>("/api/effectifs/milieux")
      .then((m) => setMilieux(m ?? []));
  }, [lire]);
  useEffect(() => {
    void lire<Array<{ code: string; libelleFr: string }>>("/api/effectifs/referentiels/activites_presence")
      .then((a) => setActivites(a ?? []));
  }, [lire]);

  const pointer = async (sens: Sens) => {
    setInfo(""); setEnCours(true);
    const position = avecPosition ? await positionUneFois() : null;
    // Le site n'est transmis que s'il a été choisi : à défaut, c'est celui du quart qui s'applique.
    // L'activité ne part **qu'avec un geste qui ouvre du travail** : le serveur la refuserait
    // ailleurs, et l'envoyer quand même ferait échouer une sortie pour un champ resté rempli.
    const ouvreDuTravail = sens === "ENTREE" || sens === "FIN_PAUSE";
    const p = await envoyer<Pointage>("/api/effectifs/presence/pointages", {
      sens, ...(position ?? {}), codeQr: code || undefined, milieuId: site || undefined,
      activiteCode: ouvreDuTravail && activite ? activite : undefined,
    });
    setEnCours(false);
    if (!p) return;
    setCode("");
    const r = resultatEnMots(p);
    setInfo(`${LIBELLE_SENS[p.sens]} enregistrée à ${horaire(p.horodatage)} — ${r.texte}${p.statut === "A_APPROUVER" ? " : votre gestionnaire l'approuvera." : "."}`);
    await charger();
  };

  const declarer = async (e: FormEvent, fermer: () => void) => {
    e.preventDefault();
    setInfo("");
    const { date, entree, sortie, pauseDebut, pauseFin, nature, activite: act, justification } = saisie;
    const avecPause = pauseDebut && pauseFin;
    // L'activité qualifie **les segments de travail** ; une pause n'en a pas.
    const activiteCode = act || undefined;
    const segments = avecPause
      ? [{ debut: iso(date, entree), fin: iso(date, pauseDebut), type: "TRAVAIL", activiteCode },
        { debut: iso(date, pauseDebut), fin: iso(date, pauseFin), type: "PAUSE", nature },
        { debut: iso(date, pauseFin), fin: iso(date, sortie), type: "TRAVAIL", activiteCode }]
      : [{ debut: iso(date, entree), fin: iso(date, sortie), type: "TRAVAIL", activiteCode }];
    const r = await envoyer<Pointage[]>("/api/effectifs/presence/saisies", { segments, justification });
    // Refusée, la déclaration reste ouverte avec sa saisie : le motif se corrige là où il a été écrit.
    if (!r) return;
    fermer();
    setInfo(`Quart du ${date} déclaré : ${r.length} pointages en attente d'approbation.`);
    setSaisie({ date: "", entree: "", sortie: "", pauseDebut: "", pauseFin: "", nature: "PAUSE", activite: "", justification: "" });
    await charger();
  };

  if (!etat) return erreur ? <Message variante="erreur" testId="mpo-erreur">{erreur}</Message> : null;
  const q = etat.quart;
  const champ = (cle: keyof typeof saisie) => ({ value: saisie[cle], onChange: (e: { target: { value: string } }) => setSaisie((s) => ({ ...s, [cle]: e.target.value })) });

  return (
    <PageMaquette module="Urgences-santé" contexte="Mon espace • pointage et présence">
      <EnTeteEcran titre="Mon pointage" description="Pointez votre quart au milieu où vous travaillez, ou déclarez un quart que vous n'avez pas pu pointer." testId="mpo-titre" />
      {erreur && <Message variante="erreur" testId="mpo-erreur">{erreur}</Message>}
      {info && <Message variante="succes" testId="mpo-info">{info}</Message>}
      {!etat.ressource && <EtatVide titre="Votre compte n'est relié à aucune ressource" compact testId="mpo-sans-ressource">Demandez à votre planificateur de relier votre compte à votre dossier.</EtatVide>}

      {etat.ressource && (
        <>
          <h2>Mon quart</h2>
          {!q
            ? <EtatVide titre="Aucun quart à pointer en ce moment" compact testId="mpo-sans-quart">Le pointage s'ouvre deux heures avant le début de votre quart publié.</EtatVide>
            : (
              <div className="eff-groupe" data-testid="mpo-quart">
                <p>
                  <strong>{q.milieu}</strong> — {q.date}, {heure(q.debut)}–{heure(q.fin)}
                  {q.presencePrealableMinutes > 0 && <> · présence préalable rémunérée : {q.presencePrealableMinutes} min</>}
                </p>
                {q.segments.length > 0 && (
                  <ul aria-label="Mon temps sur ce quart" data-testid="mpo-segments">
                    {q.segments.map((s) => <li key={`${s.type}-${s.debut}`}>{LIBELLE_SEGMENT[s.type]} : {horaire(s.debut)}{s.fin ? ` – ${horaire(s.fin)}` : " – en cours"} ({duree(s.minutes)})</li>)}
                  </ul>
                )}
                <p data-testid="mpo-payees">Temps payé à ce jour : {duree(q.minutesPayees)}</p>
                {/* E-GP-7 : où ce temps a été fait. Un seul site ne se détaille pas. */}
                {q.parMilieu.length > 1 && (
                  <ul aria-label="Mon temps par site" data-testid="mpo-par-milieu">
                    {q.parMilieu.map((x) => (
                      <li key={x.milieuId ?? "sans"}>{x.milieu} : {duree(x.minutes)}</li>
                    ))}
                  </ul>
                )}
                {/* E-GP-15 : ce que la personne a fait, quand plus d'une activité est déclarée. */}
                {q.parActivite.filter((x) => x.activiteCode !== null).length > 0 && (
                  <ul aria-label="Mon temps par activité" data-testid="mpo-par-activite">
                    {q.parActivite.map((x) => (
                      <li key={x.activiteCode ?? "sans"}>
                        {x.activiteCode
                          ? (activites.find((a) => a.code === x.activiteCode)?.libelleFr ?? x.activiteCode)
                          : "activité non précisée"} : {duree(x.minutes)}
                      </li>
                    ))}
                  </ul>
                )}
                {q.deplacements.length > 0 && (
                  <ul aria-label="Mes déplacements de ce quart" data-testid="mpo-deplacements">
                    {q.deplacements.map((d) => (
                      <li key={d.quand}>{horaire(d.quand)} : {d.deLibelle} → {d.versLibelle}</li>
                    ))}
                  </ul>
                )}
                {profil?.mode === "A_POSTERIORI"
                  ? (
                    <Message variante="info" testId="mpo-profil-a-posteriori">
                      Votre profil « {profil.libelle} » est à <strong>déclaration après coup</strong> :
                      déclarez ce quart ci-dessous, il n'y a pas de pointage en direct à faire.
                    </Message>
                  )
                  : q.prochains.length === 0
                  ? <Message variante="info" testId="mpo-termine">Quart terminé : rien d'autre à pointer.</Message>
                  : (
                    <>
                      <p className="eff-case">
                        <input id="mpo-position" type="checkbox" data-testid="mpo-position" checked={avecPosition} onChange={(e) => setAvecPosition(e.target.checked)} />
                        <label htmlFor="mpo-position">Joindre ma position, lue une seule fois au moment du pointage (seule la distance au milieu est conservée)</label>
                      </p>
                      <p>
                        <label htmlFor="mpo-site">Où êtes-vous ?</label>
                        <select id="mpo-site" data-testid="mpo-site" value={site} onChange={(e) => setSite(e.target.value)}>
                          <option value="">{q.milieu} (le site de mon quart)</option>
                          {milieux.filter((m) => m.id !== q.milieuId)
                            .map((m) => <option key={m.id} value={m.id}>{m.code} — {m.libelle}</option>)}
                        </select>
                        <small className="muted">
                          Si vous avez été dépêché ailleurs, choisissez ce site : votre temps s'y
                          imputera, et votre preuve y sera vérifiée.
                        </small>
                      </p>
                      {/* E-GP-15 : n'apparaît que si un geste d'ouverture de travail est possible. */}
                      {activites.length > 0 && q.prochains.some((x) => x === "ENTREE" || x === "FIN_PAUSE") && (
                        <p>
                          <label htmlFor="mpo-activite">Que commencez-vous à faire ?</label>
                          <select id="mpo-activite" data-testid="mpo-activite" value={activite}
                            onChange={(e) => setActivite(e.target.value)}>
                            <option value="">Non précisé</option>
                            {activites.map((a) => <option key={a.code} value={a.code}>{a.libelleFr}</option>)}
                          </select>
                          <small className="muted">
                            L'activité qualifie le temps qui commence ; elle se redemande après
                            chaque pause.
                          </small>
                        </p>
                      )}
                      <p>
                        <label htmlFor="mpo-code">Code affiché au milieu (si demandé)</label>
                        <input id="mpo-code" data-testid="mpo-code" autoComplete="off" value={code} onChange={(e) => setCode(e.target.value)} />
                      </p>
                      <p className="eff-actions" role="group" aria-label="Pointer">
                        {q.prochains.map((s) => (
                          <button key={s} type="button" data-testid={`mpo-${s}`} disabled={enCours} onClick={() => void pointer(s)}>{LIBELLE_SENS[s]}</button>
                        ))}
                      </p>
                      {enCours && <p role="status" data-testid="mpo-en-cours">Pointage en cours…</p>}
                    </>
                  )}
              </div>
            )}

          <h2>Mes pointages récents</h2>
          <Tableau colonnes={COLONNES} lignes={etat.pointages} legende="Mes pointages des deux dernières semaines" testId="mpo-table"
            vide={<EtatVide titre="Aucun pointage récent" compact testId="mpo-vide">Vos pointages des deux dernières semaines s'affichent ici.</EtatVide>}
            rendre={(p) => {
              const r = resultatEnMots(p);
              return (
                <tr key={p.id} data-testid={`mpo-p-${p.id}`}>
                  <td>{horaire(p.horodatage)}</td>
                  <td>{LIBELLE_SENS[p.sens]}{p.origine === "SAISIE" ? " (déclaré)" : ""}</td>
                  <td><Badge ton={r.ton}>{r.texte}</Badge></td>
                  <td><BadgeStatut statut={TON_STATUT[p.statut]}>{LIBELLE_STATUT[p.statut]}</BadgeStatut></td>
                  <td>
                    {[...p.preuves.filter((v) => v.fournie).map((v) => `${LIBELLE_MECANISME[v.mecanisme] ?? v.mecanisme}${v.distanceMetres !== undefined ? ` (${v.distanceMetres} m)` : ""}`),
                      ...p.motifsRegles.map((m) => m.message), ...(p.motifDecision ? [`Décision : ${p.motifDecision}`] : [])].join(" ; ") || "—"}
                  </td>
                </tr>
              );
            }} />

          {profil && profil.mode === "TEMPS_REEL" && (
            <Message variante="info" testId="mpo-profil-temps-reel">
              Votre profil « {profil.libelle} » est à <strong>pointage en temps réel</strong> :
              un quart non pointé se fait corriger par votre gestionnaire, il ne se déclare pas ici.
            </Message>
          )}

          {(!profil || profil.mode !== "TEMPS_REEL") && (
          /* Une déclaration **ajoute** des pointages : elle vit dans un dialogue. Repliée dans un
             `<details>`, elle se dépliait au milieu de la liste des pointages qu'elle venait compléter. */
          <div className="eff-actions">
          <FormulaireModal libelle="Déclarer un quart non pointé" testId="mpo-declarer" size="md" className="secondary">
            {(fermer) => (
            <form onSubmit={(e) => void declarer(e, fermer)} aria-describedby="mpo-declarer-aide">
              <p id="mpo-declarer-aide">
                Heures locales. La déclaration attend l'approbation de votre gestionnaire.
                {profil?.fenetreAPosterioriJours != null && (
                  <> Votre profil n'admet que les <strong>{profil.fenetreAPosterioriJours} derniers
                  jours</strong> : au-delà, faites corriger par votre gestionnaire.</>
                )}
              </p>
              <p><label htmlFor="mpo-s-date">Date</label><input id="mpo-s-date" type="date" required data-testid="mpo-s-date" {...champ("date")} /></p>
              <p><label htmlFor="mpo-s-entree">Entrée</label><input id="mpo-s-entree" type="time" required data-testid="mpo-s-entree" {...champ("entree")} /></p>
              <p><label htmlFor="mpo-s-pause-debut">Début de pause (facultatif)</label><input id="mpo-s-pause-debut" type="time" data-testid="mpo-s-pause-debut" {...champ("pauseDebut")} /></p>
              <p><label htmlFor="mpo-s-pause-fin">Fin de pause (facultatif)</label><input id="mpo-s-pause-fin" type="time" data-testid="mpo-s-pause-fin" {...champ("pauseFin")} /></p>
              {saisie.pauseDebut && saisie.pauseFin && (
                <p><label htmlFor="mpo-s-nature">Nature de cette période non payée</label>
                  <select id="mpo-s-nature" data-testid="mpo-s-nature" {...champ("nature")}>
                    {(profil?.naturesNonPayees ?? (["PAUSE", "REPAS", "REPOS"] as NatureNonPayee[]))
                      .map((n) => <option key={n} value={n}>{LIBELLE_NATURE[n]}</option>)}
                  </select></p>
              )}
              <p><label htmlFor="mpo-s-sortie">Sortie</label><input id="mpo-s-sortie" type="time" required data-testid="mpo-s-sortie" {...champ("sortie")} /></p>
              {activites.length > 0 && (
                <p><label htmlFor="mpo-s-activite">Activité de ce temps de travail</label>
                  <select id="mpo-s-activite" data-testid="mpo-s-activite" {...champ("activite")}>
                    <option value="">Non précisée</option>
                    {activites.map((a) => <option key={a.code} value={a.code}>{a.libelleFr}</option>)}
                  </select></p>
              )}
              <p><label htmlFor="mpo-s-justification">Pourquoi ce quart n'a-t-il pas été pointé ?</label>
                <textarea id="mpo-s-justification" required minLength={5} maxLength={500} rows={2} data-testid="mpo-s-justification" {...champ("justification")} /></p>
              {erreur && <Message variante="erreur" testId="mpo-s-erreur">{erreur}</Message>}
              <p><button type="submit" data-testid="mpo-s-envoyer" onClick={() => setErreur("")}>Déclarer le quart</button></p>
            </form>
            )}
          </FormulaireModal>
          </div>
          )}
        </>
      )}
    </PageMaquette>
  );
}
