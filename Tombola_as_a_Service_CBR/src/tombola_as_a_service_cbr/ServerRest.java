package serverrest;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Server REST per la calcolatrice - Versione V1 con Legacy
 * 
 * @author delfo
 */
public class ServerRest {

    /**
     * Avvia il server REST sulla porta specificata
     */
    public static void avviaServer(int porta) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);

            // =======================
            // === API V1 ============
            // =======================
            server.createContext("/api/v1/calcola/get", new GetHandlerV1());
            server.createContext("/api/v1/calcola/post", new PostHandlerV1());

            // =======================
            // === LEGACY → V1 =======
            // =======================
            server.createContext("/api/calcola/get", new GetHandlerV1());
            server.createContext("/api/calcola/post", new PostHandlerV1());

            // Endpoint di benvenuto
            server.createContext("/", ServerRest::gestisciBenvenuto);

            server.setExecutor(null);
            server.start();

            System.out.println("==============================================");
            System.out.println("  Server REST Calcolatrice - API V1");
            System.out.println("==============================================");
            System.out.println("Porta: " + porta);
            System.out.println();
            System.out.println("Endpoint disponibili:");
            System.out.println("  - V1 GET :  http://localhost:" + porta + "/api/v1/calcola/get");
            System.out.println("  - V1 POST:  http://localhost:" + porta + "/api/v1/calcola/post");
            System.out.println();

        } catch (IOException e) {
            System.err.println("Errore nell'avvio del server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Endpoint di benvenuto con info API
     */
    private static void gestisciBenvenuto(HttpExchange exchange) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Map<String, Object> info = new HashMap<>();
        info.put("messaggio", "Benvenuto alla Calcolatrice REST API");
        info.put("versione_api", "v1");
        info.put("tecnologia", "Java + GSON");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("get", "/api/v1/calcola/get?operando1=X&operando2=Y&operatore=OP");
        endpoints.put("post", "/api/v1/calcola/post");
        info.put("endpoints", endpoints);

        Map<String, String> operatori = new HashMap<>();
        operatori.put("supportati", "SOMMA, SOTTRAZIONE, MOLTIPLICAZIONE, DIVISIONE");
        info.put("operatori_supportati", operatori);

        String json = gson.toJson(info);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }
}
