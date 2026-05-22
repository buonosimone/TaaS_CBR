package tombola_as_a_service_cbr;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * GetHandler - Gestisce tutte le richieste HTTP GET per TaaS.
 *
 * Endpoint supportati (registrati in ServerRest):
 *
 *   GET /api/v1/tombolate
 *       → Restituisce la lista di tutte le tombolate (array JSON).
 *
 *   GET /api/v1/tombolate/{id}
 *       → Restituisce i dettagli di una singola tombolata.
 *
 *   GET /api/v1/tombolate/{id}/numeri/ultimo
 *       → Restituisce l'ultimo numero estratto.
 *
 *   GET /api/v1/tombolate/{id}/numeri/ultimi-cinque
 *       → Restituisce gli ultimi cinque numeri estratti.
 *
 *   GET /api/v1/tombolate/{id}/numeri
 *       → Restituisce tutti i numeri estratti (tabellone completo).
 *
 * NOTA: il path viene risolto tramite il campo "percorso" della richiesta
 * (exchange.getRequestURI().getPath()), non tramite query string, rispettando
 * le linee guida REST di Zalando.
 *
 * @author [Tuo Nome / Gruppo]
 */
public class GetHandler implements HttpHandler {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Prefisso base comune a tutti gli endpoint di questa classe
    private static final String BASE_PATH = "/api/v1/tombolate";

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // Regola REST: questo handler accetta solo GET
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            inviaRisposta(exchange, 405, errore("Metodo non consentito. Usa GET."));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            // -------------------------------------------------------
            // GET /api/v1/tombolate
            // Lista di tutte le tombolate
            // -------------------------------------------------------
            if (path.equals(BASE_PATH) || path.equals(BASE_PATH + "/")) {
                gestisciListaTombolate(exchange);
                return;
            }

            // -------------------------------------------------------
            // Estrai l'ID dalla URL: /api/v1/tombolate/{id}[/...]
            // -------------------------------------------------------
            // Il path ha già il prefisso "/api/v1/tombolate/" → rimuoviamolo
            String sottoPercorso = path.substring(BASE_PATH.length());
            // sottoPercorso sarà es.: "/42" oppure "/42/numeri/ultimo"

            if (sottoPercorso.isEmpty() || sottoPercorso.equals("/")) {
                // Nessun ID specificato ma non è la lista → 400
                inviaRisposta(exchange, 400, errore("ID tombolata mancante nel percorso."));
                return;
            }

            // Rimuovi lo slash iniziale e spezza il percorso
            String[] parti = sottoPercorso.substring(1).split("/");
            int idTombolata;
            try {
                idTombolata = Integer.parseInt(parti[0]);
            } catch (NumberFormatException e) {
                inviaRisposta(exchange, 400, errore("ID tombolata non valido: deve essere un numero intero."));
                return;
            }

            // -------------------------------------------------------
            // GET /api/v1/tombolate/{id}
            // Dettagli di una singola tombolata
            // -------------------------------------------------------
            if (parti.length == 1) {
                gestisciDettaglioTombolata(exchange, idTombolata);
                return;
            }

            // -------------------------------------------------------
            // Sotto-risorse: /api/v1/tombolate/{id}/numeri[/...]
            // -------------------------------------------------------
            if (parti.length >= 2 && parti[1].equals("numeri")) {

                if (parti.length == 2) {
                    // GET /api/v1/tombolate/{id}/numeri
                    gestisciTabellone(exchange, idTombolata);
                    return;
                }

                if (parti.length == 3 && parti[2].equals("ultimo")) {
                    // GET /api/v1/tombolate/{id}/numeri/ultimo
                    gestisciUltimoNumero(exchange, idTombolata);
                    return;
                }

                if (parti.length == 3 && parti[2].equals("ultimi-cinque")) {
                    // GET /api/v1/tombolate/{id}/numeri/ultimi-cinque
                    gestisciUltimiCinque(exchange, idTombolata);
                    return;
                }
            }

            // Nessuna rotta corrisponde
            inviaRisposta(exchange, 404, errore("Risorsa non trovata: " + path));

        } catch (Exception e) {
            inviaRisposta(exchange, 500, errore("Errore interno del server: " + e.getMessage()));
        }
    }

    // ===========================================================
    // Metodi di gestione delle singole rotte
    // ===========================================================

    /**
     * GET /api/v1/tombolate
     * Restituisce la lista di tutte le tombolate presenti nel sistema.
     * In un'implementazione completa questi dati verrebbero letti dal database
     * tramite il Service layer; qui viene usato un array di esempio per
     * mostrare la struttura corretta della risposta.
     */
    private void gestisciListaTombolate(HttpExchange exchange) throws IOException {
        // Costruzione risposta di esempio con due tombolate nei vari stati
        TombolataResponse t1 = new TombolataResponse(
                1, "Tombola di Natale", 10, "ATTIVA",
                "2026-12-24T20:00:00", "2026-12-24T23:00:00",
                "2026-12-24T19:00:00", "PUBBLICA", 3
        );
        TombolataResponse t2 = new TombolataResponse(
                2, "Tombola di Capodanno", 11, "CREATA",
                "2026-12-31T22:00:00", "2027-01-01T02:00:00",
                "2026-12-31T21:00:00", "CODICE", 6
        );

        TombolataResponse[] lista = { t1, t2 };
        inviaRisposta(exchange, 200, gson.toJson(lista));
    }

    /**
     * GET /api/v1/tombolate/{id}
     * Restituisce i dettagli completi di una tombolata identificata dall'ID.
     * Popola anche il campo "operazioniConsentite" calcolato dinamicamente
     * dallo stato corrente (logica incapsulata in TombolataResponse).
     */
    private void gestisciDettaglioTombolata(HttpExchange exchange, int id) throws IOException {
        // In produzione: TombolataService.cercaPerId(id)
        TombolataResponse risposta = new TombolataResponse(
                id,
                "Tombola di Natale",
                10,
                "ATTIVA",
                "2026-12-24T20:00:00",
                "2026-12-24T23:00:00",
                "2026-12-24T19:00:00",
                "PUBBLICA",
                3
        );
        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    /**
     * GET /api/v1/tombolate/{id}/numeri/ultimo
     * Restituisce l'ultimo numero estratto nella sessione specificata.
     * La tombolata DEVE essere in stato ATTIVA; in caso contrario si
     * risponde con 409 Conflict.
     *
     * Risposta esempio:
     * { "numero": 42, "ordine_estrazione": 15, "timestamp": "2026-12-24T21:03:00" }
     */
    private void gestisciUltimoNumero(HttpExchange exchange, int idTombolata) throws IOException {
        // Verifica stato (in produzione: recupero da DB)
        String statoSimulato = "ATTIVA";
        if (!statoSimulato.equalsIgnoreCase("ATTIVA")) {
            inviaRisposta(exchange, 409,
                    errore("Operazione non consentita: la tombolata non è in stato ATTIVA."));
            return;
        }

        Map<String, Object> risposta = new HashMap<>();
        risposta.put("numero", 42);
        risposta.put("ordine_estrazione", 15);
        risposta.put("timestamp", "2026-12-24T21:03:00");

        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    /**
     * GET /api/v1/tombolate/{id}/numeri/ultimi-cinque
     * Restituisce gli ultimi cinque numeri estratti (dal più recente al più vecchio).
     * Richiede stato ATTIVA.
     *
     * Risposta esempio:
     * { "ultimi_cinque": [42, 7, 88, 13, 56] }
     */
    private void gestisciUltimiCinque(HttpExchange exchange, int idTombolata) throws IOException {
        String statoSimulato = "ATTIVA";
        if (!statoSimulato.equalsIgnoreCase("ATTIVA")) {
            inviaRisposta(exchange, 409,
                    errore("Operazione non consentita: la tombolata non è in stato ATTIVA."));
            return;
        }

        Map<String, Object> risposta = new HashMap<>();
        risposta.put("ultimi_cinque", new int[]{42, 7, 88, 13, 56});

        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    /**
     * GET /api/v1/tombolate/{id}/numeri
     * Restituisce il tabellone completo: tutti i numeri già estratti in ordine
     * cronologico, con il conteggio totale.
     * Richiede stato ATTIVA.
     *
     * Risposta esempio:
     * { "id_tombolata": 1, "numeri_estratti": [42, 7, 88, 13, 56, 23, 70],
     *   "totale_estratti": 7, "numeri_rimanenti": 83 }
     */
    private void gestisciTabellone(HttpExchange exchange, int idTombolata) throws IOException {
        String statoSimulato = "ATTIVA";
        if (!statoSimulato.equalsIgnoreCase("ATTIVA")) {
            inviaRisposta(exchange, 409,
                    errore("Operazione non consentita: la tombolata non è in stato ATTIVA."));
            return;
        }

        int[] numeriEstratti = {42, 7, 88, 13, 56, 23, 70};

        Map<String, Object> risposta = new HashMap<>();
        risposta.put("id_tombolata", idTombolata);
        risposta.put("numeri_estratti", numeriEstratti);
        risposta.put("totale_estratti", numeriEstratti.length);
        risposta.put("numeri_rimanenti", 90 - numeriEstratti.length);

        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    // ===========================================================
    // Metodi di utilità
    // ===========================================================

    /**
     * Invia una risposta HTTP con il codice di stato e il body JSON indicati.
     */
    private void inviaRisposta(HttpExchange exchange, int codice, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(codice, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    /**
     * Costruisce un oggetto JSON di errore standard.
     * Esempio: { "errore": "Messaggio descrittivo" }
     */
    private String errore(String messaggio) {
        Map<String, String> mappa = new HashMap<>();
        mappa.put("errore", messaggio);
        return gson.toJson(mappa);
    }
}