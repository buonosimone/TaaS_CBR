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

public class GetHandler implements HttpHandler {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Regola REST: GET è solo per lettura
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            inviaRisposta(exchange, 405, generaMessaggio("Metodo non consentito"));
            return;
        }
        
        try {
            Map<String, String> params = estraiParametri(exchange.getRequestURI().getQuery());
            String risorsa = params.getOrDefault("risorsa", ""); // Es: "numeri", "stato", "vincite"
            String idTom = params.get("id_tombolata");

            if (idTom == null) {
                inviaRisposta(exchange, 400, generaMessaggio("Manca id_tombolata"));
                return;
            }

            Object datiResponse;
            switch (risorsa) {
                case "ultimo_numero": //
                    datiResponse = "Simulazione: Numero 42 (estratto alle 10:15)";
                    break;
                case "ultimi_cinque": //
                    datiResponse = new int[]{42, 12, 88, 5, 23};
                    break;
                case "tabellone": //
                    datiResponse = "Lista completa numeri estratti per la sessione " + idTom;
                    break;
                case "stato": //
                    datiResponse = "Stato attuale: ATTIVA";
                    break;
                default:
                    inviaRisposta(exchange, 404, generaMessaggio("Risorsa non trovata"));
                    return;
            }

            inviaRisposta(exchange, 200, gson.toJson(datiResponse));
            
        } catch (Exception e) {
            inviaRisposta(exchange, 500, generaMessaggio("Errore Server: " + e.getMessage()));
        }
    }

    private Map<String, String> estraiParametri(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {
            for (String p : query.split("&")) {
                String[] kv = p.split("=");
                if (kv.length == 2) map.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private void inviaRisposta(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, b.length);
        exchange.getResponseBody().write(b);
        exchange.getResponseBody().close();
    }

    private String generaMessaggio(String msg) {
        Map<String, String> m = new HashMap<>();
        m.put("status", msg);
        return gson.toJson(m);
    }
}