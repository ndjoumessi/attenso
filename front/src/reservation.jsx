import { useEffect, useState } from 'react';
import { BandeLegale } from './composants/chrome.jsx';
import { appel } from '../../src/main/resources/static/app/attenso.js';
import {
  grouperParJour, heureCourte, jourLong,
} from '../../src/main/resources/static/app/formats.js';
import {
  AIDE, CHAMP, ETIQUETTE, Erreur, PRESSE, Principal, attenue,
} from './composants/formulaire.jsx';

const slug = () => location.pathname.split('/').filter(Boolean)[1];

export function Reservation() {
  const [clinique, setClinique] = useState('');
  const [creneaux, setCreneaux] = useState(null);
  const [erreur, setErreur] = useState(null);
  const [choisi, setChoisi] = useState(null);
  const [erreurForm, setErreurForm] = useState(null);
  const [envoi, setEnvoi] = useState(false);
  const [confirme, setConfirme] = useState(null);

  useEffect(() => {
    appel(`/t/${encodeURIComponent(slug())}/clinique`)
      .then(({ nom }) => {
        setClinique(nom);
        document.title = `${nom} — prendre rendez-vous`;
      })
      .catch(() => { /* la page reste utilisable sans le nom */ });

    appel(`/t/${encodeURIComponent(slug())}/creneaux`)
      .then(setCreneaux)
      .catch((echec) => {
        setCreneaux([]);
        setErreur(echec.reseau
          ? 'Serveur injoignable : réessayez dans un instant.' : echec.message);
      });
  }, []);

  async function confirmer(evenement) {
    evenement.preventDefault();
    const formulaire = evenement.currentTarget;
    setEnvoi(true);
    setErreurForm(null);
    try {
      const rdv = await appel(`/t/${encodeURIComponent(slug())}/rendez-vous`, {
        method: 'POST',
        body: JSON.stringify({
          jour: choisi.creneau.jour,
          heure: heureCourte(choisi.creneau.heure),
          telephone: formulaire.telephone.value.trim() || null,
          motif: formulaire.motif.value.trim() || null,
        }),
      });
      setConfirme(rdv);
    } catch (echec) {
      // Un créneau pris entre l'affichage et le clic est le cas NORMAL : le
      // message invite à choisir autre chose, pas à réessayer.
      setErreurForm(echec.reseau
        ? "Serveur injoignable : le rendez-vous n'est pas pris." : echec.message);
      setEnvoi(false);
    }
  }

  const entete = (
    <p className="min-h-5 text-sm font-semibold uppercase tracking-wide
                  text-sur-fond-doux">{clinique}</p>
  );

  if (confirme) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-10">
        {entete}
        <h1 className="mt-2 text-3xl font-bold tracking-tight">C'est noté</h1>
        <div className="mt-6 rounded-[var(--rayon-lg)] border border-bordure-douce
                        bg-surface p-8 text-center shadow-1">
          <p className="text-sm font-semibold uppercase tracking-wide
                        text-sur-fond-doux">Votre rendez-vous</p>
          <p className="mt-2 text-6xl font-bold leading-none tracking-tighter
                        tabular-nums">{heureCourte(confirme.heure)}</p>
          <p className="mt-3 text-lg text-sur-fond-doux">{jourLong(confirme.jour)}</p>
        </div>
        <a href={`/r/${confirme.reference}`}
           className="mt-6 flex w-full cible items-center justify-center
                      rounded-[var(--rayon)] border border-bordure-controle
                      bg-surface px-6 font-semibold no-underline
                      hover:bg-surface-douce">
          Voir ou annuler
        </a>
        <p className={AIDE}>
          Gardez ce lien : il vous permet d'annuler sans appeler la clinique.
        </p>
      </main>
    );
  }

  if (choisi) {
    return (
      <main className="mx-auto w-full max-w-md px-5 py-10">
        <p className="text-sm font-semibold uppercase tracking-wide
                      text-sur-fond-doux">{choisi.libelle}</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">Confirmer</h1>
        <form onSubmit={confirmer} className="mt-8 space-y-6">
          <div>
            <label className={ETIQUETTE} htmlFor="telephone">
              Votre mobile (facultatif)
            </label>
            <input id="telephone" name="telephone" type="tel" inputMode="tel"
                   autoComplete="tel" placeholder="06 12 34 56 78"
                   aria-describedby="aide-tel" className={CHAMP} />
            <p id="aide-tel" className={AIDE}>
              Sert uniquement à vous rappeler la veille. Sans numéro, le
              rendez-vous est pris quand même.
            </p>
          </div>
          <div>
            <label className={ETIQUETTE} htmlFor="motif">Motif (facultatif)</label>
            <input id="motif" name="motif" maxLength={120} autoComplete="off"
                   placeholder="Vaccin, contrôle…" className={CHAMP} />
          </div>
          <Erreur>{erreurForm}</Erreur>
          <Principal type="submit" disabled={envoi} aria-busy={envoi}>
            {envoi ? 'Un instant…' : 'Confirmer le rendez-vous'}
          </Principal>
          <button type="button" onClick={() => setChoisi(null)}
                  className={`w-full cible rounded-[var(--rayon)] border
                             border-bordure-controle bg-surface px-6 font-semibold
                             hover:bg-surface-douce ${PRESSE}`}>
            Choisir une autre heure
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-md px-5 py-10">
      {entete}
      <h1 className="mt-2 text-3xl font-bold tracking-tight">Prendre rendez-vous</h1>

      {erreur && <div className="mt-4"><Erreur>{erreur}</Erreur></div>}

      {creneaux === null && (
        <p className="mt-6 text-sur-fond-doux">Chargement des créneaux…</p>
      )}
      {creneaux?.length === 0 && !erreur && (
        <p className="mt-6 leading-relaxed text-sur-fond-doux">
          Aucun créneau n'est ouvert pour le moment. Vous pouvez venir sans
          rendez-vous : la clinique fonctionne en flux libre.
        </p>
      )}

      <div aria-live="polite" className="mt-8 space-y-8">
        {creneaux && [...grouperParJour(creneaux)].map(([jour, liste]) => (
          <section key={jour}>
            <h2 className="font-bold">{jourLong(jour)}</h2>
            <div className="mt-3 flex flex-wrap gap-2">
              {liste.map((creneau) => (
                <button key={`${creneau.jour}-${creneau.heure}`} type="button"
                        onClick={() => setChoisi({
                          creneau,
                          libelle: `${jourLong(jour)} à ${heureCourte(creneau.heure)}`,
                        })}
                        className={`cible-min min-w-20 rounded-[var(--rayon)] border
                                   border-bordure-controle bg-surface px-4
                                   font-semibold tabular-nums
                                   [touch-action:manipulation]
                                   hover:border-accent hover:bg-accent-voile ${PRESSE}`}>
                  {heureCourte(creneau.heure)}
                </button>
              ))}
            </div>
          </section>
        ))}
      </div>
      {/* Joignable depuis toute page, comme la coquille le promet. Cette
          page a pris — ou va prendre — un numéro de mobile ; n'offrir aucun
          chemin vers la politique de confidentialité serait la seule page du
          site à ne pas en avoir. */}
      <BandeLegale />
    </main>
  );
}
