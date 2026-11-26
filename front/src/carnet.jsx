import { useEffect, useState } from 'react';
import { BandeLegale } from './composants/chrome.jsx';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import {
  aujourdhui, jourAvecAnnee,
} from '../../src/main/resources/static/app/formats.js';

function Etiquette({ ton = 'neutre', children }) {
  const tons = {
    neutre: 'border-bordure-douce bg-surface-douce text-sur-fond-doux',
    proche: 'border-attention bg-attention-voile text-attention',
    retard: 'border-alerte bg-alerte-voile text-alerte',
  };
  return (
    <span className={`inline-flex items-center rounded-full border px-3 py-1
                      text-sm font-semibold ${tons[ton]}`}>{children}</span>
  );
}

export function Carnet() {
  const [carnet, setCarnet] = useState(null);
  const [erreur, setErreur] = useState(null);

  useEffect(() => {
    const jeton = location.pathname.split('/').filter(Boolean)[1];
    appel(`/api/carnet/${encodeURIComponent(jeton)}`)
      .then((recu) => {
        setCarnet(recu);
        document.title = `${recu.animal} — carnet de santé`;
      })
      .catch((echec) => setErreur(echec.reseau
        ? 'Serveur injoignable : réessayez dans un instant.' : echec.message));
  }, []);

  const ceJour = aujourdhui();

  return (
    <main className="mx-auto w-full max-w-md px-5 py-10">
      <p className="min-h-5 text-sm font-semibold uppercase tracking-wide
                    text-sur-fond-doux">{carnet ? carnet.clinique : ''}</p>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">
        {carnet ? carnet.animal : erreur ? 'Carnet introuvable' : 'Carnet de santé'}
      </h1>
      <p className="min-h-5 text-sur-fond-doux">{carnet ? carnet.espece : ''}</p>

      {erreur && (
        <div role="alert"
             className="mt-4 rounded-[var(--rayon)] border border-alerte
                        bg-alerte-voile px-4 py-3 text-alerte">{erreur}</div>
      )}

      {carnet && (
        <>
          <h2 className="mt-8 text-xl font-bold">Vaccinations</h2>
          {carnet.vaccins.length === 0 ? (
            <p className="mt-3 text-sur-fond-doux">Aucune vaccination enregistrée.</p>
          ) : (
            <ul aria-live="polite" className="mt-4 space-y-3">
              {carnet.vaccins.map((vaccin, i) => {
                // En retard : la teinte d'alerte. C'est la seule information
                // de cette page qui demande une action, elle doit se voir.
                const enRetard = vaccin.rappelLe && vaccin.rappelLe < ceJour;
                return (
                  <li key={`${vaccin.vaccin}-${vaccin.faitLe}-${i}`}
                      className="rounded-[var(--rayon)] border border-bordure-douce
                                 bg-surface p-4 shadow-1">
                    <strong className="text-lg">{vaccin.vaccin}</strong>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <Etiquette>fait le {jourAvecAnnee(vaccin.faitLe)}</Etiquette>
                      {vaccin.rappelLe && (
                        <Etiquette ton={enRetard ? 'retard' : 'proche'}>
                          {enRetard ? 'rappel dépassé — ' : 'rappel le '}
                          {jourAvecAnnee(vaccin.rappelLe)}
                        </Etiquette>
                      )}
                    </div>
                    {vaccin.note && (
                      <p className="mt-2 text-sm text-sur-fond-doux">{vaccin.note}</p>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}

      <p className="mt-8 text-sm leading-relaxed text-sur-fond-doux">
        Ce carnet est en lecture seule. Son adresse peut être révoquée par la
        clinique à la demande du propriétaire.
      </p>
      {/* Joignable depuis toute page, comme la coquille le promet. Cette
          page a pris — ou va prendre — un numéro de mobile ; n'offrir aucun
          chemin vers la politique de confidentialité serait la seule page du
          site à ne pas en avoir. */}
      <BandeLegale />
    </main>
  );
}
