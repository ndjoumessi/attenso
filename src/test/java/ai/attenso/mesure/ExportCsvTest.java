package ai.attenso.mesure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** L'export qui part chez le gérant, donc dans un tableur français. */
class ExportCsvTest {

	private static final LocalDate JOUR = LocalDate.of(2026, 11, 24);

	@Test
	void commence_par_la_marque_d_ordre_des_octets() {
		// Sans elle, Excel lit l'UTF-8 comme du latin-1 et affiche « DÃ©parts ».
		assertThat(ExportCsv.depuis(List.of())).startsWith(ExportCsv.BOM);
	}

	@Test
	void separe_au_point_virgule_pour_un_tableur_francais() {
		// Avec des virgules, le fichier s'ouvre en une seule colonne et ne sera
		// pas lu.
		String csv = ExportCsv.depuis(List.of(new MesureJour(JOUR, 40, 20, 15, 3)));

		assertThat(csv).contains("2026-11-24;40;20;15;35;50;VALIDE;3");
	}

	@Test
	void laisse_vides_les_valeurs_absentes_au_lieu_de_les_mettre_a_zero() {
		String csv = ExportCsv.depuis(List.of(new MesureJour(JOUR, null, 7, 0, 1)));

		// Deux champs vides d'affilée : arrivants, puis taux.
		assertThat(csv).contains("2026-11-24;;7;0;7;;NON_MESURE;1");
	}

	@Test
	void porte_un_entete_nomme_meme_sans_aucune_ligne() {
		String csv = ExportCsv.depuis(List.of());

		assertThat(csv).contains("taux_auto_enregistrement_pct");
		assertThat(csv.lines()).hasSize(1);
	}

	@Test
	void termine_les_lignes_en_crlf_comme_attendu_par_excel() {
		assertThat(ExportCsv.depuis(List.of(new MesureJour(JOUR, 10, 5, 0, 0))))
				.endsWith("\r\n");
	}
}
