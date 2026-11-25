import { useEffect, useState } from 'react';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
import { AIDE, CHAMP, ETIQUETTE } from '../composants/formulaire.jsx';
import { entete } from './session.js';
import { Titre } from './Sections.jsx';

// Les quatre verdicts du cahier des charges, à la lettre.
const ZONES = {
  NON_MESURE: ['', "Saisissez le nombre d'arrivants pour obtenir le taux."],
  INVALIDE: ['border-alerte bg-alerte-voile text-alerte',
    "Sous 30 % : le cahier des charges demande d'arrêter ou de revoir le parcours."],
  ZONE_GRISE: ['border-attention bg-attention-voile text-attention',
    'Entre 30 et 50 % : zone grise, à décider avec la clinique.'],
  VALIDE: ['border-accent bg-accent-voile text-accent-texte',
    'Au-dessus de 50 % : le parcours fonctionne.'],
};

const secondaire = 'cible rounded-[var(--rayon)] border border-bordure-controle '
  + 'bg-surface px-4 font-semibold hover:bg-surface-douce disabled:opacity-50';

function Compteur({ libelle, valeur }) {
  return (
    <div className="rounded-[var(--rayon)] border border-bordure-douce bg-surface
                    px-4 py-3">
      <dt className="text-xs font-semibold uppercase tracking-wide text-sur-fond-doux">
        {libelle}
      </dt>
      <dd className="mt-1 text-2xl font-bold tabular-nums">{valeur}</dd>
    </div>
  );
}

/**
 * Mesure de l'auto-enregistrement.
 *
 * <p>`titre` : `false` quand le tableau l'enveloppe : son en-tête dit « Mesure »
 * et suffit, le titre long ne ferait que le redoubler. Seule, la section le
 * garde.
 */
export function Mesure({ jeton, surErreur, titre = true }) {
  const [mesure, setMesure] = useState(null);
  const [arrivants, setArrivants] = useState('');

  useEffect(() => {
    const relire = () => appel('/api/mesure', { headers: entete(jeton) })
      .then((recue) => {
        setMesure(recue);
        setArrivants((actuel) => (actuel === '' && recue.arrivantsTotal !== null
          ? String(recue.arrivantsTotal) : actuel));
      })
      // Silencieux : la mesure est un à-côté, son indisponibilité ne doit pas
      // couvrir le tableau d'un bandeau d'erreur.
      .catch(() => {});
    relire();
    const t = setInterval(relire, 120000);
    return () => clearInterval(t);
  }, [jeton]);

  async function enregistrer(evenement) {
    evenement.preventDefault();
    if (arrivants === '') return;
    try {
      setMesure(await appel('/api/mesure/arrivants', {
        method: 'PUT', headers: entete(jeton),
        body: JSON.stringify({ arrivants: Number(arrivants) }),
      }));
    } catch (echec) {
      surErreur(echec.reseau
        ? 'Serveur injoignable : le comptage sera à ressaisir.' : echec.message);
    }
  }

  async function exporter() {
    try {
      // L'export passe par fetch et non par un lien : la route exige l'en-tête
      // Authorization, qu'un <a href> ne sait pas poser. Mettre le jeton dans
      // l'URL le ferait atterrir dans les journaux d'accès.
      const reponse = await fetch('/api/mesure/export.csv', { headers: entete(jeton) });
      if (!reponse.ok) throw new Error(`export refusé (${reponse.status})`);
      const nom = (reponse.headers.get('Content-Disposition') ?? '')
        .match(/filename="([^"]+)"/)?.[1] ?? 'attenso.csv';
      const lien = document.createElement('a');
      lien.href = URL.createObjectURL(await reponse.blob());
      lien.download = nom;
      lien.click();
      URL.revokeObjectURL(lien.href);
    } catch {
      surErreur('Export indisponible : serveur injoignable.');
    }
  }

  const [teinte, texte] = ZONES[mesure?.zone] ?? ['', ''];

  return (
    <>
      {titre && <Titre>Mesure de l'auto-enregistrement</Titre>}
      <p className={AIDE}>
        Combien de personnes sont entrées dans la clinique aujourd'hui, ticket
        ou pas. Le système compte celles qui prennent un ticket ; il ne voit pas
        celles qui franchissent la porte. Sans ce chiffre, le taux
        d'auto-enregistrement n'est pas calculable.
      </p>

      <form onSubmit={enregistrer} className="mt-4 space-y-4">
        <div>
          <label className={ETIQUETTE} htmlFor="arrivants">
            Personnes entrées aujourd'hui
          </label>
          <input id="arrivants" type="number" inputMode="numeric" min={0}
                 max={2000} step={1} autoComplete="off" className={CHAMP}
                 value={arrivants}
                 onChange={(e) => setArrivants(e.target.value)} />
        </div>
        <button type="submit" className={`w-full ${secondaire}`}>
          Enregistrer le comptage
        </button>
      </form>

      <dl className="mt-6 grid gap-3 sm:grid-cols-3">
        <Compteur libelle="Tickets pris seul"
                  valeur={mesure ? mesure.ticketsComptoir : '—'} />
        <Compteur libelle="Saisis par l'accueil"
                  valeur={mesure ? mesure.ticketsAccueil : '—'} />
        {/* Un taux absent n'est pas un taux de zéro : afficher « 0 % » parce
            que personne n'a compté les arrivants ferait conclure à l'échec. */}
        <Compteur libelle="Auto-enregistrement"
                  valeur={mesure?.taux == null ? '—' : `${mesure.taux} %`} />
      </dl>

      {texte && (
        <p role="status"
           className={`mt-4 rounded-[var(--rayon)] border px-4 py-3
                       ${teinte || 'border-bordure-douce text-sur-fond-doux'}`}>
          {texte}
        </p>
      )}

      <p className="mt-4">
        <button type="button" onClick={exporter} className={secondaire}>
          Télécharger l'historique (CSV)
        </button>
      </p>
    </>
  );
}
