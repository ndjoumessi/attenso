import { useEffect, useState } from 'react';
import { appel, flux } from '../../src/main/resources/static/app/attenso.js';
import {
  appel as grandNombre, attente, legende, prochains, RIEN,
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
  // Y a-t-il un numéro à montrer ? Deux cas disaient non et produisaient
  // pourtant la même barre blanche : la page en chargement, et la salle où
  // personne n'est appelé.
  const nombre = vue ? grandNombre(vue) : RIEN;
  const aUnNumero = nombre !== RIEN;

  return (
    <main aria-live="polite" aria-atomic="true"
          className="flex min-h-dvh flex-col items-center justify-center
                     gap-6 px-8 py-10 text-center">
      {/* L'en-tête suit la dalle comme les quatre lignes en dessous. Il était
          la DERNIÈRE à rester en paliers : mesuré en production à 1920 × 1080,
          30 px, soit une capitale de 13 mm, lisible à 2,7 m sur un écran qui
          promet quatre mètres. Il plafonne au même corps que le délai — la
          plus petite ligne d'appui — et pas au-dessus : il dit où l'on est,
          pas ce qui arrive.

          Et plus de cadratin : à ce corps il se verrait, et la page n'a pas
          de nom à montrer tant que le serveur n'a pas répondu. L'espace
          insécable tient la ligne, comme sur le suivi et les rendez-vous. */}
      <p className="text-[clamp(1.25rem,2.5vw,3rem)] font-semibold tracking-wide
                    text-[var(--mur-encre-douce)]">
        {vue ? vue.clinique : '\u00A0'}
      </p>

      {/* Le grand nombre. Dimensionné en vw pour rester lisible à quatre
          mètres quel que soit le téléviseur, et borné pour ne pas déborder
          quand deux salles appellent en même temps.
          RENDU SEULEMENT S'IL Y A UN NUMÉRO. Le cadratin de `RIEN` y était
          mesuré à 281,6 px en gras blanc : à cette taille un tiret n'est plus
          de la ponctuation, c'est une barre pleine au milieu d'un écran noir,
          et du fond de la salle ça se lit « écran cassé ». Aucune garde de
          texte ne pouvait le voir — le texte, lui, était juste. */}
      {aUnNumero && (
        <p className="text-[clamp(5rem,22vw,18rem)] font-bold leading-none
                      tracking-tighter tabular-nums">
          {nombre}
        </p>
      )}
      {/* LES LIGNES D'APPUI SUIVENT LE MÊME PRINCIPE QUE LE GRAND NOMBRE —
          dimensionnées en vw — et pour la même raison, qui n'était appliquée
          qu'à lui. Mesurées en production à 1920 × 1080, elles faisaient 36,
          30 et 20 px, et ne grandissaient plus au-delà : des points de
          rupture, pas une proportion.

          Sur un téléviseur de 55 pouces en 1080p, un pixel CSS vaut 0,63 mm
          et la hauteur de capitale environ 0,7 fois le corps. Ces trois
          lignes mesuraient donc 16, 13 et 9 mm de capitale. La règle d'usage
          d'un panneau — capitale ≥ distance de lecture / 200 — demande 20 mm
          à quatre mètres : les trois étaient sous le seuil, la dernière de
          moitié. Elle se lisait à moins de deux mètres, sur un écran dont
          c'est la promesse d'être lu à quatre.

          Les plafonds — 72, 60 et 48 px — tiennent l'ordre de lecture :
          le délai n'est qu'une estimation, il reste la plus petite des trois
          et atteint tout juste les quatre mètres (4,3 m). Les planchers
          servent l'aperçu sur un portable, où vw ne donne presque rien.

          La branche sans numéro reste en points de rupture : à 60 px elle
          est déjà lisible à cinq mètres, donc rien à corriger là. */}
      <p className={aUnNumero
        ? 'text-[clamp(1.75rem,3.75vw,4.5rem)] text-[var(--mur-encre-douce)]'
        : 'text-5xl font-semibold text-[var(--mur-encre-douce)] sm:text-6xl'}>
        {vue ? legende(vue) : 'en attente'}
      </p>

      {suivants && (
        <p className="mt-4 text-[clamp(1.5rem,3.1vw,3.75rem)]
                      text-[var(--mur-encre-douce)]">
          {suivants}
        </p>
      )}
      {delai && (
        <p className="text-[clamp(1.25rem,2.5vw,3rem)]
                      text-[var(--mur-encre-douce)]">{delai}</p>
      )}
    </main>
  );
}
