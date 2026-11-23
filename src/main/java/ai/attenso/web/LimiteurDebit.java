package ai.attenso.web;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fenêtre glissante par clé, en mémoire.
 *
 * <p>En mémoire assumé : le V0 tourne sur une instance. Le jour où il y en a
 * deux, cette classe devient fausse d'un facteur deux — pas dangereuse, mais à
 * remplacer par un compteur partagé. C'est écrit ici pour qu'on s'en souvienne.
 */
@Component
public class LimiteurDebit {

	private static final Duration RETENTION = Duration.ofMinutes(10);

	private final Map<String, Deque<Long>> fenetres = new ConcurrentHashMap<>();

	private final Clock horloge;

	public LimiteurDebit(Clock horloge) {
		this.horloge = horloge;
	}

	/**
	 * Rend un jeton pris par {@link #autoriser}.
	 *
	 * <p>Sert au motif « réserver puis relâcher » : quand le jeton ne doit être
	 * retenu qu'en cas d'échec, on le prend AVANT l'opération et on le rend si
	 * elle réussit. Vérifier d'abord et compter après laisserait une fenêtre où
	 * plusieurs requêtes simultanées passent toutes le contrôle avant qu'aucune
	 * n'ait incrémenté — et la limite ne limiterait plus que la latence.
	 */
	public void rendre(String cle) {
		Deque<Long> horodatages = fenetres.get(cle);
		if (horodatages == null) {
			return;
		}
		synchronized (horodatages) {
			horodatages.pollLast();
		}
	}

	public boolean autoriser(String cle, int maximum, Duration fenetre) {
		long maintenant = horloge.millis();
		long debut = maintenant - fenetre.toMillis();
		Deque<Long> horodatages = fenetres.computeIfAbsent(cle, c -> new ArrayDeque<>());
		synchronized (horodatages) {
			while (!horodatages.isEmpty() && horodatages.peekFirst() < debut) {
				horodatages.pollFirst();
			}
			if (horodatages.size() >= maximum) {
				return false;
			}
			horodatages.addLast(maintenant);
			return true;
		}
	}

	@Scheduled(fixedDelay = 600_000)
	void oublierLesInactifs() {
		long limite = horloge.millis() - RETENTION.toMillis();
		fenetres.entrySet().removeIf(entree -> {
			Deque<Long> horodatages = entree.getValue();
			synchronized (horodatages) {
				return horodatages.isEmpty() || horodatages.peekLast() < limite;
			}
		});
	}
}
