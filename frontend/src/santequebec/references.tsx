/**
 * **Choisir un membre du groupe dans une liste** plutôt que de saisir son identifiant.
 *
 * Une liste déroulante rend toutes les valeurs à l'écran : un groupe compte quelques dizaines de
 * membres, et les voir tous renseigne (« il n'est pas dans ce groupe ») là où une recherche vide
 * laisse croire à une panne.
 */
import { useEffect, useState } from "react";

export interface EntreeNomenclature {
  code: string;
  libelle: string;
  exigeCohorte?: boolean;
  colonnes?: EntreeNomenclature[];
}

/**
 * Une liste déroulante garnie par le serveur.
 *
 * Le `<label>` porte un `htmlFor` **littéral** : le scanner d'accessibilité lit le dépôt sans le
 * rendre, et un identifiant passé en propriété lui serait invisible — il signalerait un champ sans
 * libellé là où il y en a un, ou, pire, se tairait sur un champ qui n'en a pas.
 */
export function ChampNomenclature({
  id, libelle, valeurs, valeur, onChange, testId, aide, requis,
}: {
  id: string;
  libelle: string;
  valeurs: EntreeNomenclature[];
  valeur: string;
  onChange: (v: string) => void;
  testId: string;
  aide?: string;
  requis?: boolean;
}) {
  return (
    <div className="sq-champ">
      <label htmlFor={id}>
        {libelle}
        {requis && <><span className="req-star" aria-hidden="true">*</span><span className="sr-only"> (obligatoire)</span></>}
      </label>
      <select id={id} data-testid={testId} value={valeur} required={requis}
              aria-describedby={aide ? `${id}-aide` : undefined}
              disabled={valeurs.length === 0}
              onChange={(e) => onChange(e.target.value)}>
        {!requis && <option value="">— Choisir —</option>}
        {valeurs.map((v) => <option key={v.code} value={v.code}>{v.libelle}</option>)}
      </select>
      {aide && <p className="sq-champ-aide" id={`${id}-aide`}>{aide}</p>}
    </div>
  );
}

/**
 * **Un membre du groupe**, choisi dans une liste plutôt que désigné par son identifiant.
 *
 * Les écrans demandaient un `personId` au clavier. Personne ne connaît ces identifiants : on les
 * cherchait dans un autre écran, on les recopiait, et une faute de frappe s'enregistrait sans un
 * mot — la ligne se rattachait alors à personne. Une liste est ici préférable à une recherche :
 * un groupe compte quelques dizaines de membres, et les voir tous renseigne (« il n'est pas dans
 * ce groupe ») là où une recherche vide laisse croire à une panne.
 */
export function ChampMembre({
  ctx, libelle, valeur, onChange, testId, requis, exclure,
}: {
  ctx: { perimetre: { groupeId: string | null }; appeler: (c: string, i?: RequestInit) => Promise<any | null> };
  libelle: string;
  valeur: string;
  onChange: (v: string) => void;
  testId: string;
  requis?: boolean;
  /** Un identifiant à retirer de la liste — l'autre partie d'un binôme, par exemple. */
  exclure?: string;
}) {
  const [membres, setMembres] = useState<Array<{ personId: string; nom?: string }>>([]);
  const groupeId = ctx.perimetre.groupeId;

  useEffect(() => {
    if (!groupeId) { setMembres([]); return; }
    void (async () => {
      const r = await ctx.appeler(`/api/communication/groupes/${encodeURIComponent(groupeId)}/membres`);
      setMembres(Array.isArray(r) ? r : []);
    })();
  }, [groupeId]);   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <ChampNomenclature
      id={testId} libelle={libelle} testId={testId} requis={requis}
      valeur={valeur} onChange={onChange}
      // Le **nom**, résolu par le serveur : personne ne connaît un identifiant d'employé, et
      // c'est en les recopiant d'un écran à l'autre qu'on rattache une ligne à la mauvaise
      // personne. L'identifiant reste le repli pour qui n'a pas de dossier — un externe de garde.
      valeurs={membres.filter((m) => m.personId !== exclure)
        .map((m) => ({ code: m.personId, libelle: m.nom || m.personId }))}
      aide={membres.length === 0 ? "Aucun membre dans ce groupe : rattachez-les d'abord." : undefined}
    />
  );
}
