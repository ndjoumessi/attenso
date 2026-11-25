import { useEffect, useState } from 'react';
import { Retour } from './composants/chrome.jsx';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import {
  promesse, urlLisible,
} from '../../src/main/resources/static/app/vue-affiche.js';
import { Qr } from './composants/qr.jsx';

const CLE_JETON = 'attenso.jeton';

export function Affiche() {
  const [vue, setVue] = useState(null);
  const [erreur, setErreur] = useState(null);

  useEffect(() => {
    const jeton = localStorage.getItem(CLE_JETON);
    if (!jeton) {
      location.replace('/connexion');
      return;
    }
    appel('/api/affiche', { headers: { Authorization: `Bearer ${jeton}` } })
      .then(setVue)
      .catch((echec) => {
        if (echec.statut === 401) location.replace('/connexion');
        else {
          setErreur(echec.reseau
            ? "Serveur injoignable : l'affiche ne peut pas être préparée."
            : echec.message);
        }
      });
  }, []);

  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-10 feuille-page">
      <div className="print:hidden">
        {/* Trouvé par la garde transversale, pas par la mesure : cette page
            demande une connexion, donc le balayage des pages publiques ne la
            voyait pas. Elle rendait la même cible de 17 px. */}
        <Retour href="/connexion">Retour au tableau</Retour>
        <h1 className="mt-4 text-3xl font-bold tracking-tight">
          L'affiche du comptoir
        </h1>
        <p className="mt-3 max-w-[62ch] leading-relaxed text-sur-fond-doux">
          C'est par ce code que le client entre dans la file. Imprimez cette page
          en A4, sans « ajuster à la page » ni impression recto-verso, et
          affichez-la au comptoir à hauteur de regard. Le code reste valable :
          il porte l'adresse de votre clinique, pas celle d'une journée.
        </p>
        {erreur && (
          <div role="alert"
               className="mt-4 rounded-[var(--rayon)] border border-alerte
                          bg-alerte-voile px-4 py-3 text-alerte">{erreur}</div>
        )}
        <button type="button" onClick={() => window.print()} disabled={!vue}
                className="mt-6 cible rounded-[var(--rayon)] bg-accent px-6
                           font-bold text-sur-accent shadow-1 disabled:opacity-60
                           hover:brightness-110">
          Imprimer l'affiche
        </button>
      </div>

      {vue && (
        /* La feuille est du papier, pas de l'écran : elle reste noire sur
           blanc en mode sombre — seconde exception du produit après le mur de
           la salle d'attente, et pour la même raison, ces deux-là sont des
           objets physiques. */
        <section className="mt-10 flex aspect-[210/297] flex-col items-center
                            justify-center gap-4 rounded-[var(--rayon)] border
                            border-bordure-douce bg-[var(--papier)] p-8
                            text-center text-[var(--sur-papier)] shadow-1
                            print:mt-0 print:aspect-auto print:min-h-full
                            print:rounded-none print:border-0 print:p-0
                            print:shadow-none">
          <p className="text-xl font-semibold text-[var(--sur-papier-doux)]">
            {vue.nom}
          </p>
          {/* La couleur et la casse sont REPOSÉES : le style h2 de
              l'application donnait ici un titre gris pâle à 2,2:1 sur fond
              blanc — le plus gros caractère de l'affiche, et le moins lisible. */}
          <h2 className="text-5xl font-bold leading-tight tracking-tight
                         text-[var(--sur-papier)] normal-case">
            Prenez votre tour
          </h2>
          <p className="max-w-[28ch] text-xl">
            Scannez ce code avec l'appareil photo de votre téléphone.
          </p>
          <Qr qr={vue.qr} />
          <p className="text-lg text-[var(--sur-papier-doux)]">
            ou tapez{' '}
            <strong className="break-all font-mono text-xl text-[var(--sur-papier)]">
              {urlLisible(vue.url)}
            </strong>
          </p>
          <p className="max-w-[30ch] text-xl font-semibold">{promesse(vue)}</p>
          <p className="text-base text-[var(--sur-papier-doux)]">
            Aucune application à installer. Votre numéro sert à vous prévenir
            aujourd'hui, puis il est effacé.
          </p>
        </section>
      )}
    </main>
  );
}
