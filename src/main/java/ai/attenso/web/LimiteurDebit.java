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
	 * Observe sans consommer. Sert quand le jeton ne doit être pris qu'en cas
	 * d'échec — compter les refus, par exemple, sans pénaliser les succès.
	 */
	public boolean sousLaLimite(String cle, int maximum, Duration fenetre) {
		Deque<Long> horodatages = fenetres.get(cle);
		if (horodatages == null) {
			return true;
		}
		long debut = horloge.millis() - fenetre.toMillis();
		synchronized (horodatages) {
			return horodatages.stream().filter(instant -> instant >= debut).count() < maximum;
		}
	}

	/** Consomme un jeton sans vérifier la limite. */
	public void compter(String cle) {
		fenetres.computeIfAbsent(cle, c -> new ArrayDeque<>()).addLast(horloge.millis());
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
