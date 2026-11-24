import { hydrateRoot } from 'react-dom/client';
import { RendezVous } from './rendez-vous.jsx';

hydrateRoot(document.getElementById('racine'), <RendezVous />);
