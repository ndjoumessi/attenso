import { useCallback, useEffect, useState } from 'react';
import { appel, flux } from '../../src/main/resources/static/app/attenso.js';
import {
  carte, consigne, lienVivant, peutDemanderUneNotification,
  peutSignalerSonArrivee, rappelDuNumero,
} from '../../src/main/resources/static/app/vue-suivi.js';

const RAFRAICHISSEMENT = 60000;

const reference = () => (typeof location === 'undefined'
  ? '' : location.pathname.split('/').filter(Boolean)[1]);

/** La clé publique VAPID doit être présentée au navigateur en octets bruts. */
function versOctets(base64url) {
  const base64 = (base64url + '='.repeat((4 - base64url.length % 4) % 4))
    .replace(/-/g, '+').replace(/_/g, '/');
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
}

function Bouton({ onClick, disabled, children }) {
  return (
    <button type="button" onClick={onClick} disabled={disabled}
            className="w-full cible rounded-[var(--rayon)] border
                       border-bordure-controle bg-surface px-4 font-semibold
                       [touch-action:manipulation] disabled:opacity-60
                       hover:bg-surface-douce
                       focus-visible:outline-2 focus-visible:outline-offset-2
                       focus-visible:outline-[var(--anneau)]">
      {children}
    </button>
  );
}

export function Suivi() {
  const [etat, setEtat] = useState(null);
  const [erreur, setErreur] = useState(null);
  const [pushPossible, setPushPossible] = useState(false);
  const [pushActif, setPushActif] = useState(false);
  const [clePush, setClePush] = useState(null);
  const [arriveeSignalee, setArriveeSignalee] = useState(false);
  const [envoi, setEnvoi] = useState(false);

  const relire = useCallback(async () => {
    try {
      setEtat(await appel(`/api/suivi/${encodeURIComponent(reference())}`));
      setErreur(null);
    } catch (echec) {
      setErreur(echec.statut === 404
        ? "Ce lien n'est plus valable. Adressez-vous à l'accueil."
        : `${echec.message} — dernier état affiché.`);
    }
  }, []);

  useEffect(() => {
    relire();
    const fermer = flux(`/api/suivi/${encodeURIComponent(reference())}/flux`, relire);
    const minuterie = setInterval(relire, RAFRAICHISSEMENT);
    return () => {
      clearInterval(minuterie);
      if (typeof fermer === 'function') fermer();
    };
  }, [relire]);

  // EF-307. Trois conditions, et toutes sont des cas réels : pas de service
  // worker (navigation privée), pas de PushManager (Safari hors écran
  // d'accueil), pas de clé configurée côté service. Le bouton n'apparaît que
  // si les trois sont remplies — en proposer un qui échoue serait pire que
  // de ne rien proposer.
  useEffect(() => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) return;
    appel('/api/push/cle')
      .then(({ clePublique }) => {
        if (clePublique) {
          setClePush(clePublique);
          setPushPossible(true);
        }
      })
      .catch(() => {});
  }, []);

  async function activerPush() {
    setEnvoi(true);
    try {
      // L'autorisation DOIT être demandée depuis un geste de l'utilisateur :
      // la réclamer au chargement la fait refuser d'office par le navigateur,
      // et le refus est définitif pour le site.
      if (await Notification.requestPermission() !== 'granted') {
        setPushPossible(false);
        return;
      }
      const inscription = await navigator.serviceWorker.ready;
      const abonnement = await inscription.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: versOctets(clePush),
      });
      const donnees = abonnement.toJSON();
      await appel(`/api/suivi/${encodeURIComponent(reference())}/push`, {
        method: 'POST',
        body: JSON.stringify({
          endpoint: abonnement.endpoint,
          clePublique: donnees.keys.p256dh,
          cleAuth: donnees.keys.auth,
        }),
      });
      setPushPossible(false);
      setPushActif(true);
    } catch {
      // On laisse le bouton cliquable : l'échec peut être passager.
    } finally {
      setEnvoi(false);
    }
  }

  // EF-205. Un seul envoi suffit ; le bouton reste cliquable ensuite parce que
  // re-signaler rafraîchit l'horodatage, et « il l'a dit il y a deux minutes »
  // vaut mieux que « il y a une heure ».
  async function signalerArrivee() {
    setEnvoi(true);
    try {
      await appel(`/api/suivi/${encodeURIComponent(reference())}/jarrive`,
        { method: 'POST' });
      setArriveeSignalee(true);
    } catch (echec) {
      setErreur(echec.reseau
        ? "Serveur injoignable : l'accueil n'a pas été prévenu."
        : echec.message);
    } finally {
      setEnvoi(false);
    }
  }

  const vue = etat ? carte(etat) : null;
  const message = etat ? consigne(etat) : '';
  // Un ticket clos n'a plus de geste à offrir ; garder le conteneur laisserait
  // une marge vide entre « Consultation terminée » et le rappel du numéro.
  const gestes = peutSignalerSonArrivee(etat?.statut)
    || (pushPossible && peutDemanderUneNotification(etat?.statut));

  return (
    <main className="mx-auto w-full max-w-md px-5 py-10">
      <p className="min-h-5 text-sm font-semibold uppercase tracking-wide
                    text-sur-fond-doux">{etat ? etat.clinique : ''}</p>

      {/* Un lien de suivi invalide laissait cette carte sur « Personnes avant
          vous — / chargement… » pour toujours, avec le 404 dit juste en
          dessous. Elle reste en revanche affichée quand une erreur réseau
          accompagne un état déjà connu : le message promet alors « dernier
          état affiché », et la retirer en ferait un mensonge. */}
      {lienVivant(etat, erreur) && (
        <div aria-live="polite" aria-atomic="true"
             className="mt-4 rounded-[var(--rayon-lg)] border border-bordure-douce
                        bg-surface p-8 text-center shadow-1">
          <p className="text-sm font-semibold uppercase tracking-wide
                        text-sur-fond-doux">
            {vue ? vue.etiquette : 'Personnes avant vous'}
          </p>
          <p className={`mt-2 font-bold leading-none tabular-nums
                         ${vue?.appel
                           ? 'text-5xl text-accent-texte'
                           : 'text-7xl tracking-tighter'}`}>
            {vue ? vue.valeur : '—'}
          </p>
          <p className="mt-3 text-lg text-sur-fond-doux">
            {vue ? vue.dessous : 'chargement…'}
          </p>
        </div>
      )}

      {message && (
        <div role="status"
             className="mt-4 rounded-[var(--rayon)] border border-accent
                        bg-accent-voile px-4 py-3 text-accent-texte">{message}</div>
      )}
      {erreur && (
        <div role="alert"
             className="mt-4 rounded-[var(--rayon)] border border-alerte
                        bg-alerte-voile px-4 py-3 text-alerte">{erreur}</div>
      )}

      {/* Le libellé dit ce que le geste fait VRAIMENT : il prévient l'accueil,
          il ne fait pas passer devant. Promettre autre chose produirait une
          déception à chaque appui. */}
      {gestes && (
        <div className="mt-6 space-y-3">
          {pushPossible && peutDemanderUneNotification(etat?.statut) && (
            <Bouton onClick={activerPush} disabled={envoi}>
              Me prévenir par notification (sans SMS)
            </Bouton>
          )}
          {pushActif && (
            <p role="status" className="text-sm text-sur-fond-doux">
              Notifications activées. Vous ne recevrez pas de SMS pour ce ticket.
            </p>
          )}
          {peutSignalerSonArrivee(etat?.statut) && (
            <Bouton onClick={signalerArrivee} disabled={envoi}>
              Je suis à quelques minutes
            </Bouton>
          )}
          {arriveeSignalee && (
            <p role="status" className="text-sm text-sur-fond-doux">
              C'est noté : l'accueil le voit sur son tableau. Votre tour ne change pas.
            </p>
          )}
        </div>
      )}

      <p className="mt-6 min-h-5 text-sm text-sur-fond-doux">
        {etat ? rappelDuNumero(etat) : ''}
      </p>
      {/* Trouvé en vérifiant le correctif en production : cette promesse
          s'affichait deux lignes sous « Ce lien n'est plus valable ». Même
          condition que la carte — c'est la même propriété. */}
      {lienVivant(etat, erreur) && (
        <p className="mt-2 text-sm leading-relaxed text-sur-fond-doux">
          Cette page se met à jour toute seule. Vous pouvez la garder ouverte,
          la fermer et revenir : le lien reste valable aujourd'hui.
        </p>
      )}
    </main>
  );
}
