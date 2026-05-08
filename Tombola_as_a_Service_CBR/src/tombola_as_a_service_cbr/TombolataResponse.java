/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tombola_as_a_service_cbr;

/**
 *
 * @author delfo
 */
public class TombolataResponse {
 
    public int id;
    public String nome;
    public int idGestore;
    public String stato;
    public String dataInizio;
    public String dataFine;
    public String dataFineAssegnazioneCartelle;
    public String modalitaAutenticazione;
    public int maxCartellePerUtente;
    public String operazioniConsentite;
 
    // Costruttore vuoto necessario per GSON
    public TombolataResponse() {
    }
 
    public TombolataResponse(int id, String nome, int idGestore, String stato,
                             String dataInizio, String dataFine,
                             String dataFineAssegnazioneCartelle,
                             String modalitaAutenticazione,
                             int maxCartellePerUtente) {
        this.id = id;
        this.nome = nome;
        this.idGestore = idGestore;
        this.stato = stato;
        this.dataInizio = dataInizio;
        this.dataFine = dataFine;
        this.dataFineAssegnazioneCartelle = dataFineAssegnazioneCartelle;
        this.modalitaAutenticazione = modalitaAutenticazione;
        this.maxCartellePerUtente = maxCartellePerUtente;
        this.operazioniConsentite = calcolaOperazioniConsentite(stato);
    }
 
    /**
     * Calcola la descrizione delle operazioni consentite in base allo stato corrente.
     * Segue la tabella definita nelle specifiche TaaS.
     *
     * @param stato lo stato corrente della tombolata
     * @return la descrizione delle operazioni permesse
     */
    private String calcolaOperazioniConsentite(String stato) {
        if (stato == null) return "";
        switch (stato.toUpperCase().trim()) {
            case "CREATA":    return "Modifica configurazione, aggiunta cartelle";
            case "APERTA":    return "Iscrizione utenti, assegnazione cartelle";
            case "ATTIVA":    return "Estrazione numeri, controllo vincite";
            case "TERMINATA": return "Sola lettura; nessuna operazione di scrittura";
            default:          return "";
        }
    }
 
    public int getId() {
        return id;
    }
 
    public void setId(int id) {
        this.id = id;
    }
 
    public String getNome() {
        return nome;
    }
 
    public void setNome(String nome) {
        this.nome = nome;
    }
 
    public int getIdGestore() {
        return idGestore;
    }
 
    public void setIdGestore(int idGestore) {
        this.idGestore = idGestore;
    }
 
    public String getStato() {
        return stato;
    }
 
    public void setStato(String stato) {
        this.stato = stato;
        this.operazioniConsentite = calcolaOperazioniConsentite(stato);
    }
 
    public String getDataInizio() {
        return dataInizio;
    }
 
    public void setDataInizio(String dataInizio) {
        this.dataInizio = dataInizio;
    }
 
    public String getDataFine() {
        return dataFine;
    }
 
    public void setDataFine(String dataFine) {
        this.dataFine = dataFine;
    }
 
    public String getDataFineAssegnazioneCartelle() {
        return dataFineAssegnazioneCartelle;
    }
 
    public void setDataFineAssegnazioneCartelle(String dataFineAssegnazioneCartelle) {
        this.dataFineAssegnazioneCartelle = dataFineAssegnazioneCartelle;
    }
 
    public String getModalitaAutenticazione() {
        return modalitaAutenticazione;
    }
 
    public void setModalitaAutenticazione(String modalitaAutenticazione) {
        this.modalitaAutenticazione = modalitaAutenticazione;
    }
 
    public int getMaxCartellePerUtente() {
        return maxCartellePerUtente;
    }
 
    public void setMaxCartellePerUtente(int maxCartellePerUtente) {
        this.maxCartellePerUtente = maxCartellePerUtente;
    }
 
    public String getOperazioniConsentite() {
        return operazioniConsentite;
    }
 
    public void setOperazioniConsentite(String operazioniConsentite) {
        this.operazioniConsentite = operazioniConsentite;
    }
 
    @Override
    public String toString() {
        return "TombolataResponse{ id=" + id
                + ", nome=" + nome
                + ", idGestore=" + idGestore
                + ", stato=" + stato
                + ", dataInizio=" + dataInizio
                + ", dataFine=" + dataFine
                + ", dataFineAssegnazioneCartelle=" + dataFineAssegnazioneCartelle
                + ", modalitaAutenticazione=" + modalitaAutenticazione
                + ", maxCartellePerUtente=" + maxCartellePerUtente
                + ", operazioniConsentite=" + operazioniConsentite + '}';
    }
}