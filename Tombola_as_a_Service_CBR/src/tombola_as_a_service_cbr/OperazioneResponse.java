/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package serverrest;

/**
 *
 * @author delfo
 */
public class OperazioneResponse {
    private double operando1;
    private double operando2;
    private String operatore;
    private double risultato;
    private String operazione;
    
    // Costruttore vuoto necessario per GSON
    public OperazioneResponse() {
    }
    
    // Costruttore con parametri
    public OperazioneResponse(double operando1, double operando2, 
                             String operatore, double risultato) {
        this.operando1 = operando1;
        this.operando2 = operando2;
        this.operatore = operatore;
        this.risultato = risultato;
        this.operazione = String.format("%.2f %s %.2f = %.2f", 
            operando1, operatore, operando2, risultato);
    }
    
    // Getter
    public double getOperando1() {
        return operando1;
    }
    
    public double getOperando2() {
        return operando2;
    }
    
    public String getOperatore() {
        return operatore;
    }
    
    public double getRisultato() {
        return risultato;
    }
    
    public String getOperazione() {
        return operazione;
    }
    
    // Setter
    public void setOperando1(double operando1) {
        this.operando1 = operando1;
    }
    
    public void setOperando2(double operando2) {
        this.operando2 = operando2;
    }
    
    public void setOperatore(String operatore) {
        this.operatore = operatore;
    }
    
    public void setRisultato(double risultato) {
        this.risultato = risultato;
    }
    
    public void setOperazione(String operazione) {
        this.operazione = operazione;
    }
}