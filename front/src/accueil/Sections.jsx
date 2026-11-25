import { useEffect, useState } from 'react';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
import { heureCourte } from '../../../src/main/resources/static/app/formats.js';
import { AIDE, CHAMP, ETIQUETTE } from '../composants/formulaire.jsx';
import { CLE_SLUG, entete } from './session.js';

export function Titre({ children }) {
  return <h2 className="mt-12 text-xl font-bold tracking-tight">{children}</h2>;
}

const secondaire = 'cible rounded-[var(--rayon)] border border-bordure-controle '
  + 'bg-surface px-4 font-semibold hover:bg-surface-douce disabled:opacity-50';

/** Rendez-vous du jour (EF-905). */
export function Agenda({ jeton }) {
  const [rdvs, setRdvs] = useState(null);

  useEffect(() => {
    const relire = () => appel('/api/agenda', { headers: entete(jeton) })
      .then(setRdvs)
      // L'agenda est un à-côté : il ne doit pas masquer la file.
      .catch(() => {});
    relire();
    const t = setInterval(relire, 120000);
    return () => clearInterval(t);
  }, [jeton]);

  const lien = typeof location === 'undefined' ? ''
    : `${location.origin}/rdv/${localStorage.getItem(CLE_SLUG)}`;

  return (
    <>
      <Titre>Rendez-vous du jour</Titre>
      {rdvs?.length ? (
        <ul className="mt-4 space-y-2">
          {rdvs.map((rdv) => (
            <li key={rdv.reference ?? `${rdv.heure}-${rdv.motif}`}
                className={`flex items-center gap-4 rounded-[var(--rayon)] border
                            border-bordure-douce bg-surface p-3
                            ${rdv.statut === 'ANNULE' ? 'opacity-60' : ''}`}>
              <div className="shrink-0 text-center">
                <div className="text-xl font-bold tabular-nums">
                  {heureCourte(rdv.heure)}
                </div>
                <div className="text-xs text-sur-fond-doux">
                  {rdv.statut === 'ANNULE' ? 'annulé' : 'prévu'}
                </div>
              </div>
              <div className="text-sur-fond-doux">{rdv.motif || 'sans motif'}</div>
            </li>
          ))}
        </ul>
      ) : (
        <p className={AIDE}>
          Aucun rendez-vous aujourd'hui. Les clients réservent sur{' '}
          <code className="break-all font-mono">{lien}</code>.
        </p>
      )}
    </>
  );
}

/** Carnets de santé (EF-906). */
export function Carnets({ jeton, surErreur }) {
  const [animaux, setAnimaux] = useState([]);

  const relire = () => appel('/api/animaux', { headers: entete(jeton) })
    .then(setAnimaux).catch(() => {});

  useEffect(() => { relire(); }, [jeton]);

  async function ajouter(evenement) {
    evenement.preventDefault();
    const f = evenement.currentTarget;
    try {
      await appel('/api/animaux', {
        method: 'POST', headers: entete(jeton),
        body: JSON.stringify({
          nom: f.nom.value.trim(),
          espece: f.espece.value.trim(),
          telephone: f.telephone.value.trim() || null,
          avecCarnet: f.avecCarnet.checked,
        }),
      });
      f.reset();
      f.avecCarnet.checked = true;
      relire();
    } catch (echec) {
      surErreur(echec.reseau
        ? "Serveur injoignable : l'animal n'a pas été enregistré." : echec.message);
    }
  }

  async function revoquer(id) {
    try {
      await appel(`/api/animaux/${id}/carnet/revoquer`, {
        method: 'POST', headers: entete(jeton),
        body: JSON.stringify({ reemettre: true }),
      });
      relire();
    } catch (echec) {
      surErreur(echec.reseau
        ? "Serveur injoignable : le QR n'a PAS été révoqué." : echec.message);
    }
  }

  return (
    <>
      <Titre>Carnets de santé</Titre>
      <form onSubmit={ajouter} className="mt-4 space-y-4">
        <div>
          <label className={ETIQUETTE} htmlFor="animal-nom">Nom de l'animal</label>
          <input id="animal-nom" name="nom" required maxLength={80}
                 autoComplete="off" className={CHAMP} />
        </div>
        <div>
          <label className={ETIQUETTE} htmlFor="animal-espece">Espèce</label>
          <input id="animal-espece" name="espece" required maxLength={40}
                 autoComplete="off" placeholder="Chat, chien…" className={CHAMP} />
        </div>
        <div>
          <label className={ETIQUETTE} htmlFor="animal-tel">
            Mobile du propriétaire (facultatif)
          </label>
          <input id="animal-tel" name="telephone" type="tel" inputMode="tel"
                 autoComplete="off" className={CHAMP} />
        </div>
        <label className="flex cible-min cursor-pointer items-center gap-3">
          <input type="checkbox" name="avecCarnet" defaultChecked
                 className="size-5 accent-[var(--accent)]" />
          Émettre un QR de carnet
        </label>
        <button type="submit" className={`w-full ${secondaire}`}>Ajouter l'animal</button>
      </form>

      <ul className="mt-4 space-y-2">
        {animaux.map((animal) => (
          <li key={animal.id}
              className="rounded-[var(--rayon)] border border-bordure-douce
                         bg-surface p-3">
            <strong>{animal.nom} — {animal.espece}</strong>
            {animal.jetonCarnet && (
              <div className="mt-2">
                <code className="block break-all font-mono text-sm text-sur-fond-doux">
                  {location.origin}/carnet/{animal.jetonCarnet}
                </code>
                <button type="button" onClick={() => revoquer(animal.id)}
                        title="Le QR actuel cessera de fonctionner et un nouveau sera émis."
                        className={`mt-2 ${secondaire}`}>
                  Révoquer le QR
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </>
  );
}
