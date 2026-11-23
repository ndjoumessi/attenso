package ai.attenso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param periodeTick          période du tick de grâce, en millisecondes.
 * @param ticketsMaxParNumero  deux : une famille avec deux animaux en a besoin,
 *                             au-delà c'est du bruit.
 * @param ticketsMaxParJour    plafond par clinique et par jour. Ce n'est pas
 *                             une limite métier — une clinique en fait
 *                             quarante — mais un <strong>plafond de
 *                             dépense</strong> : chaque ticket déclenche un
 *                             SMS payant, et l'agrégateur n'offre aucun
 *                             plafond. Sans ça, un script qui boucle sur
 *                             l'enregistrement se traduit par une facture.
 */
@ConfigurationProperties("attenso.file")
public record ProprietesFile(long periodeTick, int ticketsMaxParNumero, int ticketsMaxParJour) {
}
