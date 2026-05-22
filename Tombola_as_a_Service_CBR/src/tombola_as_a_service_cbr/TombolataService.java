package tombola_as_a_service_cbr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * TombolataService - Logica di business per TaaS.
 *
 * Questa classe contiene TUTTA la logica applicativa del sistema:
 * nessuna logica di business deve stare nei Handler (che si occupano
 * solo di HTTP: leggere request, chiamare il service, restituire response).
 *
 * Responsabilità:
 *  - Creazione e recupero delle tombolate
 *  - Gestione della macchina a stati (CREATA → APERTA → ATTIVA → TERMINATA)
 *  - Estrazione casuale dei numeri (1–90) senza ripetizioni
 *  - Recupero ultimo numero / ultimi cinque / tabellone completo
 *  - Generazione delle cartelle rispettando la regola dei decili
 *  - Controllo automatico delle vincite (ambo, terno, quaterna, cinquina, tombola)
 *  - Conferma manuale delle vincite da parte del gestore
 *
 * NOTA IMPLEMENTATIVA: in questa versione i dati sono tenuti in memoria
 * (strutture dati statiche) per permettere il testing senza un database.
 * In produzione i metodi dovrebbero delegare a un DAO / repository che
 * esegue query SQL sul database reale.
 *
 * @author [Tuo Nome / Gruppo]
 */
public class TombolataService {

    // ===========================================================
    // Costanti della macchina a stati
    // ===========================================================

    public static final String STATO_CREATA    = "CREATA";
    public static final String STATO_APERTA    = "APERTA";
    public static final String STATO_ATTIVA    = "ATTIVA";
    public static final String STATO_TERMINATA = "TERMINATA";

    private static final String[] STATI_ORDINATI = {
        STATO_CREATA, STATO_APERTA, STATO_ATTIVA, STATO_TERMINATA
    };

    // Tipi di vincita in ordine crescente di difficoltà
    public static final String VINCITA_AMBO      = "AMBO";
    public static final String VINCITA_TERNO     = "TERNO";
    public static final String VINCITA_QUATERNA  = "QUATERNA";
    public static final String VINCITA_CINQUINA  = "CINQUINA";
    public static final String VINCITA_TOMBOLA   = "TOMBOLA";

    // ===========================================================
    // Storage in-memory (sostituire con DAO in produzione)
    // ===========================================================

    /** Mappa id → TombolataResponse (le tombolate create nel sistema). */
    private static final Map<Integer, TombolataResponse> tombolate = new HashMap<>();

    /**
     * Mappa id_tombolata → lista ordinata dei numeri estratti.
     * L'indice 0 è il primo numero estratto, l'ultimo elemento è il più recente.
     */
    private static final Map<Integer, List<Integer>> numeriEstratti = new HashMap<>();

    /**
     * Mappa id_tombolata → lista di cartelle (ogni cartella è un int[3][9]).
     * Ogni cartella è associata a un utente tramite CartellaInfo.
     */
    private static final Map<Integer, List<CartellaInfo>> cartelle = new HashMap<>();

    /** Contatore auto-increment per gli ID delle tombolate. */
    private static int nextIdTombolata = 1;

    /** Contatore auto-increment per gli ID delle cartelle. */
    private static int nextIdCartella = 1;

    private static final Random random = new Random();

    // ===========================================================
    // Classe interna: CartellaInfo
    // Associa una cartella (griglia numerica) a un utente e a una tombolata.
    // ===========================================================

    public static class CartellaInfo {
        public int idCartella;
        public int idUtente;
        public int idTombolata;
        public int[][] griglia; // 3 righe × 9 colonne; 0 = cella vuota

        public CartellaInfo(int idCartella, int idUtente, int idTombolata, int[][] griglia) {
            this.idCartella  = idCartella;
            this.idUtente    = idUtente;
            this.idTombolata = idTombolata;
            this.griglia     = griglia;
        }
    }

    // ===========================================================
    // Classe interna: EstrazioneResult
    // Risultato dell'estrazione: numero estratto + vincite rilevate.
    // ===========================================================

    public static class EstrazioneResult {
        public int  numero;
        public int  ordineEstrazione;
        public List<String> vinciteRilevate;

        public EstrazioneResult(int numero, int ordine, List<String> vincite) {
            this.numero           = numero;
            this.ordineEstrazione = ordine;
            this.vinciteRilevate  = vincite;
        }
    }

    // ===========================================================
    // Eccezione interna: ServiceException
    // Trasporta codice HTTP + messaggio d'errore verso gli Handler.
    // ===========================================================

    public static class ServiceException extends Exception {
        private final int httpStatus;

        public ServiceException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }

    // ===========================================================
    // 1. Gestione delle tombolate
    // ===========================================================

    /**
     * Restituisce la lista di tutte le tombolate presenti nel sistema.
     *
     * @return lista (possibilmente vuota) di TombolataResponse
     */
    public static List<TombolataResponse> getTutteLeTombolate() {
        return new ArrayList<>(tombolate.values());
    }

    /**
     * Restituisce i dettagli di una singola tombolata.
     *
     * @param id identificativo della tombolata
     * @return TombolataResponse corrispondente
     * @throws ServiceException 404 se la tombolata non esiste
     */
    public static TombolataResponse getTombolata(int id) throws ServiceException {
        TombolataResponse t = tombolate.get(id);
        if (t == null) {
            throw new ServiceException(404, "Tombolata non trovata con ID: " + id);
        }
        return t;
    }

    /**
     * Crea una nuova tombolata a partire da un TombolataRequest validato.
     * Lo stato iniziale è sempre CREATA.
     *
     * Validazioni eseguite:
     *  - nome non nullo/vuoto
     *  - idGestore valido (numero intero > 0)
     *  - dataInizio non nulla
     *  - modalitaAutenticazione in {PUBBLICA, CODICE, LISTA}
     *  - maxCartellePerUtente > 0 (default 6 se non specificato)
     *
     * @param req oggetto TombolataRequest deserializzato dal body HTTP
     * @return TombolataResponse con l'ID assegnato e stato CREATA
     * @throws ServiceException 400 se i dati non sono validi
     */
    public static TombolataResponse creaTombolata(TombolataRequest req) throws ServiceException {
        // --- Validazioni ---
        if (req == null) {
            throw new ServiceException(400, "Body della richiesta vuoto o non valido.");
        }
        if (isBlank(req.getNome())) {
            throw new ServiceException(400, "Campo obbligatorio mancante: 'nome'.");
        }
        if (isBlank(req.getIdGestore())) {
            throw new ServiceException(400, "Campo obbligatorio mancante: 'idGestore'.");
        }
        if (isBlank(req.getDataInizio())) {
            throw new ServiceException(400, "Campo obbligatorio mancante: 'dataInizio'.");
        }
        if (isBlank(req.getModalitaAutenticazione()) || !isModalitaValida(req.getModalitaAutenticazione())) {
            throw new ServiceException(400,
                    "'modalitaAutenticazione' non valida. Valori ammessi: PUBBLICA, CODICE, LISTA.");
        }

        int idGestore;
        try {
            idGestore = Integer.parseInt(req.getIdGestore().trim());
            if (idGestore <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new ServiceException(400, "'idGestore' deve essere un numero intero positivo.");
        }

        int maxCartelle = 6; // valore di default
        if (!isBlank(req.getMaxCartellePerUtente())) {
            try {
                maxCartelle = Integer.parseInt(req.getMaxCartellePerUtente().trim());
                if (maxCartelle <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new ServiceException(400, "'maxCartellePerUtente' deve essere un numero intero positivo.");
            }
        }

        // --- Creazione ---
        int nuovoId = nextIdTombolata++;
        TombolataResponse nuova = new TombolataResponse(
                nuovoId,
                req.getNome().trim(),
                idGestore,
                STATO_CREATA,
                req.getDataInizio(),
                req.getDataFine(),
                req.getDataFineAssegnazioneCartelle(),
                req.getModalitaAutenticazione().toUpperCase().trim(),
                maxCartelle
        );

        tombolate.put(nuovoId, nuova);
        numeriEstratti.put(nuovoId, new ArrayList<>());
        cartelle.put(nuovoId, new ArrayList<>());

        return nuova;
    }

    // ===========================================================
    // 2. Macchina a stati
    // ===========================================================

    /**
     * Avanza lo stato di una tombolata alla fase successiva.
     *
     * Transizioni consentite (solo avanzamento di un passo):
     *   CREATA → APERTA → ATTIVA → TERMINATA
     *
     * Transizioni saltate o regressi sono rifiutati con 409 Conflict.
     *
     * @param id         identificativo della tombolata
     * @param nuovoStato stringa del nuovo stato desiderato (case-insensitive)
     * @return TombolataResponse aggiornata con il nuovo stato
     * @throws ServiceException 404 tombolata non trovata | 400 stato non valido | 409 transizione vietata
     */
    public static TombolataResponse cambiaStato(int id, String nuovoStato) throws ServiceException {
        TombolataResponse t = getTombolata(id);

        if (isBlank(nuovoStato)) {
            throw new ServiceException(400, "Campo 'nuovo_stato' mancante o vuoto.");
        }

        String nuovo = nuovoStato.toUpperCase().trim();
        if (!isStatoValido(nuovo)) {
            throw new ServiceException(400,
                    "Stato non riconosciuto: '" + nuovoStato + "'. "
                    + "Valori ammessi: CREATA, APERTA, ATTIVA, TERMINATA.");
        }

        if (!transazioneConsentita(t.getStato(), nuovo)) {
            throw new ServiceException(409,
                    "Transizione non consentita: " + t.getStato() + " → " + nuovo
                    + ". Sequenza valida: CREATA → APERTA → ATTIVA → TERMINATA.");
        }

        t.setStato(nuovo); // aggiorna anche operazioniConsentite internamente
        return t;
    }

    // ===========================================================
    // 3. Estrazione numeri
    // ===========================================================

    /**
     * Estrae casualmente un numero tra 1 e 90 non ancora estratto
     * nella sessione specificata.
     *
     * Dopo l'estrazione controlla automaticamente le vincite su tutte
     * le cartelle della tombolata (ambo, terno, quaterna, cinquina, tombola).
     *
     * Se tutte le vincite principali (fino a TOMBOLA) sono state assegnate,
     * o tutti i 90 numeri sono esauriti, lo stato viene portato a TERMINATA.
     *
     * @param id identificativo della tombolata
     * @return EstrazioneResult con numero, ordine e vincite rilevate
     * @throws ServiceException 404 non trovata | 409 non in stato ATTIVA | 409 numeri esauriti
     */
    public static EstrazioneResult estraiNumero(int id) throws ServiceException {
        TombolataResponse t = getTombolata(id);

        if (!t.getStato().equalsIgnoreCase(STATO_ATTIVA)) {
            throw new ServiceException(409,
                    "Estrazione non consentita: la tombolata deve essere in stato ATTIVA. "
                    + "Stato attuale: " + t.getStato());
        }

        List<Integer> estratti = numeriEstratti.get(id);
        if (estratti.size() >= 90) {
            // Tutti i numeri esauriti → chiudi automaticamente la tombolata
            t.setStato(STATO_TERMINATA);
            throw new ServiceException(409, "Tutti i 90 numeri sono già stati estratti. "
                    + "La tombolata è stata terminata automaticamente.");
        }

        // Costruisce il set dei numeri già usciti per ricerca O(1)
        Set<Integer> estrattiSet = new HashSet<>(estratti);

        // Estrae un numero non ancora uscito
        int numero;
        do {
            numero = random.nextInt(90) + 1; // 1–90 inclusi
        } while (estrattiSet.contains(numero));

        estratti.add(numero);
        estrattiSet.add(numero);

        // Controlla vincite su tutte le cartelle della sessione
        List<String> vincite = controllaVincite(id, estrattiSet);

        return new EstrazioneResult(numero, estratti.size(), vincite);
    }

    /**
     * Restituisce l'ultimo numero estratto.
     *
     * @param id identificativo della tombolata
     * @return mappa con "numero" e "ordine_estrazione"
     * @throws ServiceException 404 non trovata | 409 non in stato ATTIVA | 404 nessun numero estratto
     */
    public static Map<String, Object> getUltimoNumero(int id) throws ServiceException {
        TombolataResponse t = getTombolata(id);

        if (!t.getStato().equalsIgnoreCase(STATO_ATTIVA)) {
            throw new ServiceException(409,
                    "Operazione non consentita: la tombolata non è in stato ATTIVA.");
        }

        List<Integer> estratti = numeriEstratti.get(id);
        if (estratti == null || estratti.isEmpty()) {
            throw new ServiceException(404, "Nessun numero ancora estratto per la tombolata " + id + ".");
        }

        Map<String, Object> risultato = new HashMap<>();
        risultato.put("numero", estratti.get(estratti.size() - 1));
        risultato.put("ordine_estrazione", estratti.size());
        return risultato;
    }

    /**
     * Restituisce gli ultimi cinque numeri estratti (dal più recente al più vecchio).
     *
     * @param id identificativo della tombolata
     * @return mappa con "ultimi_cinque" (lista da 1 a 5 elementi)
     * @throws ServiceException 404 | 409
     */
    public static Map<String, Object> getUltimiCinque(int id) throws ServiceException {
        TombolataResponse t = getTombolata(id);

        if (!t.getStato().equalsIgnoreCase(STATO_ATTIVA)) {
            throw new ServiceException(409,
                    "Operazione non consentita: la tombolata non è in stato ATTIVA.");
        }

        List<Integer> estratti = numeriEstratti.get(id);
        if (estratti == null || estratti.isEmpty()) {
            throw new ServiceException(404, "Nessun numero ancora estratto per la tombolata " + id + ".");
        }

        // Prendi gli ultimi 5 (o meno se non ne sono stati estratti abbastanza)
        int da = Math.max(0, estratti.size() - 5);
        List<Integer> ultimi = new ArrayList<>(estratti.subList(da, estratti.size()));
        Collections.reverse(ultimi); // dal più recente al più vecchio

        Map<String, Object> risultato = new HashMap<>();
        risultato.put("ultimi_cinque", ultimi);
        return risultato;
    }

    /**
     * Restituisce il tabellone completo: tutti i numeri estratti in ordine
     * cronologico, con conteggio totale e numeri rimanenti.
     *
     * @param id identificativo della tombolata
     * @return mappa con "numeri_estratti", "totale_estratti", "numeri_rimanenti"
     * @throws ServiceException 404 | 409
     */
    public static Map<String, Object> getTabellone(int id) throws ServiceException {
        TombolataResponse t = getTombolata(id);

        if (!t.getStato().equalsIgnoreCase(STATO_ATTIVA)) {
            throw new ServiceException(409,
                    "Operazione non consentita: la tombolata non è in stato ATTIVA.");
        }

        List<Integer> estratti = numeriEstratti.getOrDefault(id, new ArrayList<>());

        Map<String, Object> risultato = new HashMap<>();
        risultato.put("id_tombolata", id);
        risultato.put("numeri_estratti", new ArrayList<>(estratti));
        risultato.put("totale_estratti", estratti.size());
        risultato.put("numeri_rimanenti", 90 - estratti.size());
        return risultato;
    }

    // ===========================================================
    // 4. Gestione cartelle
    // ===========================================================

    /**
     * Crea e assegna automaticamente una cartella a un utente.
     *
     * Regole della cartella italiana (rispettate):
     *  - 3 righe × 9 colonne
     *  - Esattamente 5 numeri per riga (15 numeri totali)
     *  - Colonna 1: numeri da 1–9
     *  - Colonna 2: numeri da 10–19 ... Colonna 9: numeri da 80–90
     *  - Nessun numero ripetuto nella stessa cartella
     *
     * Consentita SOLO in stato APERTA.
     * Verifica che l'utente non abbia già raggiunto il limite maxCartellePerUtente.
     *
     * @param idTombolata identificativo della tombolata
     * @param idUtente    identificativo dell'utente
     * @return CartellaInfo con id, griglia generata e metadati
     * @throws ServiceException 404 non trovata | 409 stato non APERTA | 409 limite cartelle raggiunto
     */
    public static CartellaInfo assegnaCartella(int idTombolata, int idUtente) throws ServiceException {
        TombolataResponse t = getTombolata(idTombolata);

        if (!t.getStato().equalsIgnoreCase(STATO_APERTA)) {
            throw new ServiceException(409,
                    "Assegnazione cartella non consentita: la tombolata deve essere in stato APERTA. "
                    + "Stato attuale: " + t.getStato());
        }

        // Conta quante cartelle ha già l'utente in questa tombolata
        List<CartellaInfo> cartelleSessione = cartelle.getOrDefault(idTombolata, new ArrayList<>());
        long cartelleUtente = cartelleSessione.stream()
                .filter(c -> c.idUtente == idUtente)
                .count();

        if (cartelleUtente >= t.getMaxCartellePerUtente()) {
            throw new ServiceException(409,
                    "L'utente " + idUtente + " ha già raggiunto il limite di "
                    + t.getMaxCartellePerUtente() + " cartelle per questa tombolata.");
        }

        int[][] griglia = generaCartella();
        CartellaInfo nuova = new CartellaInfo(nextIdCartella++, idUtente, idTombolata, griglia);
        cartelleSessione.add(nuova);
        cartelle.put(idTombolata, cartelleSessione);

        return nuova;
    }

    // ===========================================================
    // 5. Vincite
    // ===========================================================

    /**
     * Controlla automaticamente le vincite su tutte le cartelle della tombolata
     * dopo ogni estrazione. Restituisce la lista di vincite rilevate.
     *
     * Logica per riga (5 numeri validi per riga):
     *  2 trovati → AMBO
     *  3 trovati → TERNO
     *  4 trovati → QUATERNA
     *  5 trovati → CINQUINA
     *
     * Logica per cartella (15 numeri totali):
     *  15 trovati → TOMBOLA
     *
     * @param idTombolata  identificativo della tombolata
     * @param estrattiSet  set di tutti i numeri fin qui estratti
     * @return lista (possibilmente vuota) delle vincite rilevate in questa estrazione
     */
    public static List<String> controllaVincite(int idTombolata, Set<Integer> estrattiSet) {
        List<String> vinciteRilevate = new ArrayList<>();
        List<CartellaInfo> cartelleSessione = cartelle.getOrDefault(idTombolata, new ArrayList<>());

        for (CartellaInfo cartella : cartelleSessione) {
            int totaleCartella = 0;

            for (int[] riga : cartella.griglia) {
                int trovatiRiga = 0;
                int celleValide = 0;

                for (int n : riga) {
                    if (n != 0) {
                        celleValide++;
                        if (estrattiSet.contains(n)) trovatiRiga++;
                    }
                }

                // Una riga valida ha esattamente 5 numeri
                if (celleValide == 5) {
                    totaleCartella += trovatiRiga;
                    switch (trovatiRiga) {
                        case 2: vinciteRilevate.add(VINCITA_AMBO);     break;
                        case 3: vinciteRilevate.add(VINCITA_TERNO);    break;
                        case 4: vinciteRilevate.add(VINCITA_QUATERNA); break;
                        case 5: vinciteRilevate.add(VINCITA_CINQUINA); break;
                    }
                }
            }

            // TOMBOLA: tutti i 15 numeri della cartella estratti
            if (totaleCartella == 15) {
                vinciteRilevate.add(VINCITA_TOMBOLA);
            }
        }

        return vinciteRilevate;
    }

    /**
     * Conferma manualmente una vincita da parte del gestore.
     *
     * @param idTombolata identificativo della tombolata
     * @param idUtente    utente a cui si attribuisce la vincita
     * @param tipoVincita tipo di vincita (AMBO, TERNO, QUATERNA, CINQUINA, TOMBOLA)
     * @return mappa con i dettagli della conferma
     * @throws ServiceException 404 non trovata | 400 tipo vincita non valido | 409 non in stato ATTIVA
     */
    public static Map<String, Object> confermaVincita(int idTombolata, int idUtente, String tipoVincita)
            throws ServiceException {

        getTombolata(idTombolata); // verifica esistenza

        TombolataResponse t = tombolate.get(idTombolata);
        if (!t.getStato().equalsIgnoreCase(STATO_ATTIVA)) {
            throw new ServiceException(409,
                    "Conferma vincita non consentita: la tombolata non è in stato ATTIVA.");
        }

        if (isBlank(tipoVincita) || !isTipoVincitaValido(tipoVincita)) {
            throw new ServiceException(400,
                    "'tipo_vincita' non valido. Valori ammessi: AMBO, TERNO, QUATERNA, CINQUINA, TOMBOLA.");
        }

        String tipo = tipoVincita.toUpperCase().trim();

        Map<String, Object> risultato = new HashMap<>();
        risultato.put("id_tombolata", idTombolata);
        risultato.put("id_utente", idUtente);
        risultato.put("tipo_vincita", tipo);
        risultato.put("stato_conferma", "CONFERMATA");
        risultato.put("messaggio",
                "Vincita " + tipo + " confermata dal gestore per l'utente " + idUtente + ".");

        // Se la vincita è TOMBOLA → la tombolata termina automaticamente
        if (tipo.equals(VINCITA_TOMBOLA)) {
            t.setStato(STATO_TERMINATA);
            risultato.put("nota", "La tombolata è stata terminata automaticamente dopo la TOMBOLA.");
        }

        return risultato;
    }

    // ===========================================================
    // Logica di generazione cartella
    // ===========================================================

    /**
     * Genera una cartella della tombola italiana rispettando le regole ufficiali:
     *
     *  - 3 righe × 9 colonne
     *  - Esattamente 5 numeri per riga (le altre 4 celle sono 0 = vuote)
     *  - Ogni colonna può contenere al massimo 1 numero per riga
     *  - Colonna 0: numeri   1–9   (9 valori possibili)
     *  - Colonna 1: numeri 10–19  (10 valori possibili)
     *  - ...
     *  - Colonna 8: numeri 80–90  (11 valori possibili, 90 incluso)
     *  - Ogni colonna contiene almeno 1 numero tra le 3 righe
     *  - All'interno della stessa colonna i numeri sono in ordine crescente dall'alto
     *
     * L'algoritmo:
     *  1. Per ogni colonna, decide quanti numeri mettere (1 o 2) in modo che
     *     il totale per riga rimanga 5 su ciascuna delle 3 righe.
     *  2. Sceglie casualmente le righe in cui posizionare i numeri.
     *  3. Assegna numeri casuali dal decile corrispondente senza ripetizioni.
     *
     * @return griglia int[3][9] con 0 nelle celle vuote
     */
    private static int[][] generaCartella() {
        int[][] griglia = new int[3][9];

        // Passo 1: decidi la distribuzione per colonna (quanti numeri in ciascuna)
        // Ogni riga deve avere esattamente 5 numeri → 3 righe × 5 = 15 numeri totali
        // 9 colonne: alcune avranno 1 numero, alcune 2 → 9 colonne con 1 = 9,
        // quindi 6 colonne devono avere 2 numeri per raggiungere 15
        // (6×2 + 3×1 = 15). Distribuiamo casualmente le 6 colonne "doppie".
        int[] numerPerColonna = new int[9];
        List<Integer> indiciColonne = new ArrayList<>();
        for (int i = 0; i < 9; i++) indiciColonne.add(i);
        Collections.shuffle(indiciColonne, random);

        for (int i = 0; i < 9; i++) {
            numerPerColonna[i] = (i < 6) ? 2 : 1;
        }
        // Rimettiamo in ordine (l'ordine non importa per la generazione)

        // Passo 2: per ogni colonna, assegna i numeri alle righe
        for (int col = 0; col < 9; col++) {
            int min = (col == 0) ? 1  : col * 10;
            int max = (col == 8) ? 90 : col * 10 + 9;
            int range = max - min + 1;

            int quanti = numerPerColonna[col];

            // Scegli 'quanti' righe distinte in ordine casuale
            List<Integer> righe = new ArrayList<>();
            righe.add(0); righe.add(1); righe.add(2);
            Collections.shuffle(righe, random);
            righe = righe.subList(0, quanti);
            Collections.sort(righe); // ordine crescente per riga → numeri crescenti in colonna

            // Scegli 'quanti' numeri distinti dal decile della colonna
            List<Integer> numeriDisponibili = new ArrayList<>();
            for (int n = min; n <= max; n++) numeriDisponibili.add(n);
            Collections.shuffle(numeriDisponibili, random);

            List<Integer> numeriScelti = new ArrayList<>(numeriDisponibili.subList(0, quanti));
            Collections.sort(numeriScelti); // ordine crescente nella colonna (regola ufficiale)

            for (int i = 0; i < quanti; i++) {
                griglia[righe.get(i)][col] = numeriScelti.get(i);
            }
        }

        return griglia;
    }

    // ===========================================================
    // Metodi di validazione interni
    // ===========================================================

    /** Verifica che la transizione di stato sia consentita (solo passo sequenziale successivo). */
    private static boolean transazioneConsentita(String statoAttuale, String nuovoStato) {
        int idxAttuale = indexOfStato(statoAttuale);
        int idxNuovo   = indexOfStato(nuovoStato);
        return idxAttuale >= 0 && idxNuovo == idxAttuale + 1;
    }

    private static int indexOfStato(String stato) {
        for (int i = 0; i < STATI_ORDINATI.length; i++) {
            if (STATI_ORDINATI[i].equalsIgnoreCase(stato)) return i;
        }
        return -1;
    }

    private static boolean isStatoValido(String stato) {
        return indexOfStato(stato) >= 0;
    }

    private static boolean isModalitaValida(String modalita) {
        String m = modalita.toUpperCase().trim();
        return m.equals("PUBBLICA") || m.equals("CODICE") || m.equals("LISTA");
    }

    private static boolean isTipoVincitaValido(String tipo) {
        String t = tipo.toUpperCase().trim();
        return t.equals(VINCITA_AMBO)   || t.equals(VINCITA_TERNO)    ||
               t.equals(VINCITA_QUATERNA) || t.equals(VINCITA_CINQUINA) ||
               t.equals(VINCITA_TOMBOLA);
    }

    /** null-safe blank check (equivalente a String.isBlank() di Java 11). */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}