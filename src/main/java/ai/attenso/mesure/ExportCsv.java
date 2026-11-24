package ai.attenso.mesure;

import java.util.List;

/**
 * Export CSV des indicateurs du pilote (EF-703, critère de recette R9).
 *
 * <p>Deux détails qui n'en sont pas. Le séparateur est le point-virgule et non
 * la virgule : c'est ce qu'attend un tableur configuré en français, et un
 * fichier qui s'ouvre en une seule colonne ne sera pas lu. Et le fichier
 * commence par une marque d'ordre des octets, sans quoi Excel lit l'UTF-8
 * comme du latin-1 et affiche « DÃ©parts ».
 */
public final class ExportCsv {

	/** Marque d'ordre des octets : c'est elle qui fait lire l'UTF-8 à Excel. */
	public static final String BOM = "﻿";

	private static final String SEPARATEUR = ";";

	private static final String FIN_DE_LIGNE = "\r\n";

	private ExportCsv() {
	}

	public static String depuis(List<MesureJour> mesures) {
		StringBuilder csv = new StringBuilder(BOM);
		ligne(csv, "jour", "arrivants_total", "tickets_comptoir", "tickets_accueil",
				"tickets_total", "taux_auto_enregistrement_pct", "zone", "departs_sans_consultation");
		for (MesureJour mesure : mesures) {
			ligne(csv,
					mesure.jour().toString(),
					nombre(mesure.arrivantsTotal()),
					String.valueOf(mesure.ticketsComptoir()),
					String.valueOf(mesure.ticketsAccueil()),
					String.valueOf(mesure.ticketsTotal()),
					nombre(mesure.tauxAutoEnregistrement()),
					mesure.zone(),
					String.valueOf(mesure.departs()));
		}
		return csv.toString();
	}

	/**
	 * Une valeur absente reste VIDE et ne devient pas zéro : un jour sans
	 * saisie d'arrivants n'est pas un jour à 0 % d'auto-enregistrement, et
	 * confondre les deux fausserait la décision de la semaine 10.
	 */
	private static String nombre(Integer valeur) {
		return valeur == null ? "" : valeur.toString();
	}

	private static void ligne(StringBuilder csv, String... cellules) {
		for (int i = 0; i < cellules.length; i++) {
			if (i > 0) {
				csv.append(SEPARATEUR);
			}
			csv.append(echapper(cellules[i]));
		}
		csv.append(FIN_DE_LIGNE);
	}

	private static String echapper(String cellule) {
		if (cellule.contains(SEPARATEUR) || cellule.contains("\"") || cellule.contains("\n")) {
			return '"' + cellule.replace("\"", "\"\"") + '"';
		}
		return cellule;
	}
}
