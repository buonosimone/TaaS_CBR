/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */

package tombola_as_a_service_cbr;

/**
 * Entry point dell'applicazione Tombola as a Service (TaaS)
 * * @author [Tuo Nome / Gruppo]
 */
public class App {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // Configurazione porta (default 8080)
        // Nota: Assicurati che non ci siano altri servizi sulla 8080
        int porta = 8080;
        
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta non valida, uso porta default 8080");
            }
        }
        
        System.out.println("Avvio del servizio TaaS...");
        
        ServerRest.avviaServer(porta);
    }
}