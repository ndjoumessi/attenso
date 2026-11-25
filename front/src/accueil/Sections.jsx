import { useEffect, useState } from 'react';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
import { heureCourte } from '../../../src/main/resources/static/app/formats.js';
import { CHAMP, ETIQUETTE } from '../composants/formulaire.jsx';
import { entete } from './session.js';

export function Titre({ children }) {
  return <h2 className="mt-12 text-xl font-bold tracking-tight">{children}</h2>;
}

const secondaire = 'cible rounded-[var(--rayon)] border border-bordure-controle '
  + 'bg-surface px-4 font-semibold hover:bg-surface-douce disabled:opacity-50';

/**
 * Rendez-vous du jour (EF-905).
 *
 * <p>`vide` : ce qu'on affiche quand il n'y en a aucun. La section seule
 * détient la liste chargée, donc seule elle sait qu'elle est vide ; c'est la
 * vue qui l'enveloppe qui sait quoi dire alors.
 */
export function Agenda({ jeton, vide }) {
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
      ) : vide}
    </>
  );
}

/**
 * Carnets de santé (EF-906).
 *
 * <p>`vide` : ce qu'on affiche une fois la liste chargée et vide. Tant qu'elle
 * n'est pas chargée (`null`), ou si le chargement échoue, rien : un « aucun
 * carnet » affiché pendant le chargement dirait faux.
 */
export function Carnets({ jeton, surErreur, vide }) {
  const [animaux, setAnimaux] = useState(null);

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

      {animaux?.length === 0 && vide}
      <ul className="mt-4 space-y-2">
        {animaux?.map((animal) => (
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
