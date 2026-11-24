import { createRoot } from 'react-dom/client';
import { Affiche } from './affiche.jsx';

// Montée et non hydratée : la feuille n'existe qu'une fois les données
// arrivées, et pré-rendre une affiche sans QR n'avancerait à rien.
createRoot(document.getElementById('racine')).render(<Affiche />);
