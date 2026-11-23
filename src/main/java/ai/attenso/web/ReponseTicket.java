package ai.attenso.web;

public record ReponseTicket(int numero, String reference, String lien, int position,
		long attenteMinMinutes, long attenteMaxMinutes) {
}
