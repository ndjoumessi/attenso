package ai.attenso.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.amorce.AmorceClinique;
import ai.attenso.priorite.MotifDao;
import ai.attenso.priorite.MotifPriorite;
import ai.attenso.tenant.ContexteTenant;
import ai.attenso.securite.GenerateurReference;
import ai.attenso.amorce.ProprietesAmorce;
import ai.attenso.tenant.ServiceTenant;
import ai.attenso.tenant.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * La clinique amorcée montre une priorité <em>graduée</em>, pas deux motifs au
 * même rang : c'est ce que voit une clinique pendant la démonstration.
 */
class AmorceMotifsDemoIntegrationTest extends SocleIntegration {

	@Autowired
	TenantRepository tenants;

	@Autowired
	GenerateurReference jetons;

	private AmorceClinique amorce;

	@Autowired
	ServiceTenant cliniques;

	@Autowired
	MotifDao motifs;

	@Autowired
	ai.attenso.agenda.AgendaDao agenda;

	@Autowired
	ai.attenso.carnet.CarnetDao carnets;

	@Autowired
	java.time.Clock horloge;

	@BeforeEach
	void construireLAmorce() {
		// Construite à la main plutôt que par une propriété d'activation : un
		// contexte Spring de plus aurait son propre pool et épuiserait les
		// connexions de PostgreSQL pour les classes qui suivent.
		ProprietesAmorce proprietes = new ProprietesAmorce(true, "demo",
				"Clinique de démonstration", "accueil@demo.attenso.test",
				"mdp-de-demonstration-de-test", null, null, null, false);
		this.amorce = new AmorceClinique(proprietes, tenants, applicatif, motsDePasse, jetons,
				motifs, agenda, carnets, horloge);
	}

	private List<MotifPriorite> motifsDeLaDemo() {
		amorce.run(new DefaultApplicationArguments(new String[0]));
		UUID id = cliniques.parSlug("demo").id();
		return ContexteTenant.avec(id, () -> motifs.tous(id));
	}

	@Test
	@DisplayName("la clinique de démonstration porte six motifs, à des rangs gradués dans la bande")
	void sixMotifsGradues() {
		List<MotifPriorite> tous = motifsDeLaDemo();

		assertThat(tous).extracting(MotifPriorite::libelle).containsExactly(
				"Personne âgée", "Personne handicapée",
				"Animal potentiellement contagieux", "Chat en caisse depuis plus de 30 min",
				"Animal de ferme en bétaillère", "Contrôle post-opératoire court");
		assertThat(tous).extracting(MotifPriorite::rang).allMatch(r -> r >= 1 && r <= 9);
		assertThat(tous).extracting(MotifPriorite::rang).containsExactly(
				(short) 5, (short) 5, (short) 8, (short) 7, (short) 6, (short) 3);
		// Le gradient est le point : au moins quatre niveaux distincts.
		assertThat(tous.stream().map(MotifPriorite::rang).distinct().count()).isGreaterThan(3);
		assertThat(tous).extracting(MotifPriorite::ordre).containsExactly(
				(short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6);
	}

	@Test
	@DisplayName("relancer l'amorce ne sème pas les motifs deux fois")
	void idempotente() {
		motifsDeLaDemo();
		amorce.run(new DefaultApplicationArguments(new String[0]));

		UUID id = cliniques.parSlug("demo").id();
		assertThat(ContexteTenant.avec(id, () -> motifs.tous(id))).hasSize(6);
	}
}
