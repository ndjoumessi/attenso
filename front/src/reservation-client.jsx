import { hydrateRoot } from 'react-dom/client';
import { Reservation } from './reservation.jsx';

hydrateRoot(document.getElementById('racine'), <Reservation />);
