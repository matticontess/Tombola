package com.mycompany.tombola1;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server della tombola.
 * - Accetta client in qualsiasi momento e assegna a ognuno una cartella casuale.
 * - Gestisce il tabellone ed estrae i numeri (dopo INVIO sulla console).
 * - Riceve le richieste di vincita e le VERIFICA prima di assegnarle.
 *
 * Protocollo (una riga di testo per messaggio):
 *   client -> server : NOME <nome>
 *                      VINCITA <PREMIO>
 *   server -> client : CARTELLA <r1;r2;r3>        (numeri separati da virgola)
 *                      NUMERO <n>
 *                      VINCITA <PREMIO> <nome>     (a tutti)
 *                      RIFIUTATA <PREMIO> <motivo> (solo al richiedente)
 *                      FINE <messaggio>
 */
public class Server {

    private static final int PORTA = 5000;  //porta su cui il client si metterà in comunicazione
    private static final int PAUSA_MS = 2000; //tempo di pausa del server dopo l'estrazione

    private final List<GestoreClient> clients = new CopyOnWriteArrayList<>(); //lista dinamica di client che si collegano al server
    private final Set<Integer> estratti = new LinkedHashSet<>();      //numeri estratti finora, nell'ordine di uscita (inizialmente vuoto)
    private final Map<String, String> vincitori = new LinkedHashMap<>(); //associa a ogni premio il nome di chi l'ha vinto
    private final Random rnd = new Random();  //generatore di numeri randomici
    private volatile boolean finita = false; //controlla se la partita è finita (volatile: visibile subito a tutti i thread)

    public static void main(String[] args) throws Exception {
        new Server().avvia(); //si fa partire il server
    }

    private void avvia() throws Exception {
        ServerSocket ss = new ServerSocket(PORTA); //il server si mette in ascolto sulla porta definita

        //thread che accetta le connessioni
        Thread accettatore = new Thread(() -> {
            while (!ss.isClosed()) { //finché il server è aperto continua ad accettare client
                try {
                    new GestoreClient(ss.accept()).start(); //accept() aspetta un client, poi gli si crea un thread dedicato
                } catch (IOException e) {
                    break; //se il socket viene chiuso si esce dal ciclo
                }
            }
        });
        accettatore.setDaemon(true); //thread daemon: il programma può terminare senza aspettare che finisca
        accettatore.start(); //viene fatto partire il thread che accetta

        System.out.println("Server in ascolto sulla porta " + PORTA);
        System.out.println("Premi INVIO per iniziare l'estrazione...");
        new Scanner(System.in).nextLine(); //se si preme enter parte l'estrazione

        List<Integer> sacchetto = new ArrayList<>(); //lista che contiene i numeri
        for (int i = 1; i <= 90; i++) sacchetto.add(i); //inserimento dei numeri nella lista
        Collections.shuffle(sacchetto, rnd); //mette in ordine randomico i numeri nella lista

        for (int n : sacchetto) { //viene fatta l'estrazione dei numeri
            synchronized (this) { //blocco sincronizzato: nessuna verifica di vincita può avvenire durante l'estrazione
                if (finita) break; //se qualcuno ha fatto tombola si smette di estrarre
                estratti.add(n); //il numero viene segnato sul tabellone
                broadcast("NUMERO " + n); //dice a tutti i client il numero estratto
                System.out.println("\nEstratto: " + n);
                stampaTabellone(); //mostra sulla console del server il tabellone aggiornato
            }
            Thread.sleep(PAUSA_MS); //pausa fuori dal blocco sincronizzato, così i client possono chiedere vincite
        }

        synchronized (this) {
            if (!finita) { //se si arriva qui senza tombola vuol dire che i numeri sono finiti
                finita = true;
                broadcast("FINE Numeri esauriti"); //avvisa tutti i client che la partita è terminata
            }
        }

        System.out.println("\n=== RISULTATI ===");
        vincitori.forEach((premio, nome) -> System.out.println(premio + ": " + nome)); //per ogni vincitore viene scritto il premio e il nome
        ss.close(); //chiude il server socket (fa terminare anche il thread accettatore)
    }

    //metodo che verifica le vincite (synchronized: una richiesta alla volta)
    private synchronized void verifica(GestoreClient g, String premio) {
        if (finita) return; //se la partita è finita esce

        //controlla se il premio c'è, è già stato assegnato oppure se non è valido

        if (!Arrays.asList(Cartella.PREMI).contains(premio)) { //il premio non esiste tra quelli previsti
            g.invia("RIFIUTATA " + premio + " premio sconosciuto");
            return;
        }
        if (vincitori.containsKey(premio)) { //il premio è già stato vinto da un altro giocatore
            g.invia("RIFIUTATA " + premio + " gia' assegnato a " + vincitori.get(premio));
            return;
        }
        if (!g.cartella.haPremio(premio, estratti)) { //la cartella non ha davvero i numeri per quel premio
            g.invia("RIFIUTATA " + premio + " non valido");
            System.out.println("Richiesta NON valida di " + g.nome + " per " + premio);
            return;
        }

        vincitori.put(premio, g.nome); //il premio viene assegnato al giocatore
        System.out.println(">>> " + premio + " a " + g.nome);
        broadcast("VINCITA " + premio + " " + g.nome); //tutti i client vengono avvisati della vincita

        if (premio.equals("TOMBOLA")) { //con la tombola la partita finisce
            finita = true;
            broadcast("FINE Tombola di " + g.nome);
        }
    }

    private void broadcast(String msg) {
        for (GestoreClient g : clients) g.invia(msg);
    } //invia ad ogni user il messaggio

    private void stampaTabellone() {   //costruisce una stringa con i numeri estratti
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 90; i++) {
            sb.append(estratti.contains(i) ? String.format("%3d", i) : "  ."); //numero se estratto, altrimenti un punto
            if (i % 10 == 0) sb.append('\n'); //va a capo ogni 10 numeri (righe del tabellone)
        }
        System.out.print(sb);
    }

    /** Un thread per ogni client connesso. */
    private class GestoreClient extends Thread {
        private final Socket socket; //connessione con il client
        private PrintWriter out; //canale per scrivere al client
        private String nome = "Anonimo"; //nome del giocatore (di default Anonimo)
        private Cartella cartella; //cartella assegnata al giocatore

        GestoreClient(Socket socket) {
            this.socket = socket; //salva il socket del client appena connesso
        }

        @Override
        public void run() {
            try (socket; //try-with-resources: socket e reader vengono chiusi in automatico alla fine
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) { //canale per leggere dal client

                out = new PrintWriter(socket.getOutputStream(), true); //true = autoflush, ogni messaggio parte subito

                String prima = in.readLine(); //il primo messaggio del client deve essere il nome
                if (prima != null && prima.startsWith("NOME ")) nome = prima.substring(5).trim(); //si prende il nome togliendo "NOME "

                cartella = Cartella.casuale(rnd); //viene generata una cartella casuale
                invia("CARTELLA " + cartella); //la cartella viene mandata al client

                //registrazione + invio dei numeri già usciti (se entra a partita iniziata)
                synchronized (Server.this) { //sincronizzato col server per non perdere numeri estratti nel frattempo
                    clients.add(this); //il client entra nella lista di chi riceve i broadcast
                    for (int n : estratti) invia("NUMERO " + n); //gli si mandano i numeri già estratti
                }
                System.out.println(nome + " connesso, cartella: " + cartella);

                String riga;
                while ((riga = in.readLine()) != null) { //legge i messaggi finché il client resta connesso
                    if (riga.startsWith("VINCITA ")) { //il client dichiara una vincita
                        verifica(this, riga.substring(8).trim().toUpperCase()); //si controlla se è valida
                    }
                }
            } catch (IOException e) {
                //client disconnesso
            } finally {
                clients.remove(this); //il client viene tolto dalla lista
                System.out.println(nome + " disconnesso");
            }
        }

        synchronized void invia(String msg) {
            out.println(msg);
        } //manda un messaggio al client (synchronized per non mescolare messaggi da thread diversi)
    }
}
