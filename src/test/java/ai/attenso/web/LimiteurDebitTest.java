package ai.attenso.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimiteurDebitTest {

	private static final Duration FENETRE = Duration.ofMinutes(10);

	private final Clock fixe = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"),
			ZoneOffset.UTC);

	private final LimiteurDebit limiteur = new LimiteurDebit(fixe);

	@Test
	@DisplayName("la limite s'applique, et un jeton rendu redevient disponible")
	void reserverPuisRendre() {
		assertThat(limiteur.autoriser("a", 2, FENETRE)).isTrue();
		assertThat(limiteur.autoriser("a", 2, FENETRE)).isTrue();
		assertThat(limiteur.autoriser("a", 2, FENETRE)).isFalse();

		limiteur.rendre("a");

		assertThat(limiteur.autoriser("a", 2, FENETRE))
				.as("le motif « réserver puis relâcher » ne doit pas pénaliser un succès")
				.isTrue();
	}

	@Test
	@DisplayName("rendre un jeton sur une clé inconnue ne casse rien")
	void rendreSurCleInconnue() {
		limiteur.rendre("jamais-vue");
		assertThat(limiteur.autoriser("jamais-vue", 1, FENETRE)).isTrue();
	}

	@Test
	@DisplayName("les clés sont indépendantes")
	void clesIndependantes() {
		assertThat(limiteur.autoriser("a", 1, FENETRE)).isTrue();
		assertThat(limiteur.autoriser("a", 1, FENETRE)).isFalse();
		assertThat(limiteur.autoriser("b", 1, FENETRE)).isTrue();
	}

	@Test
	@DisplayName("sous assaut simultané, pas un jeton de plus que la limite")
	void aucunDepassementSousConcurrence() throws Exception {
		int simultanes = 64;
		int plafond = 10;
		AtomicInteger accordes = new AtomicInteger();
		CountDownLatch depart = new CountDownLatch(1);
		ExecutorService fils = Executors.newFixedThreadPool(simultanes);
		try {
			for (int i = 0; i < simultanes; i++) {
				fils.submit(() -> {
					depart.await();
					if (limiteur.autoriser("assaut", plafond, FENETRE)) {
						accordes.incrementAndGet();
					}
					return null;
				});
			}
			depart.countDown();
			fils.shutdown();
			assertThat(fils.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			fils.shutdownNow();
		}

		assertThat(accordes.get())
				.as("vérifier d'abord et compter après laissait passer tout le lot")
				.isEqualTo(plafond);
	}
}
