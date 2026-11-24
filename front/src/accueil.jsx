import { useCallback, useEffect, useRef, useState } from 'react';
import {
  appel, etatLiaison, fileDActions, flux,
} from '../../src/main/resources/static/app/attenso.js';
import {
  disponible, lireEtat, poserEtat, vider as viderJournal,
} from '../../src/main/resources/static/app/journal.js';
import {
  CIBLES, avecEnAttente,
} from '../../src/main/resources/static/app/vue-file.js';
import { aujourdhui, jourLong } from '../../src/main/resources/static/app/formats.js';
import { AIDE, CHAMP, ETIQUETTE, Erreur, Info, Principal } from './composants/formulaire.jsx';
import { Connexion } from './accueil/Connexion.jsx';
import { File } from './accueil/File.jsx';
import { Agenda, Carnets, Saisies, Titre } from './accueil/Sections.jsx';
import { Compteurs, Exploitation } from './accueil/Exploitation.jsx';
import { Mesure } from './accueil/Mesure.jsx';
import {
  CLE_CLINIQUE, CLE_JETON, CLE_MDP_CHANGE, CLE_SLUG, entete, oublierSession,
} from './accueil/session.js';

const FILET_DE_SECURITE = 30000;

export function Accueil() {
  const [jeton, setJeton] = useState(null);
  const [pret, setPret] = useState(false);
  if (!pret && typeof window !== 'undefined') {
    // Lu une seule fois, et pas dans un effet : la page ne doit pas montrer
    // l'écran de connexion à quelqu'un qui est déjà connecté.
    setJeton(localStorage.getItem(CLE_JETON));
    setPret(true);
  }

  if (!pret) return null;
  if (!jeton) return <Connexion surConnexion={setJeton} />;
  return <Tableau jeton={jeton} surDeconnexion={() => setJeton(null)} />;
}

function Tableau({ jeton, surDeconnexion }) {
  const [derniereVue, setDerniereVue] = useState(null);
  const [commandes, setCommandes] = useState([]);
  const [horsReseau, setHorsReseau] = useState(false);
  const [erreur, setErreur] = useState(null);
  const [info, setInfo] = useState(null);
  const [infoCompte, setInfoCompte] = useState(null);
  const [journalAbsent, setJournalAbsent] = useState(false);
  const [identite, setIdentite] = useState('');
  const liaison = useRef(null);
  const actions = useRef(null);

  const rafraichir = useCallback(async () => {
    try {
      const vue = await appel('/api/file', { headers: entete(jeton) });
      liaison.current?.succes();
      setDerniereVue({ vue, enregistreLe: Date.now() });
      // Journal indisponible : déjà signalé par son propre bandeau.
      poserEtat('file', vue).catch(() => {});
      setErreur(null);
    } catch (echec) {
      if (echec.statut === 401) {
        localStorage.removeItem(CLE_JETON);
        location.reload();
        return;
      }
      if (echec.reseau || echec.statut >= 500) {
        liaison.current?.echec();
        return;
      }
      setErreur(echec.message);
    }
  }, [jeton]);

  useEffect(() => {
    let fermerFlux = null;
    const minuteries = [];
    liaison.current = etatLiaison(setHorsReseau);

    (async () => {
      if (sessionStorage.getItem(CLE_MDP_CHANGE)) {
        sessionStorage.removeItem(CLE_MDP_CHANGE);
        setInfoCompte('Mot de passe changé. Vos autres sessions ont été '
          + 'fermées : les autres tablettes devront se reconnecter.');
      }

      // Qui est connecté. Pas bloquant : le tableau doit s'afficher même si
      // cet appel échoue, c'est une information de confort et non la file.
      appel('/api/compte', { headers: entete(jeton) })
        .then((c) => setIdentite(`${c.email} · ${c.role === 'GERANT' ? 'gérant' : 'accueil'}`))
        .catch(() => {});

      // Le journal n'est pas garanti : en navigation privée, IndexedDB
      // échoue. On le dit plutôt que de laisser croire que les gestes sont
      // protégés.
      setJournalAbsent(!(await disponible()));

      // D'abord l'écran, ensuite le réseau : la tablette affiche le dernier
      // tableau connu avant même d'avoir réussi un appel.
      const cache = await lireEtat('file').catch(() => null);
      if (cache) setDerniereVue({ vue: cache.valeur, enregistreLe: cache.enregistreLe });

      const jour = cache?.valeur?.jour ?? aujourdhui();
      actions.current = fileDActions({
        jeton, jour, liaison: liaison.current,
        surEtat: ({ restantes, abandons, erreur: souci, commandes: liste }) => {
          if (liste) setCommandes(liste);
          if (souci) setErreur(souci);
          const messages = [];
          if (restantes > 0) messages.push(`${restantes} geste(s) en attente d'envoi.`);
          if (abandons > 0) {
            messages.push(`${abandons} geste(s) abandonné(s) : le ticket avait `
              + `déjà changé d'état entre-temps.`);
          }
          setInfo(messages.join(' ') || null);
        },
      });
      const perimees = await actions.current.reprendre();
      if (perimees > 0) {
        setErreur(`${perimees} geste(s) d'un autre jour ont été abandonnés : `
          + 'la file est journalière.');
      }

      // Le flux SSE ne transporte AUCUNE donnée : il dit « ça a bougé », et on
      // relit par l'API authentifiée. On s'abonne donc au flux public de la
      // clinique plutôt qu'à un flux protégé — EventSource ne sait pas poser
      // d'en-tête Authorization, et un jeton passé en paramètre d'URL
      // finirait dans les journaux d'accès du serveur et des proxys.
      const slug = localStorage.getItem(CLE_SLUG);
      fermerFlux = flux(`/api/ecran/${encodeURIComponent(slug)}/flux`, rafraichir);
      rafraichir();
      // Filet de sécurité si le SSE meurt sans le dire.
      minuteries.push(setInterval(rafraichir, FILET_DE_SECURITE));
    })();

    return () => {
      minuteries.forEach(clearInterval);
      if (typeof fermerFlux === 'function') fermerFlux();
    };
  }, [jeton, rafraichir]);

  // Le geste est écrit AVANT d'être tenté : il survit à la panne comme au
  // rechargement de la page.
  const agir = (donnees, route) => actions.current
    ?.pousser(`/api/tickets/${donnees.id}/${route}`,
      { ticketId: donnees.id, cible: CIBLES[route] })
    .then(rafraichir);

  // Pas de passage par la file hors ligne : l'ordre est arbitré par le
  // serveur, et deux tablettes qui réordonnent à l'aveugle pendant une panne
  // produiraient deux files différentes, chacune persuadée d'avoir raison.
  const deplacer = async (id, route) => {
    try {
      await appel(`/api/tickets/${id}/${route}`,
        { method: 'POST', headers: entete(jeton) });
    } catch (echec) {
      setErreur(echec.reseau
        ? "Serveur injoignable : l'ordre est arbitré par le serveur." : echec.message);
    }
    rafraichir();
  };

  const vue = derniereVue ? avecEnAttente(derniereVue.vue, commandes) : null;

  return (
    <>
      <Bandeaux horsReseau={horsReseau} journalAbsent={journalAbsent}
                derniereVue={derniereVue} restantes={commandes.length} />
      <main className="mx-auto w-full max-w-3xl px-5 py-8">
        <Entete identite={identite} vue={vue} commandes={commandes}
                surDeconnexion={surDeconnexion} />
        <Compteurs vue={vue} />

        <div className="mt-4 space-y-3">
          <Erreur>{erreur}</Erreur>
          <Info>{info}</Info>
          {/* Son propre bandeau : celui de la file de rejeu est réécrit à
              chaque publication, et la confirmation d'un changement de mot de
              passe y a vécu deux secondes. */}
          <Info>{infoCompte}</Info>
        </div>

        <Titre>File du jour</Titre>
        <File vue={vue} horsReseau={horsReseau} surAction={agir}
              surDeplacement={deplacer} />

        <Saisies jeton={jeton} horsReseau={horsReseau} surErreur={setErreur}
                 surRafraichir={rafraichir} />
        <Agenda jeton={jeton} />
        <Carnets jeton={jeton} surErreur={setErreur} />

        {/* EF-101 : par où le client entre. Ouvert aux deux rôles —
            réimprimer une affiche décollée est un geste de comptoir. */}
        <Titre>L'affiche du comptoir</Titre>
        <p className={AIDE}>
          Le QR code par lequel les clients prennent leur tour, à imprimer et à
          afficher au comptoir.
        </p>
        <p className="mt-2">
          <a href="/affiche" className="cible-min inline-flex items-center
                                        text-accent-texte underline">
            Voir l'affiche à imprimer →
          </a>
        </p>

        <Exploitation jeton={jeton} surErreur={setErreur} />
        <Mesure jeton={jeton} surErreur={setErreur} />
        <MotDePasse jeton={jeton} />
      </main>
    </>
  );
}

function Bandeaux({ horsReseau, journalAbsent, derniereVue, restantes }) {
  if (!horsReseau && !journalAbsent) return null;
  const heure = derniereVue
    ? new Date(derniereVue.enregistreLe)
      .toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
    : '';
  return (
    <div className="sticky top-0 z-20">
      {horsReseau && (
        <div role="status"
             className="bg-attention px-4 py-2 text-center text-sm font-semibold
                        text-sur-attention">
          Serveur injoignable — {restantes > 0 ? `${restantes} geste(s) en attente, ` : ''}
          tableau de {heure} — le tableau reste utilisable, les actions
          partiront au retour du réseau.
        </div>
      )}
      {journalAbsent && (
        <div role="alert"
             className="bg-alerte px-4 py-2 text-center text-sm font-semibold
                        text-sur-alerte">
          Journal local indisponible (navigation privée ?) — les actions faites
          hors réseau seraient perdues.
        </div>
      )}
    </div>
  );
}

function Entete({ identite, vue, commandes, surDeconnexion }) {
  // On retient le NOMBRE averti, pas un simple « déjà averti » : sinon, une
  // personne avertie une fois puis qui continue de travailler hors réseau
  // repartirait plus tard sans un mot, en abandonnant des gestes plus récents
  // que l'avertissement qu'elle a lu.
  const [gestesAvertis, setGestesAvertis] = useState(-1);
  const [avertissement, setAvertissement] = useState(null);

  async function quitter() {
    const restantes = commandes.length;
    if (restantes > 0 && restantes !== gestesAvertis) {
      setGestesAvertis(restantes);
      setAvertissement(`Des gestes ne sont pas encore arrivés au serveur `
        + `(${restantes}). Vous déconnecter maintenant les abandonne. Cliquez `
        + `à nouveau pour partir quand même.`);
      return;
    }
    // La déconnexion VIDE le journal local, gestes en attente compris : on ne
    // laisse pas le tableau d'une clinique sur une tablette qu'on rend.
    await viderJournal();
    oublierSession();
    surDeconnexion();
  }

  return (
    <div className="border-b border-bordure-douce pb-4">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            {typeof localStorage === 'undefined' ? 'Attenso'
              : localStorage.getItem(CLE_CLINIQUE) ?? 'Attenso'}
          </h1>
          <p className="text-sm text-sur-fond-doux">
            {vue ? jourLong(vue.jour) : ''}
          </p>
          {/* Sur une tablette de comptoir partagée, « qui suis-je » précède
              « comment je m'en vais » : les deux sont ici, en haut. */}
          <p className="mt-1 text-sm text-sur-fond-doux">
            {identite}{' '}
            <button type="button" onClick={quitter}
                    className="cible-min text-accent-texte underline">
              Se déconnecter
            </button>
          </p>
        </div>
      </div>
      {avertissement && <div className="mt-3"><Erreur>{avertissement}</Erreur></div>}
    </div>
  );
}

function MotDePasse({ jeton }) {
  const [erreur, setErreur] = useState(null);
  const [envoi, setEnvoi] = useState(false);

  async function changer(evenement) {
    evenement.preventDefault();
    const f = evenement.currentTarget;
    setEnvoi(true);
    setErreur(null);
    try {
      const reponse = await appel('/api/compte/mot-de-passe', {
        method: 'POST', headers: entete(jeton),
        body: JSON.stringify({ actuel: f.actuel.value, nouveau: f.nouveau.value }),
      });
      // Le changement a fermé toutes les sessions du compte, y compris
      // celle-ci : le serveur rend donc un jeton neuf. La page RECHARGE, parce
      // que le flux d'événements et la file de rejeu portent encore l'ancien
      // jeton dans leurs fermetures.
      localStorage.setItem(CLE_JETON, reponse.jeton);
      sessionStorage.setItem(CLE_MDP_CHANGE, '1');
      location.reload();
    } catch (echec) {
      setErreur(echec.reseau ? 'Serveur injoignable.' : echec.message);
      setEnvoi(false);
    }
  }

  return (
    <>
      <Titre>Mon mot de passe</Titre>
      <form onSubmit={changer} className="mt-4 space-y-4">
        <div>
          <label className={ETIQUETTE} htmlFor="mdp-actuel">Mot de passe actuel</label>
          <input id="mdp-actuel" name="actuel" type="password" required
                 maxLength={200} autoComplete="current-password" className={CHAMP} />
        </div>
        <div>
          <label className={ETIQUETTE} htmlFor="mdp-nouveau">Nouveau mot de passe</label>
          <input id="mdp-nouveau" name="nouveau" type="password" required
                 minLength={12} maxLength={200} autoComplete="new-password"
                 aria-describedby="aide-mdp" className={CHAMP} />
          <p id="aide-mdp" className={AIDE}>
            Douze caractères au minimum. Changer votre mot de passe ferme vos
            autres sessions — les autres tablettes devront se reconnecter.
          </p>
        </div>
        <Erreur>{erreur}</Erreur>
        <Principal type="submit" disabled={envoi}>
          {envoi ? 'Un instant…' : 'Changer mon mot de passe'}
        </Principal>
      </form>
    </>
  );
}
