/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package serverrest;

/**
 *
 * @author delfo
 */
public class OperazioneRequest {
    private double operando1;
    private double operando2;
    private String operatore;
    
    // Costruttore vuoto necessario per GSON
    public OperazioneRequest() {
    }
    
    // Costruttore con parametri
    public OperazioneRequest(double operando1, double operando2, String operatore) {
        this.operando1 = operando1;
        this.operando2 = operando2;
        this.operatore = operatore;
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
    
    @Override
    public String toString() {
        return "OperazioneRequest{" +
                "operando1=" + operando1 +
                ", operando2=" + operando2 +
                ", operatore='" + operatore + '\'' +
                '}';
    }
}