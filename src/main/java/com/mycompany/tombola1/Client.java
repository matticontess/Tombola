package com.mycompany.tombola1;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Client della tombola.
 * Riceve la cartella, segna i numeri estratti e segnala al server
 * ogni premio raggiunto (ambo, terno, quaterna, cinquina, tombola).
 *
 * Uso: java Client [host]   (default: localhost)
 */
public class Client {

    private static final int PORTA = 5000;  //porta sulla quale il server è in ascolto

    public static void main(String[] args) throws IOException {
        String host = (args.length > 0) ? args[0] : "localhost"; // indirizzo del server. per riga di comando si usa la prima opzione, altrimenti si usa localhost

        System.out.print("Il tuo nome: ");
        String nome = new Scanner(System.in).nextLine().trim();
        if (nome.isEmpty()) nome = "Giocatore"; // nome di default se l'utente non lo mette

        try (Socket socket = new Socket(host, PORTA); // connesione al server
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // legge le righe di testo inviate al server
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {  //scrive righe verso il server

            out.println("NOME " + nome); //mi presento al sever

            Cartella cartella = null; // la cartella arrivera' dal server
            Set<Integer> segnati = new HashSet<>(); // numeri della MIA cartella gia' usciti
            Set<String> premiGestiti = new HashSet<>(); // gia' vinti da qualcuno o gia' richiesti da me

            String riga;
            while ((riga = in.readLine()) != null) { //leggo un messaggio alla volta finche' il server non chiude, (readLine() restituisce null quando la connessione viene chiusa)
                String[] p = riga.split(" ", 2);
                String comando = p[0];
                String dati = (p.length > 1) ? p[1] : "";

                switch (comando) { // Gestisco il messaggio in base al comando ricevuto
                    case "CARTELLA": // Il server mi assegna la cartella 
                        cartella = Cartella.daStringa(dati);
                        System.out.println("La tua cartella:");
                        System.out.print(cartella.disegna(segnati));
                        break;

                    case "NUMERO":
                        int n = Integer.parseInt(dati);
                        if (cartella.contiene(n)) {
                            segnati.add(n);
                            System.out.println("\nEstratto " + n + "  -> CE L'HAI!");
                            System.out.print(cartella.disegna(segnati));
                        } else {
                            System.out.println("Estratto " + n);
                        }
                        // controllo dei premi raggiunti
                        for (String premio : Cartella.PREMI) {
                            if (!premiGestiti.contains(premio) && cartella.haPremio(premio, segnati)) {
                                out.println("VINCITA " + premio);
                                premiGestiti.add(premio);
                                System.out.println("*** Segnalo " + premio + "! ***");
                            }
                        }
                        break;

                    case "VINCITA":
                        String[] v = dati.split(" ", 2);
                        premiGestiti.add(v[0]);
                        if (v.length > 1 && v[1].equals(nome)) {
                            System.out.println("*** HAI VINTO " + v[0] + "! ***");
                        } else {
                            System.out.println(v[0] + " vinto da " + (v.length > 1 ? v[1] : "?"));
                        }
                        break;

                    case "RIFIUTATA":
                        System.out.println("Richiesta rifiutata: " + dati);
                        break;

                    case "FINE":
                        System.out.println("\nPartita terminata: " + dati);
                        return;

                    default:
                        System.out.println("Messaggio sconosciuto: " + riga);
                }
            }
            System.out.println("Connessione chiusa dal server.");
        }
    }
}
