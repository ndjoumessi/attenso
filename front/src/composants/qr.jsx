import { useEffect, useRef } from 'react';

/**
 * Dessine le QR.
 *
 * Le serveur n'envoie pas de balise : il envoie un côté et un tracé, et c'est
 * ici qu'ils deviennent une figure. Donc createElementNS et setAttribute,
 * jamais innerHTML ni dangerouslySetInnerHTML — rien de ce qui vient du réseau
 * n'est interprété comme de la structure sur cette page.
 */
export function Qr({ qr, className = 'feuille-qr-impression aspect-square w-[62%]' }) {
  const hote = useRef(null);

  useEffect(() => {
    if (!qr || !hote.current) return;
    const NS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', `0 0 ${qr.cote} ${qr.cote}`);
    // Sans ceci, les navigateurs lissent les bords des modules et un QR flou
    // ne se lit pas — à l'écran comme sur le papier.
    svg.setAttribute('shape-rendering', 'crispEdges');
    // Un QR ne se lit pas à la voix : l'adresse est écrite en clair juste en
    // dessous, et c'est elle qui porte l'information pour un lecteur d'écran.
    svg.setAttribute('aria-hidden', 'true');
    svg.style.display = 'block';
    svg.style.width = '100%';
    svg.style.height = '100%';

    // Noir et blanc purs, et non les jetons de la feuille : ce ne sont pas des
    // couleurs, ce sont les deux états que le décodeur d'un téléphone doit
    // distinguer. Le fond blanc est explicite parce qu'un QR transparent posé
    // sur autre chose que du blanc cesse d'être lisible.
    const fond = document.createElementNS(NS, 'rect');
    fond.setAttribute('width', qr.cote);
    fond.setAttribute('height', qr.cote);
    fond.setAttribute('fill', '#ffffff');
    svg.append(fond);

    const trace = document.createElementNS(NS, 'path');
    trace.setAttribute('d', qr.chemin);
    trace.setAttribute('fill', '#000000');
    svg.append(trace);

    hote.current.replaceChildren(svg);
  }, [qr]);

  return <div ref={hote} className={className} />;
}
