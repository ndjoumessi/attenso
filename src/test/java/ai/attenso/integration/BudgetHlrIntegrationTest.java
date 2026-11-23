package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.numero.BudgetHlrDao;
import ai.attenso.tenant.ContexteTenant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * La réservation du budget, sous concurrence réelle.
 *
 * <p>C'est le seul test qui prouve quelque chose ici : la version précédente
 * comptait puis appelait, et passait tous les tests séquentiels. Son défaut
 * n'apparaît qu'avec plusieurs requêtes simultanées.
 */
class BudgetHlrIntegrationTest extends SocleIntegration {

	private static final int PLAFOND = 10;

	private static final int SIMULTANES = 40;

	@Autowired
	BudgetHlrDao budget;

	@Test
	@DisplayName("quarante demandes simultanées pour un plafond de dix : "
			+ "exactement dix passent")
	void laReservationEstIndivisible() throws Exception {
		UUID clinique = creerClinique("budget-course", "Clinique", 2, 180);
		LocalDate jour = LocalDate.now();

		ExecutorService fils = Executors.newFixedThreadPool(SIMULTANES);
		CountDownLatch depart = new CountDownLatch(1);
		try {
			List<Callable<Boolean>> demandes = java.util.stream.IntStream.range(0, SIMULTANES)
					.<Callable<Boolean>>mapToObj(i -> () -> {
						depart.await();
						// ContexteTenant est un ThreadLocal : chaque fil lie le
						// sien, sans quoi le RLS masquerait tout.
						return ContexteTenant.avec(clinique,
								() -> budget.reserver(clinique, jour, PLAFOND));
					})
					.toList();

			List<Future<Boolean>> resultats = demandes.stream().map(fils::submit).toList();
			depart.countDown();
			fils.shutdown();
			assertThat(fils.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

			long accordees = resultats.stream().filter(f -> {
				try {
					return f.get();
				}
				catch (Exception echec) {
					throw new IllegalStateException(echec);
				}
			}).count();

			assertThat(accordees)
					.as("ni plus — le plafond serait percé — ni moins")
					.isEqualTo(PLAFOND);
		}
		finally {
			fils.shutdownNow();
		}

		ContexteTenant.executer(clinique, () ->
				assertThat(budget.consommeDuJour(clinique, jour)).isEqualTo(PLAFOND));
	}

	@Test
	@DisplayName("le compteur est par clinique et par jour")
	void leCompteurEstCloisonne() {
		UUID alpha = creerClinique("budget-alpha", "Alpha", 2, 180);
		UUID beta = creerClinique("budget-beta", "Beta", 2, 180);
		LocalDate jour = LocalDate.now();

		ContexteTenant.executer(alpha, () -> {
			assertThat(budget.reserver(alpha, jour, 1)).isTrue();
			assertThat(budget.reserver(alpha, jour, 1))
					.as("plafond atteint pour Alpha").isFalse();
		});

		ContexteTenant.executer(beta, () ->
				assertThat(budget.reserver(beta, jour, 1))
						.as("Beta a son propre budget").isTrue());

		ContexteTenant.executer(alpha, () ->
				assertThat(budget.reserver(alpha, jour.plusDays(1), 1))
						.as("et demain repart à zéro").isTrue());
	}
}
