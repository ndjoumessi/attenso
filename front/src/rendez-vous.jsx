import { useEffect, useState } from 'react';
import { attenue } from './composants/formulaire.jsx';
import { BandeLegale } from './composants/chrome.jsx';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import {
  heureCourte, jourLong,
} from '../../src/main/resources/static/app/formats.js';

export function RendezVous() {
  const [rdv, setRdv] = useState(null);
  const [erreur, setErreur] = useState(null);
  const [envoi, setEnvoi] = useState(false);

  const reference = () => location.pathname.split('/').filter(Boolean)[1];

  useEffect(() => {
    appel(`/api/rendez-vous/${encodeURIComponent(reference())}`)
      .then(setRdv)
      .catch((echec) => setErreur(echec.reseau
        ? 'Serveur injoignable : réessayez dans un instant.' : echec.message));
  }, []);

  async function annuler() {
    setEnvoi(true);
    setErreur(null);
    try {
      setRdv(await appel(
        `/api/rendez-vous/${encodeURIComponent(reference())}/annuler`,
        { method: 'POST' }));
    } catch (echec) {
      setErreur(echec.reseau
        ? "Serveur injoignable : le rendez-vous n'est PAS annulé." : echec.message);
    } finally {
      setEnvoi(false);
    }
  }

  const annule = rdv?.statut === 'ANNULE';

  return (
    <main className="mx-auto w-full max-w-md px-5 py-10">
      <p className="min-h-5 text-sm font-semibold uppercase tracking-wide
                    text-sur-fond-doux">{rdv ? rdv.clinique : ''}</p>

      <div aria-live="polite" aria-atomic="true"
           className="mt-4 rounded-[var(--rayon-lg)] border border-bordure-douce
                      bg-surface p-8 text-center shadow-1">
        <p className="text-sm font-semibold uppercase tracking-wide
                      text-sur-fond-doux">Votre rendez-vous</p>
        {/* Un rendez-vous annulé garde son heure affichée en grisé plutôt que
            de disparaître : la personne doit pouvoir vérifier ce qu'elle vient
            de faire. */}
        <p className={`mt-2 text-6xl font-bold leading-none tracking-tighter
                       tabular-nums ${annule ? 'text-sur-fond-doux line-through' : ''}`}>
          {rdv ? heureCourte(rdv.heure) : '—'}
        </p>
        <p className="mt-3 text-lg text-sur-fond-doux">
          {rdv ? jourLong(rdv.jour) : erreur ? '' : 'chargement…'}
        </p>
      </div>

      {rdv?.motif && (
        <p className="mt-4 text-sur-fond-doux">Motif : {rdv.motif}</p>
      )}

      {/* En INFO et non en accent : un rendez-vous annulé n'est pas une
          réussite, et le vert de la marque le présentait comme telle. */}
      {annule && (
        <div role="status"
             className="mt-4 rounded-[var(--rayon)] border border-info
                        bg-info-voile px-4 py-3 text-info-texte">
          Ce rendez-vous est annulé.
        </div>
      )}
      {erreur && (
        <div role="alert"
             className="mt-4 rounded-[var(--rayon)] border border-alerte
                        bg-alerte-voile px-4 py-3 text-alerte">{erreur}</div>
      )}

      {rdv && !annule && (
        <>
          <button type="button" onClick={annuler} disabled={envoi} aria-busy={envoi}
                  className={`mt-6 w-full cible rounded-[var(--rayon)] border
                             border-alerte bg-transparent px-6 font-semibold
                             text-alerte [touch-action:manipulation]
                             ${attenue(envoi)} hover:bg-alerte-voile`}>
            Annuler ce rendez-vous
          </button>
          <p className="mt-2 text-sm text-sur-fond-doux">
            Prévenir libère la place pour quelqu'un d'autre.
          </p>
        </>
      )}
      {/* Joignable depuis toute page, comme la coquille le promet. Cette
          page a pris — ou va prendre — un numéro de mobile ; n'offrir aucun
          chemin vers la politique de confidentialité serait la seule page du
          site à ne pas en avoir. */}
      <BandeLegale />
    </main>
  );
}
