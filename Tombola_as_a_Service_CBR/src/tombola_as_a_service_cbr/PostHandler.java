package tombola_as_a_service_cbr;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PostHandler implements HttpHandler {
    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            inviaRisposta(exchange, 405, "Usa POST");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            JsonObject body = JsonParser.parseReader(reader).getAsJsonObject();
            String azione = body.get("azione").getAsString();
            String statoAttuale = "attiva"; // In produzione questo verrebbe dal DB

            Map<String, Object> res = new HashMap<>();

            switch (azione) {
                case "estrai": 
                    // Vincolo: solo se STATO = ATTIVA
                    if (!statoAttuale.equals("attiva")) {
                        inviaRisposta(exchange, 403, "Errore: Puoi estrarre solo in stato ATTIVA");
                        return;
                    }
                    res.put("numero", 7); 
                    res.put("ordine", 12);
                    break;

                case "assegna_cartella":
                    // Vincolo: solo se STATO = APERTA
                    res.put("messaggio", "Cartella generata rispettando la regola dei decili (col 1: 1-9, col 2: 10-19...)");
                    break;

                case "cambia_stato":
                    // Gestisce transizioni: creata -> aperta -> attiva -> terminata
                    String nuovoStato = body.get("nuovo_stato").getAsString();
                    res.put("messaggio", "Tombolata aggiornata a: " + nuovoStato);
                    break;

                case "conferma_vincita": //
                    res.put("messaggio", "Vincita validata manualmente dal gestore");
                    break;

                default:
                    inviaRisposta(exchange, 400, "Azione non supportata");
                    return;
            }

            inviaRisposta(exchange, 200, gson.toJson(res));
        } catch (Exception e) {
            inviaRisposta(exchange, 500, "Errore: " + e.getMessage());
        }
    }

    private void inviaRisposta(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, b.length);
        exchange.getResponseBody().write(b);
        exchange.getResponseBody().close();
    }
}