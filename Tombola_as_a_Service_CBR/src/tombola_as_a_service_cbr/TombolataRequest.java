/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tombola_as_a_service_cbr;

/**
 *
 * @author delfo
 */
public class TombolataRequest {
 
    public String nome;
    public String idGestore;
    public String stato;
    public String dataInizio;
    public String dataFine;
    public String dataFineAssegnazioneCartelle;
    public String modalitaAutenticazione;
    public String maxCartellePerUtente;
 
    // Costruttore vuoto necessario per GSON
    public TombolataRequest() {
    }
 
    public TombolataRequest(String nome, String idGestore, String stato,
                            String dataInizio, String dataFine,
                            String dataFineAssegnazioneCartelle,
                            String modalitaAutenticazione,
                            String maxCartellePerUtente) {
        this.nome = nome;
        this.idGestore = idGestore;
        this.stato = stato;
        this.dataInizio = dataInizio;
        this.dataFine = dataFine;
        this.dataFineAssegnazioneCartelle = dataFineAssegnazioneCartelle;
        this.modalitaAutenticazione = modalitaAutenticazione;
        this.maxCartellePerUtente = maxCartellePerUtente;
    }
 
    public String getNome() {
        return nome;
    }
 
    public void setNome(String nome) {
        this.nome = nome;
    }
 
    public String getIdGestore() {
        return idGestore;
    }
 
    public void setIdGestore(String idGestore) {
        this.idGestore = idGestore;
    }
 
    public String getStato() {
        return stato;
    }
 
    public void setStato(String stato) {
        this.stato = stato;
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
 
    public String getMaxCartellePerUtente() {
        return maxCartellePerUtente;
    }
 
    public void setMaxCartellePerUtente(String maxCartellePerUtente) {
        this.maxCartellePerUtente = maxCartellePerUtente;
    }
 
    @Override
    public String toString() {
        return "TombolataRequest{ nome=" + nome
                + ", idGestore=" + idGestore
                + ", stato=" + stato
                + ", dataInizio=" + dataInizio
                + ", dataFine=" + dataFine
                + ", dataFineAssegnazioneCartelle=" + dataFineAssegnazioneCartelle
                + ", modalitaAutenticazione=" + modalitaAutenticazione
                + ", maxCartellePerUtente=" + maxCartellePerUtente + '}';
    }
}