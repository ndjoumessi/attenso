package ai.attenso.web;

import ai.attenso.ticket.EvenementFileModifiee;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Diffusion des changements de file en SSE.
 *
 * <p>L'événement ne transporte pas l'état : il dit « ça a bougé », et le client
 * va relire. C'est volontaire — l'écran de salle d'attente et la page client ne
 * veulent pas la même chose, et un seul message pour les deux évite d'inventer
 * un format commun qui ne conviendrait à personne.
 */
@Component
public class DiffusionFile {

	private static final Logger JOURNAL = LoggerFactory.getLogger(DiffusionFile.class);

	private static final long SANS_EXPIRATION = 0L;

	private final Map<String, Set<SseEmitter>> abonnes = new ConcurrentHashMap<>();

	public SseEmitter abonner(UUID tenantId, LocalDate jour) {
		SseEmitter flux = new SseEmitter(SANS_EXPIRATION);
		String cle = cle(tenantId, jour);
		abonnes.computeIfAbsent(cle, c -> ConcurrentHashMap.newKeySet()).add(flux);
		flux.onCompletion(() -> retirer(cle, flux));
		flux.onTimeout(() -> retirer(cle, flux));
		flux.onError(erreur -> retirer(cle, flux));
		return flux;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void surFileModifiee(EvenementFileModifiee evenement) {
		diffuser(cle(evenement.tenantId(), evenement.jour()), "maj", "1");
	}

	/** Garde les connexions ouvertes à travers les proxys qui coupent au silence. */
	@Scheduled(fixedDelay = 25_000)
	void battement() {
		abonnes.keySet().forEach(cle -> diffuser(cle, "battement", "1"));
	}

	private void diffuser(String cle, String nom, String donnee) {
		Set<SseEmitter> flux = abonnes.get(cle);
		if (flux == null) {
			return;
		}
		for (SseEmitter emetteur : flux) {
			try {
				emetteur.send(SseEmitter.event().name(nom).data(donnee));
			}
			catch (IOException | IllegalStateException ferme) {
				retirer(cle, emetteur);
			}
			catch (RuntimeException panne) {
				JOURNAL.debug("diffusion SSE abandonnée : {}", panne.getMessage());
				retirer(cle, emetteur);
			}
		}
	}

	private void retirer(String cle, SseEmitter emetteur) {
		Set<SseEmitter> flux = abonnes.get(cle);
		if (flux != null) {
			flux.remove(emetteur);
			if (flux.isEmpty()) {
				abonnes.remove(cle, flux);
			}
		}
	}

	private String cle(UUID tenantId, LocalDate jour) {
		return tenantId + "/" + jour;
	}

	int nombreAbonnes() {
		return abonnes.values().stream().mapToInt(Set::size).sum();
	}
}
