package ai.attenso.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param arrivants nombre de personnes entrées dans la clinique aujourd'hui,
 *        tickets ou non. Plafonné à 2000 : au-delà, c'est une faute de frappe,
 *        et un dénominateur absurde ruine la mesure sans qu'on s'en aperçoive.
 */
public record DemandeArrivants(@Min(0) @Max(2000) int arrivants) {
}
