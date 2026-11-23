package ai.attenso.tenant;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Le tenant de la requête ou de la tâche en cours.
 *
 * <p>Il sert deux barrières distinctes : les requêtes le passent explicitement
 * dans leur {@code WHERE} (première barrière), et
 * {@link SourceDonneesCloisonnee} le pousse dans la session PostgreSQL pour
 * que les politiques RLS s'appliquent (seconde barrière). Non positionné, le
 * RLS ne montre aucune ligne : l'oubli donne une liste vide, pas une fuite.
 */
public final class ContexteTenant {

	private static final ThreadLocal<UUID> COURANT = new ThreadLocal<>();

	private ContexteTenant() {
	}

	public static Optional<UUID> courant() {
		return Optional.ofNullable(COURANT.get());
	}

	public static UUID exige() {
		UUID tenant = COURANT.get();
		if (tenant == null) {
			throw new IllegalStateException("aucun tenant lié au fil courant");
		}
		return tenant;
	}

	/** Lie le tenant le temps de l'action, et le délie toujours. */
	public static <T> T avec(UUID tenant, Supplier<T> action) {
		UUID precedent = COURANT.get();
		COURANT.set(tenant);
		try {
			return action.get();
		}
		finally {
			if (precedent == null) {
				COURANT.remove();
			}
			else {
				COURANT.set(precedent);
			}
		}
	}

	public static void executer(UUID tenant, Runnable action) {
		avec(tenant, () -> {
			action.run();
			return null;
		});
	}

	public static void lier(UUID tenant) {
		COURANT.set(tenant);
	}

	public static void delier() {
		COURANT.remove();
	}
}
