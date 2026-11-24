import { hydrateRoot } from 'react-dom/client';
import { Inscription } from './inscription.jsx';

hydrateRoot(document.getElementById('racine'), <Inscription />);
