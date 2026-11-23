package ai.attenso.web;

public record EtatSuivi(String clinique, int numero, int position, String statut,
		long attenteMinMinutes, long attenteMaxMinutes, int seuilPrevenir) {
}
