package tombola_as_a_service_cbr;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * PostHandler - Gestisce tutte le richieste HTTP POST per TaaS.
 *
 * Endpoint supportati (registrati in ServerRest):
 *
 *   POST /api/v1/tombolate
 *       → Crea una nuova tombolata. Body: TombolataRequest (JSON).
 *         Risposta: TombolataResponse con stato iniziale "CREATA" (201 Created).
 *
 *   POST /api/v1/tombolate/{id}/stato
 *       → Cambia lo stato della tombolata seguendo la macchina a stati:
 *         CREATA → APERTA → ATTIVA → TERMINATA.
 *         Body: { "nuovo_stato": "APERTA" }
 *
 *   POST /api/v1/tombolate/{id}/numeri
 *       → Estrae un numero casuale (1–90) non ancora estratto.
 *         Consentita solo in stato ATTIVA.
 *         Verifica automaticamente le vincite (ambo, terno, quaterna,
 *         cinquina, tombola) dopo ogni estrazione.
 *
 *   POST /api/v1/tombolate/{id}/cartelle
 *       → Crea e assegna una cartella a un utente.
 *         Body: { "id_utente": 5 }   (modalità automatica)
 *         Consentita solo in stato APERTA.
 *
 *   POST /api/v1/tombolate/{id}/vincite/conferma
 *       → Conferma manuale di una vincita da parte del gestore.
 *         Body: { "id_utente": 5, "tipo_vincita": "TOMBOLA" }
 *
 * Struttura del body per la creazione (TombolataRequest):
 * {
 *   "nome":                         "Tombola di Natale",
 *   "idGestore":                    "10",
 *   "dataInizio":                   "2026-12-24T20:00:00",
 *   "dataFine":                     "2026-12-24T23:00:00",
 *   "dataFineAssegnazioneCartelle": "2026-12-24T19:00:00",
 *   "modalitaAutenticazione":       "PUBBLICA",
 *   "maxCartellePerUtente":         "3"
 * }
 *
 * @author [Tuo Nome / Gruppo]
 */
public class PostHandler implements HttpHandler {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Random random = new Random();

    // Prefisso base comune a tutti gli endpoint di questa classe
    private static final String BASE_PATH = "/api/v1/tombolate";

    // Stati validi e loro ordine nella macchina a stati
    private static final String[] STATI_ORDINATI = {"CREATA", "APERTA", "ATTIVA", "TERMINATA"};

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // Regola REST: questo handler accetta solo POST
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            inviaRisposta(exchange, 405, errore("Metodo non consentito. Usa POST."));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        try {
            // Leggi il body della richiesta (potrebbe essere vuoto per alcune rotte)
            String bodyRaw = leggiBody(exchange);

            // -------------------------------------------------------
            // POST /api/v1/tombolate
            // Crea una nuova tombolata
            // -------------------------------------------------------
            if (path.equals(BASE_PATH) || path.equals(BASE_PATH + "/")) {
                gestisciCreazioneTobolata(exchange, bodyRaw);
                return;
            }

            // -------------------------------------------------------
            // Estrai l'ID dalla URL: /api/v1/tombolate/{id}[/...]
            // -------------------------------------------------------
            String sottoPercorso = path.substring(BASE_PATH.length());
            if (sottoPercorso.isEmpty() || sottoPercorso.equals("/")) {
                inviaRisposta(exchange, 400, errore("ID tombolata mancante nel percorso."));
                return;
            }

            String[] parti = sottoPercorso.substring(1).split("/");
            int idTombolata;
            try {
                idTombolata = Integer.parseInt(parti[0]);
            } catch (NumberFormatException e) {
                inviaRisposta(exchange, 400, errore("ID tombolata non valido: deve essere un numero intero."));
                return;
            }

            if (parti.length < 2) {
                inviaRisposta(exchange, 404, errore("Sotto-risorsa mancante. Consulta la documentazione API."));
                return;
            }

            String sottoRisorsa = parti[1];

            // -------------------------------------------------------
            // POST /api/v1/tombolate/{id}/stato
            // Cambio di stato della tombolata
            // -------------------------------------------------------
            if (sottoRisorsa.equals("stato")) {
                gestisciCambioStato(exchange, idTombolata, bodyRaw);
                return;
            }

            // -------------------------------------------------------
            // POST /api/v1/tombolate/{id}/numeri
            // Estrazione di un numero
            // -------------------------------------------------------
            if (sottoRisorsa.equals("numeri")) {
                gestisciEstrazione(exchange, idTombolata);
                return;
            }

            // -------------------------------------------------------
            // POST /api/v1/tombolate/{id}/cartelle
            // Assegnazione cartella a un utente
            // -------------------------------------------------------
            if (sottoRisorsa.equals("cartelle")) {
                gestisciAssegnazioneCartella(exchange, idTombolata, bodyRaw);
                return;
            }

            // -------------------------------------------------------
            // POST /api/v1/tombolate/{id}/vincite/conferma
            // Conferma manuale di una vincita da parte del gestore
            // -------------------------------------------------------
            if (sottoRisorsa.equals("vincite") && parti.length >= 3 && parti[2].equals("conferma")) {
                gestisciConfermaVincita(exchange, idTombolata, bodyRaw);
                return;
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
     * POST /api/v1/tombolate
     * Crea una nuova tombolata deserializzando il body in TombolataRequest.
     * Restituisce un TombolataResponse con stato iniziale "CREATA" e HTTP 201.
     *
     * Validazioni:
     *  - nome non nullo/vuoto
     *  - idGestore non nullo/vuoto
     *  - dataInizio non nulla
     *  - modalitaAutenticazione in {PUBBLICA, CODICE, LISTA}
     */
    private void gestisciCreazioneTobolata(HttpExchange exchange, String body) throws IOException {
        if (body == null || body.isBlank()) {
            inviaRisposta(exchange, 400, errore("Body della richiesta vuoto o mancante."));
            return;
        }

        TombolataRequest req = gson.fromJson(body, TombolataRequest.class);

        // Validazioni obbligatorie
        if (req.getNome() == null || req.getNome().isBlank()) {
            inviaRisposta(exchange, 400, errore("Campo obbligatorio mancante: 'nome'."));
            return;
        }
        if (req.getIdGestore() == null || req.getIdGestore().isBlank()) {
            inviaRisposta(exchange, 400, errore("Campo obbligatorio mancante: 'idGestore'."));
            return;
        }
        if (req.getDataInizio() == null || req.getDataInizio().isBlank()) {
            inviaRisposta(exchange, 400, errore("Campo obbligatorio mancante: 'dataInizio'."));
            return;
        }
        if (req.getModalitaAutenticazione() == null
                || !isModalitaValida(req.getModalitaAutenticazione())) {
            inviaRisposta(exchange, 400,
                    errore("'modalitaAutenticazione' non valida. Valori ammessi: PUBBLICA, CODICE, LISTA."));
            return;
        }

        // In produzione: idGestore verrebbe risolto tramite DB; usiamo parseInt con fallback
        int idGestore;
        try {
            idGestore = Integer.parseInt(req.getIdGestore());
        } catch (NumberFormatException e) {
            inviaRisposta(exchange, 400, errore("'idGestore' deve essere un numero intero."));
            return;
        }

        int maxCartelle = 6; // default
        if (req.getMaxCartellePerUtente() != null && !req.getMaxCartellePerUtente().isBlank()) {
            try {
                maxCartelle = Integer.parseInt(req.getMaxCartellePerUtente());
            } catch (NumberFormatException e) {
                inviaRisposta(exchange, 400, errore("'maxCartellePerUtente' deve essere un numero intero."));
                return;
            }
        }

        // In produzione l'ID verrebbe generato dal DB (auto-increment)
        int nuovoId = 100; // ID simulato

        TombolataResponse risposta = new TombolataResponse(
                nuovoId,
                req.getNome(),
                idGestore,
                "CREATA",           // stato iniziale sempre CREATA
                req.getDataInizio(),
                req.getDataFine(),
                req.getDataFineAssegnazioneCartelle(),
                req.getModalitaAutenticazione(),
                maxCartelle
        );

        // HTTP 201 Created per la creazione di una nuova risorsa (Zalando guideline)
        inviaRisposta(exchange, 201, gson.toJson(risposta));
    }

    /**
     * POST /api/v1/tombolate/{id}/stato
     * Cambia lo stato di una tombolata rispettando la macchina a stati:
     *   CREATA → APERTA → ATTIVA → TERMINATA
     *
     * Transizioni non sequenziali vengono rifiutate con 409 Conflict.
     *
     * Body atteso: { "nuovo_stato": "APERTA" }
     */
    private void gestisciCambioStato(HttpExchange exchange, int id, String body) throws IOException {
        if (body == null || body.isBlank()) {
            inviaRisposta(exchange, 400, errore("Body mancante. Fornisci: { \"nuovo_stato\": \"APERTA\" }"));
            return;
        }

        // Deserializza il body in una mappa generica per leggere "nuovo_stato"
        @SuppressWarnings("unchecked")
        Map<String, String> bodyMap = gson.fromJson(body, Map.class);
        String nuovoStato = bodyMap.get("nuovo_stato");

        if (nuovoStato == null || nuovoStato.isBlank()) {
            inviaRisposta(exchange, 400, errore("Campo 'nuovo_stato' mancante nel body."));
            return;
        }

        nuovoStato = nuovoStato.toUpperCase().trim();

        // Stato corrente simulato (in produzione: recupero da DB)
        String statoCorrente = "CREATA";

        // Verifica che la transizione sia consentita (solo avanzamento di un passo)
        if (!transazioneConsentita(statoCorrente, nuovoStato)) {
            inviaRisposta(exchange, 409,
                    errore("Transizione non consentita: " + statoCorrente + " → " + nuovoStato
                           + ". Sequenza valida: CREATA → APERTA → ATTIVA → TERMINATA."));
            return;
        }

        // In produzione: aggiorna il DB e ricarica l'oggetto aggiornato
        TombolataResponse risposta = new TombolataResponse(
                id,
                "Tombola di Natale",
                10,
                nuovoStato,
                "2026-12-24T20:00:00",
                "2026-12-24T23:00:00",
                "2026-12-24T19:00:00",
                "PUBBLICA",
                3
        );

        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    /**
     * POST /api/v1/tombolate/{id}/numeri
     * Estrae casualmente un numero tra 1 e 90 non ancora estratto.
     * Consentita SOLO in stato ATTIVA; restituisce 409 altrimenti.
     *
     * Dopo l'estrazione controlla automaticamente le vincite:
     *  ambo (2 numeri sulla stessa riga), terno (3), quaterna (4),
     *  cinquina (5), tombola (tutti i 15 numeri di una cartella).
     *
     * Risposta:
     * {
     *   "numero_estratto": 42,
     *   "ordine_estrazione": 16,
     *   "vincite_rilevate": ["AMBO", "TERNO"]
     * }
     */
    private void gestisciEstrazione(HttpExchange exchange, int idTombolata) throws IOException {
        // Stato simulato (in produzione: recupero da DB)
        String statoCorrente = "ATTIVA";

        if (!statoCorrente.equalsIgnoreCase("ATTIVA")) {
            inviaRisposta(exchange, 409,
                    errore("Estrazione non consentita: la tombolata deve essere in stato ATTIVA."
                           + " Stato attuale: " + statoCorrente));
            return;
        }

        // In produzione: leggi dal DB i numeri già estratti e scegli tra quelli rimanenti
        // Qui simuliamo: numeri già estratti = {42, 7, 88, 13, 56, 23, 70}
        java.util.Set<Integer> estratti = new java.util.HashSet<>(
                java.util.Arrays.asList(42, 7, 88, 13, 56, 23, 70)
        );

        if (estratti.size() >= 90) {
            inviaRisposta(exchange, 409, errore("Tutti i 90 numeri sono già stati estratti."));
            return;
        }

        // Estrai un numero non ancora uscito
        int numero;
        do {
            numero = random.nextInt(90) + 1; // 1-90 inclusi
        } while (estratti.contains(numero));

        estratti.add(numero);
        int ordine = estratti.size();

        // Controllo automatico delle vincite (logica semplificata di esempio)
        java.util.List<String> vincite = controllaVincite(numero, estratti);

        Map<String, Object> risposta = new HashMap<>();
        risposta.put("numero_estratto", numero);
        risposta.put("ordine_estrazione", ordine);
        risposta.put("vincite_rilevate", vincite);

        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    /**
     * POST /api/v1/tombolate/{id}/cartelle
     * Crea e assegna automaticamente una cartella a un utente.
     * Consentita SOLO in stato APERTA.
     *
     * Regola delle cartelle (tombola italiana):
     *  - 3 righe × 9 colonne, con 5 numeri per riga (15 totali)
     *  - Colonna 1: numeri 1–9
     *  - Colonna 2: numeri 10–19
     *  - ... fino a Colonna 9: numeri 80–90
     *
     * Body atteso: { "id_utente": 5 }
     *
     * Risposta:
     * {
     *   "id_cartella": 201,
     *   "id_utente": 5,
     *   "id_tombolata": 1,
     *   "righe": [[...], [...], [...]]
     * }
     */
    private void gestisciAssegnazioneCartella(HttpExchange exchange, int idTombolata, String body)
            throws IOException {

        // Stato simulato
        String statoCorrente = "APERTA";

        if (!statoCorrente.equalsIgnoreCase("APERTA")) {
            inviaRisposta(exchange, 409,
                    errore("Assegnazione cartella non consentita: la tombolata deve essere in stato APERTA."
                           + " Stato attuale: " + statoCorrente));
            return;
        }

        if (body == null || body.isBlank()) {
            inviaRisposta(exchange, 400, errore("Body mancante. Fornisci: { \"id_utente\": 5 }"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = gson.fromJson(body, Map.class);
        if (!bodyMap.containsKey("id_utente")) {
            inviaRisposta(exchange, 400, errore("Campo 'id_utente' mancante nel body."));
            return;
        }

        int idUtente;
        try {
            // Gson deserializza i numeri come Double nelle mappe generiche
            idUtente = ((Number) bodyMap.get("id_utente")).intValue();
        } catch (Exception e) {
            inviaRisposta(exchange, 400, errore("'id_utente' deve essere un numero intero."));
            return;
        }

        // Genera la cartella rispettando la regola dei decili
        int[][] cartella = generaCartella();

        Map<String, Object> risposta = new HashMap<>();
        risposta.put("id_cartella", 201);          // In produzione: ID generato dal DB
        risposta.put("id_utente", idUtente);
        risposta.put("id_tombolata", idTombolata);
        risposta.put("righe", cartella);

        inviaRisposta(exchange, 201, gson.toJson(risposta));
    }

    /**
     * POST /api/v1/tombolate/{id}/vincite/conferma
     * Permette al gestore di confermare manualmente una vincita.
     *
     * Body atteso:
     * { "id_utente": 5, "tipo_vincita": "TOMBOLA" }
     *
     * Tipi di vincita validi: AMBO, TERNO, QUATERNA, CINQUINA, TOMBOLA
     */
    private void gestisciConfermaVincita(HttpExchange exchange, int idTombolata, String body)
            throws IOException {

        if (body == null || body.isBlank()) {
            inviaRisposta(exchange, 400,
                    errore("Body mancante. Fornisci: { \"id_utente\": 5, \"tipo_vincita\": \"TOMBOLA\" }"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = gson.fromJson(body, Map.class);

        if (!bodyMap.containsKey("id_utente") || !bodyMap.containsKey("tipo_vincita")) {
            inviaRisposta(exchange, 400, errore("Campi obbligatori mancanti: 'id_utente' e 'tipo_vincita'."));
            return;
        }

        int idUtente;
        try {
            idUtente = ((Number) bodyMap.get("id_utente")).intValue();
        } catch (Exception e) {
            inviaRisposta(exchange, 400, errore("'id_utente' deve essere un numero intero."));
            return;
        }

        String tipoVincita = ((String) bodyMap.get("tipo_vincita")).toUpperCase().trim();
        if (!isTipoVincitaValido(tipoVincita)) {
            inviaRisposta(exchange, 400,
                    errore("'tipo_vincita' non valido. Valori ammessi: AMBO, TERNO, QUATERNA, CINQUINA, TOMBOLA."));
            return;
        }

        // In produzione: aggiorna il DB, imposta vincita confermata
        Map<String, Object> risposta = new HashMap<>();
        risposta.put("id_tombolata", idTombolata);
        risposta.put("id_utente", idUtente);
        risposta.put("tipo_vincita", tipoVincita);
        risposta.put("stato_conferma", "CONFERMATA");
        risposta.put("messaggio", "Vincita " + tipoVincita + " confermata dal gestore per l'utente " + idUtente + ".");

        inviaRisposta(exchange, 200, gson.toJson(risposta));
    }

    // ===========================================================
    // Logica di business (tombola)
    // ===========================================================

    /**
     * Controlla se ci sono nuove vincite dopo l'estrazione di "numero".
     * Logica semplificata: verifica quanti numeri di una cartella di
     * esempio sono stati estratti.
     *
     * In produzione: iterare su tutte le cartelle delle tombolata,
     * contare i numeri presenti in ogni riga e segnalare le soglie raggiunte.
     */
    private java.util.List<String> controllaVincite(int ultimoNumero,
            java.util.Set<Integer> tuttiEstratti) {

        java.util.List<String> vincite = new java.util.ArrayList<>();

        // Cartella di esempio per la verifica (in produzione: tutte le cartelle dal DB)
        int[][] cartellaEsempio = {
            {5, 0, 23, 0, 42, 0, 70, 0, 88},   // riga 0
            {0, 13, 0, 34, 0, 56, 0, 77, 0},    // riga 1
            {7, 0, 29, 0, 0, 61, 0, 82, 90}     // riga 2
        };

        for (int[] riga : cartellaEsempio) {
            int trovati = 0;
            int celleValide = 0;
            for (int n : riga) {
                if (n != 0) {
                    celleValide++;
                    if (tuttiEstratti.contains(n)) trovati++;
                }
            }
            // Una riga ha esattamente 5 numeri validi
            if (celleValide == 5) {
                switch (trovati) {
                    case 2: vincite.add("AMBO");     break;
                    case 3: vincite.add("TERNO");    break;
                    case 4: vincite.add("QUATERNA"); break;
                    case 5: vincite.add("CINQUINA"); break;
                }
            }
        }

        // Verifica TOMBOLA: tutti i 15 numeri della cartella estratti
        int totaleTrovati = 0;
        for (int[] riga : cartellaEsempio) {
            for (int n : riga) {
                if (n != 0 && tuttiEstratti.contains(n)) totaleTrovati++;
            }
        }
        if (totaleTrovati == 15) {
            vincite.add("TOMBOLA");
        }

        return vincite;
    }

    /**
     * Genera una cartella della tombola italiana rispettando la regola dei decili:
     *  - Colonna 1 (indice 0): numeri scelti da 1–9
     *  - Colonna 2 (indice 1): numeri scelti da 10–19
     *  - ...
     *  - Colonna 9 (indice 8): numeri scelti da 80–90 (11 valori)
     *
     * Struttura: 3 righe × 9 colonne, con esattamente 5 numeri per riga (15 totali).
     * Le celle vuote vengono rappresentate con il valore 0.
     */
    private int[][] generaCartella() {
        int[][] cartella = new int[3][9];

        // Per ogni colonna scegli i numeri del decile corrispondente
        for (int col = 0; col < 9; col++) {
            int min = col == 0 ? 1 : col * 10;
            int max = col == 8 ? 90 : col * 10 + 9;
            int range = max - min + 1;

            // Quanti numeri inserire in questa colonna (0, 1 o 2)
            // Distribuiamo per avere esattamente 15 numeri totali
            // Scegliamo casualmente quante celle riempire: la distribuzione
            // esatta è gestita in una implementazione completa con backtracking;
            // qui usiamo una distribuzione semplificata
            int numeriInColonna = random.nextInt(2) + 1; // 1 o 2 per colonna

            java.util.List<Integer> righeDisponibili = new java.util.ArrayList<>();
            righeDisponibili.add(0); righeDisponibili.add(1); righeDisponibili.add(2);
            java.util.Collections.shuffle(righeDisponibili, random);

            java.util.Set<Integer> numerUsati = new java.util.HashSet<>();
            for (int i = 0; i < numeriInColonna && i < righeDisponibili.size(); i++) {
                int riga = righeDisponibili.get(i);
                int num;
                do {
                    num = min + random.nextInt(range);
                } while (numerUsati.contains(num));
                numerUsati.add(num);
                cartella[riga][col] = num;
            }
        }

        return cartella;
    }

    /**
     * Verifica che la transizione di stato sia consentita.
     * La macchina a stati ammette solo avanzamenti sequenziali di un passo.
     */
    private boolean transazioneConsentita(String statoAttuale, String nuovoStato) {
        int idxAttuale = -1, idxNuovo = -1;
        for (int i = 0; i < STATI_ORDINATI.length; i++) {
            if (STATI_ORDINATI[i].equalsIgnoreCase(statoAttuale)) idxAttuale = i;
            if (STATI_ORDINATI[i].equalsIgnoreCase(nuovoStato))   idxNuovo  = i;
        }
        // Consentito solo il passo successivo immediato
        return idxAttuale >= 0 && idxNuovo == idxAttuale + 1;
    }

    /** Verifica che la modalità di autenticazione sia tra i valori ammessi. */
    private boolean isModalitaValida(String modalita) {
        String m = modalita.toUpperCase().trim();
        return m.equals("PUBBLICA") || m.equals("CODICE") || m.equals("LISTA");
    }

    /** Verifica che il tipo di vincita sia tra i valori ammessi. */
    private boolean isTipoVincitaValido(String tipo) {
        return tipo.equals("AMBO") || tipo.equals("TERNO") || tipo.equals("QUATERNA")
                || tipo.equals("CINQUINA") || tipo.equals("TOMBOLA");
    }

    // ===========================================================
    // Metodi di utilità
    // ===========================================================

    /**
     * Legge l'intero body della richiesta HTTP come stringa UTF-8.
     */
    private String leggiBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String linea;
            while ((linea = reader.readLine()) != null) {
                sb.append(linea);
            }
            return sb.toString();
        }
    }

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