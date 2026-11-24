package ai.attenso.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Export complet des données d'un établissement, en un clic (EF-904).
 *
 * <p><strong>C'est un argument de vente autant qu'une fonction.</strong> Le
 * §14 du cahier des charges le dit : la réticence d'une clinique face à un
 * éditeur seul se traite en montrant la porte de sortie. Un export qu'on
 * promet sans le montrer ne rassure personne ; celui-ci se télécharge devant
 * le client pendant la démonstration.
 *
 * <p>Une archive ZIP de CSV, et rien d'exotique : le gérant doit pouvoir
 * l'ouvrir dans un tableur sans nous. Même convention que l'export du pilote —
 * point-virgule et marque d'ordre des octets — parce qu'un fichier qui s'ouvre
 * en une seule colonne n'est pas un export, c'est une promesse non tenue.
 */
@Service
public class ExportComplet {

	/**
	 * Les tables exportées, et leur filtre.
	 *
	 * <p>Déclaratif plutôt que câblé : une table ajoutée au schéma et oubliée
	 * ici produirait un export silencieusement incomplet, c'est-à-dire le pire
	 * des cas. Un test compare cette liste aux tables réellement cloisonnées.
	 */
	public static final Map<String, String> TABLES = Map.of(
			"tickets", "SELECT * FROM ticket WHERE tenant_id = ? ORDER BY jour, numero_jour",
			"notifications", "SELECT * FROM notification WHERE tenant_id = ? ORDER BY cree_le",
			"journal-acces", "SELECT * FROM journal_acces WHERE tenant_id = ? ORDER BY horodatage",
			"commandes", "SELECT * FROM commande WHERE tenant_id = ? ORDER BY recu_le",
			"mesures-du-pilote", "SELECT * FROM mesure_jour WHERE tenant_id = ? ORDER BY jour",
			"motifs-de-priorite", "SELECT * FROM motif_priorite WHERE tenant_id = ? ORDER BY ordre",
			"abonnements", "SELECT * FROM abonnement WHERE tenant_id = ? ORDER BY debut",
			"factures", "SELECT * FROM facture WHERE tenant_id = ? ORDER BY periode",
			"utilisateurs", "SELECT id, tenant_id, email, role, actif, cree_le FROM utilisateur "
					+ "WHERE tenant_id = ? ORDER BY cree_le");

	private static final String BOM = "﻿";

	private final JdbcTemplate jdbc;

	public ExportComplet(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public byte[] pour(UUID tenantId, String slug, LocalDate jour) {
		ByteArrayOutputStream tampon = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(tampon, StandardCharsets.UTF_8)) {
			zip.putNextEntry(new ZipEntry("LISEZ-MOI.txt"));
			zip.write(lisezMoi(slug, jour).getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			for (Map.Entry<String, String> table : TABLES.entrySet()) {
				zip.putNextEntry(new ZipEntry(table.getKey() + ".csv"));
				zip.write(csv(table.getValue(), tenantId).getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		catch (IOException panne) {
			throw new UncheckedIOException(panne);
		}
		return tampon.toByteArray();
	}

	/**
	 * Les mots de passe ne sont JAMAIS exportés, pas même hachés.
	 *
	 * <p>Un export complet part par courriel, atterrit sur une clé USB, finit
	 * dans un dossier partagé. Une liste d'empreintes de mots de passe dans ce
	 * fichier serait une fuite à retardement — et la requête de la table
	 * {@code utilisateur} nomme donc ses colonnes au lieu de prendre
	 * {@code *}.
	 */
	private String csv(String requete, UUID tenantId) {
		StringBuilder csv = new StringBuilder(BOM);
		jdbc.query(requete, resultat -> {
			ResultSetMetaData meta = resultat.getMetaData();
			int colonnes = meta.getColumnCount();
			if (csv.length() == BOM.length()) {
				for (int c = 1; c <= colonnes; c++) {
					csv.append(c > 1 ? ";" : "").append(echapper(meta.getColumnLabel(c)));
				}
				csv.append("\r\n");
			}
			for (int c = 1; c <= colonnes; c++) {
				Object valeur = resultat.getObject(c);
				csv.append(c > 1 ? ";" : "")
						.append(valeur == null ? "" : echapper(String.valueOf(valeur)));
			}
			csv.append("\r\n");
		}, tenantId);
		return csv.toString();
	}

	private static String echapper(String cellule) {
		if (cellule.contains(";") || cellule.contains("\"") || cellule.contains("\n")) {
			return '"' + cellule.replace("\"", "\"\"") + '"';
		}
		return cellule;
	}

	private String lisezMoi(String slug, LocalDate jour) {
		return String.join("\n", List.of(
				"Export complet des données — " + slug,
				"Établi le " + jour,
				"",
				"Un fichier CSV par table, séparateur point-virgule, encodage UTF-8",
				"avec marque d'ordre des octets : ils s'ouvrent directement dans un",
				"tableur configuré en français.",
				"",
				"Ce que vous ne trouverez pas ici, et pourquoi :",
				"",
				"  - les empreintes de mots de passe. Un export circule ; une liste",
				"    d'empreintes dedans serait une fuite à retardement.",
				"  - les numéros de mobile des journées passées. Ils sont effacés le",
				"    lendemain de chaque visite, conformément à la minimisation RGPD.",
				"    Les numéros du jour, eux, sont présents.",
				"",
				"Ces données sont les vôtres. Vous pouvez les emporter chez un autre",
				"prestataire sans nous le demander, et sans nous prévenir.",
				""));
	}
}
