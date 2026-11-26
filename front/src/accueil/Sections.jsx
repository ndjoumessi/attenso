import { useEffect, useState } from 'react';
import { LIGNE, Squelette } from '../composants/squelette.jsx';
import { appel } from '../../../src/main/resources/static/app/attenso.js';
import { heureCourte } from '../../../src/main/resources/static/app/formats.js';
import { Adresse } from '../composants/adresse.jsx';
import {
  AIDE, ANNEAU, CHAMP, ETIQUETTE, Erreur, SECONDAIRE,
} from '../composants/formulaire.jsx';
import { Qr } from '../composants/qr.jsx';
import { entete } from './session.js';

export function Titre({ children }) {
  return <h2 className="mt-12 text-xl font-bold tracking-tight">{children}</h2>;
}


/**
 * Rendez-vous du jour (EF-905).
 *
 * <p>`vide` : ce qu'on affiche quand il n'y en a aucun. La section seule
 * détient la liste chargée, donc seule elle sait qu'elle est vide ; c'est la
 * vue qui l'enveloppe qui sait quoi dire alors. Tant que rien n'est chargé
 * (`null`) elle ne dit rien ; si le premier chargement échoue elle le dit, car
 * ni la liste ni « aucun » ne seraient vrais.
 *
 * <p>`titre` : `false` quand une vue l'enveloppe, dont l'en-tête porte déjà
 * le même libellé. Seule, la section garde son titre.
 */
export function Agenda({ jeton, vide, titre = true }) {
  const [rdvs, setRdvs] = useState(null);
  const [echec, setEchec] = useState(false);

  useEffect(() => {
    const relire = () => appel('/api/agenda', { headers: entete(jeton) })
      .then((recus) => { setRdvs(recus); setEchec(false); })
      // L'agenda est un à-côté : il ne doit pas masquer la file. Une liste déjà
      // chargée est gardée si un rafraîchissement échoue.
      .catch(() => setEchec(true));
    relire();
    const t = setInterval(relire, 120000);
    return () => clearInterval(t);
  }, [jeton]);

  return (
    <>
      {titre && <Titre>Rendez-vous du jour</Titre>}
      {/* Trois états, et non deux : pas encore chargé, chargé et vide, chargé
          et plein. Sans squelette, le premier ressemblait au deuxième — un
          écran nu — et on ne pouvait pas savoir si la page travaillait. */}
      {rdvs === null ? (echec ? (
        <Erreur>Les rendez-vous n'ont pas pu être chargés. Réessayez dans un
          instant.</Erreur>
      ) : <Squelette />) : rdvs.length === 0 ? vide : (
        <ul aria-busy={rdvs === null} className="mt-4 space-y-2">
          {rdvs.map((rdv) => (
            <li key={rdv.reference ?? `${rdv.heure}-${rdv.motif}`}
                className={`${LIGNE} flex items-center gap-4
                            rounded-[var(--rayon)] border border-bordure-douce
                            bg-surface p-3
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
      )}
    </>
  );
}

/**
 * Carnets de santé (EF-906).
 *
 * <p>`vide` : ce qu'on affiche une fois la liste chargée et vide. Tant qu'elle
 * n'est pas chargée (`null`), ou si le chargement échoue, rien : un « aucun
 * carnet » affiché pendant le chargement dirait faux. `titre` : voir `Agenda`.
 */
export function Carnets({ jeton, surErreur, vide, titre = true }) {
  const [animaux, setAnimaux] = useState(null);
  const [echec, setEchec] = useState(false);
  const [qrOuvert, setQrOuvert] = useState(null);
  const [qrCarnet, setQrCarnet] = useState(null);
  const [echecQr, setEchecQr] = useState(false);

  // Un échec LOCAL, pas le bandeau partagé du haut de page : celui-ci est
  // effacé à chaque rafraîchissement réussi de la file, donc à chaque
  // événement SSE. Une liste déjà chargée est gardée si une relecture échoue.
  const relire = () => appel('/api/animaux', { headers: entete(jeton) })
    .then((recus) => { setAnimaux(recus); setEchec(false); })
    .catch(() => setEchec(true));

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

  async function montrerLeQr(id) {
    if (qrOuvert === id) {
      setQrOuvert(null);
      return;
    }
    setQrOuvert(id);
    setQrCarnet(null);
    setEchecQr(false);
    try {
      const vue = await appel(`/api/animaux/${id}/carnet/qr`, { headers: entete(jeton) });
      setQrCarnet(vue.qr);
    } catch {
      setEchecQr(true);
    }
  }

  async function revoquer(id) {
    try {
      await appel(`/api/animaux/${id}/carnet/revoquer`, {
        method: 'POST', headers: entete(jeton),
        body: JSON.stringify({ reemettre: true }),
      });
      // Le QR affiché vient d'être invalidé : le garder à l'écran ferait
      // scanner une adresse morte.
      setQrOuvert(null);
      setQrCarnet(null);
      relire();
    } catch (echec) {
      surErreur(echec.reseau
        ? "Serveur injoignable : le QR n'a PAS été révoqué." : echec.message);
    }
  }

  return (
    <>
      {titre && <Titre>Carnets de santé</Titre>}
      {echec && (
        <div className="mt-4">
          <Erreur>
            Les carnets n'ont pas pu être chargés. Réessayez dans un instant.
          </Erreur>
        </div>
      )}
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
        <button type="submit" className={`w-full ${SECONDAIRE} ${ANNEAU}`}>Ajouter l'animal</button>
      </form>

      {/* Hauteur déclarée ici et non héritée : une ligne de carnet porte
          selon l'animal une adresse copiable et deux boutons QR, donc aucune
          hauteur ne serait juste. Le squelette y dit « ça travaille », il ne
          prétend pas réserver la place. */}
      {animaux === null && !echec && <Squelette lignes={2} hauteur="h-14" />}
      {animaux?.length === 0 && vide}
      <ul aria-busy={animaux === null} className="mt-4 space-y-2">
        {animaux?.map((animal) => (
          <li key={animal.id}
              className="rounded-[var(--rayon)] border border-bordure-douce
                         bg-surface p-3">
            <strong>{animal.nom} — {animal.espece}</strong>
            {animal.jetonCarnet && (
              <div className="mt-2">
                <Adresse url={`${location.origin}/carnet/${animal.jetonCarnet}`} />
                {/* EF-906 promet un accès « en scannant un QR code ». Le
                    tableau disait « Émettre un QR », « Révoquer le QR », et
                    n'en montrait aucun : il n'en existait nulle part, pas même
                    côté serveur. Il se demande À LA DEMANDE — charger un QR
                    par animal au montage ferait payer la liste entière pour un
                    geste qui porte sur un seul. */}
                <div className="mt-2 flex flex-wrap gap-2">
                  <button type="button" onClick={() => montrerLeQr(animal.id)}
                          className={SECONDAIRE}>
                    {qrOuvert === animal.id ? 'Masquer le QR' : 'Montrer le QR'}
                  </button>
                  <button type="button" onClick={() => revoquer(animal.id)}
                          title="Le QR actuel cessera de fonctionner et un nouveau sera émis."
                          className={SECONDAIRE}>
                    Révoquer le QR
                  </button>
                </div>
                {qrOuvert === animal.id && (
                  <div className="mt-3 flex justify-center rounded-[var(--rayon)]
                                  bg-white p-4">
                    {qrCarnet
                      ? <Qr qr={qrCarnet} className="aspect-square w-48" />
                      : <p className="text-sm text-[#333]">Un instant…</p>}
                  </div>
                )}
                {echecQr && (
                  <p role="status" className={AIDE}>
                    Le QR n'a pas pu être chargé. Réessayez dans un instant.
                  </p>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
    </>
  );
}
