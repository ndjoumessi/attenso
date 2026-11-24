import { useEffect, useState } from 'react';
import { appel, flux } from '../../src/main/resources/static/app/attenso.js';
import {
  appel as grandNombre, attente, legende, prochains,
} from '../../src/main/resources/static/app/vue-ecran.js';

// Les fonctions de vue sont IMPORTÉES, pas réécrites. Elles ne touchent pas au
// DOM, leurs tests non plus, et ce sont elles qui décident ce que le mur
// annonce — « sont appelés » au pluriel, « consultation en cours » plutôt que
// « personne en salle » quand la file n'est pas vide. Les reproduire en JSX
// aurait fait deux vérités pour une seule règle.

const RAFRAICHISSEMENT = 20000;

export function Ecran({ jeton }) {
  const [vue, setVue] = useState(null);

  useEffect(() => {
    const route = `/api/ecran/jeton/${encodeURIComponent(jeton)}`;
    let vivant = true;
    const relire = async () => {
      try {
        const neuve = await appel(route);
        if (vivant) setVue(neuve);
      } catch {
        // L'écran garde son dernier affichage : un mur vide inquiète la salle
        // plus qu'un numéro de trente secondes.
      }
    };
    relire();
    const fermer = flux(`${route}/flux`, relire);
    const minuterie = setInterval(relire, RAFRAICHISSEMENT);
    return () => {
      vivant = false;
      clearInterval(minuterie);
      if (typeof fermer === 'function') fermer();
    };
  }, [jeton]);

  const suivants = vue ? prochains(vue) : '';
  const delai = vue ? attente(vue) : '';

  return (
    <main aria-live="polite" aria-atomic="true"
          className="flex min-h-dvh flex-col items-center justify-center
                     gap-6 px-8 py-10 text-center">
      <p className="text-2xl font-semibold tracking-wide
                    text-[var(--mur-encre-douce)] sm:text-3xl">
        {vue ? vue.clinique : '—'}
      </p>

      {/* Le grand nombre. Dimensionné en vw pour rester lisible à quatre
          mètres quel que soit le téléviseur, et borné pour ne pas déborder
          quand deux salles appellent en même temps. */}
      <p className="text-[clamp(5rem,22vw,18rem)] font-bold leading-none
                    tracking-tighter tabular-nums">
        {vue ? grandNombre(vue) : '—'}
      </p>
      <p className="text-3xl text-[var(--mur-encre-douce)] sm:text-4xl">
        {vue ? legende(vue) : 'en attente'}
      </p>

      {suivants && (
        <p className="mt-4 text-2xl text-[var(--mur-encre-douce)] sm:text-3xl">
          {suivants}
        </p>
      )}
      {delai && (
        <p className="text-xl text-[var(--mur-encre-douce)]">{delai}</p>
      )}
    </main>
  );
}
