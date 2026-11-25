import { useEffect, useState } from 'react';
import { appel } from '../../../../src/main/resources/static/app/attenso.js';
import {
  promesse, urlLisible,
} from '../../../../src/main/resources/static/app/vue-affiche.js';
import { AIDE, Erreur } from '../../composants/formulaire.jsx';
import { Qr } from '../../composants/qr.jsx';
import { Titre } from '../Sections.jsx';
import { entete } from '../session.js';

/**
 * La vue Affiche du comptoir : le QR par lequel les clients prennent leur tour,
 * et le bouton qui ouvre l'affiche à imprimer.
 *
 * <p>Ouverte aux deux rôles : réimprimer une affiche décollée est un geste de
 * comptoir. `titre` : `false` quand le tableau l'enveloppe, dont l'en-tête dit
 * déjà « Affiche du comptoir ».
 *
 * <p>Trois états : pas encore chargée (`null`, sans échec), chargée, en échec.
 * Sans cette distinction la vue afficherait un aperçu vide avant la réponse.
 *
 * <p>`/affiche` s'ouvre dans un NOUVEL onglet : son lien « Retour au tableau »
 * mène à `/connexion`, donc le suivre ferait repasser le personnel par la
 * connexion et lui ferait perdre l'état du tableau. `rel="noopener"` retire à
 * la page ouverte sa référence `window.opener` vers le tableau du personnel.
 */
export function Affiche({ jeton, titre = true }) {
  const [affiche, setAffiche] = useState(null);
  const [echec, setEchec] = useState(false);

  useEffect(() => {
    let annule = false;
    setEchec(false);
    appel('/api/affiche', { headers: entete(jeton) })
      .then((recu) => { if (!annule) setAffiche(recu); })
      .catch(() => { if (!annule) setEchec(true); });
    return () => { annule = true; };
  }, [jeton]);

  return (
    <>
      {titre && <Titre>L'affiche du comptoir</Titre>}
      <p className={AIDE}>
        Le QR code par lequel les clients prennent leur tour, à imprimer et à
        afficher au comptoir.
      </p>

      {echec && (
        <div className="mt-4">
          <Erreur>
            L'affiche n'a pas pu être chargée. Réessayez dans un instant.
          </Erreur>
        </div>
      )}
      {!affiche && !echec && (
        <p role="status" className={AIDE}>Chargement de l'affiche…</p>
      )}

      {affiche && (
        <div className="mt-4 flex flex-col items-center gap-3 rounded-[var(--rayon)]
                        border border-bordure-douce bg-[var(--papier)] p-6 text-center
                        text-[var(--sur-papier)]">
          <p className="font-semibold text-[var(--sur-papier-doux)]">{affiche.nom}</p>
          <Qr qr={affiche.qr} className="aspect-square w-48" />
          <p className="break-all font-mono text-sm">{urlLisible(affiche.url)}</p>
          <p className="max-w-[30ch] text-sm font-semibold">{promesse(affiche)}</p>
        </div>
      )}

      <p className="mt-4">
        <a href="/affiche" target="_blank" rel="noopener"
           className="cible-min inline-flex items-center text-accent-texte underline">
          Ouvrir l'affiche à imprimer →
        </a>
      </p>
    </>
  );
}
