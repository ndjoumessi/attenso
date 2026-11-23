package ai.attenso.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Au démarrage, nomme les cliniques qui n'enverront aucun SMS.
 *
 * <p>Le pire scénario de ce produit n'est pas une panne bruyante : c'est une
 * clinique qui tourne une journée entière en croyant que ses clients sont
 * prévenus. Deux lignes de journal au démarrage coûtent moins cher qu'une
 * demi-journée de pilote perdue.
 */
@Component
public class ControleExpediteurs implements ApplicationRunner {

	private static final Logger JOURNAL = LoggerFactory.getLogger(ControleExpediteurs.class);

	private final TenantRepository tenants;

	public ControleExpediteurs(TenantRepository tenants) {
		this.tenants = tenants;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		tenants.findAllByActifTrue().stream()
				.filter(clinique -> !clinique.peutEnvoyerDesSms())
				.forEach(clinique -> JOURNAL.warn(
						"clinique « {} » sans expéditeur SMS enregistré : AUCUN SMS ne partira. "
								+ "La file, l'écran et la page de suivi fonctionnent. "
								+ "Proposition de nom à déposer auprès des opérateurs : « {} »",
						clinique.slug(),
						NomExpediteur.proposerDepuis(clinique.nom())));
	}
}
