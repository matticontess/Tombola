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
    private final Set<Integer> estratti = new LinkedHashSet<>();      // tabellone dei numeri estratti, inizalmente pieno di punti (.) poi riempito coi numeri
    private final Map<String, String> vincitori = new LinkedHashMap<>(); // premio -> nome
    private final Random rnd = new Random();  //generatore di numeri randomici
    private volatile boolean finita = false; //controlla se la partita è finita

    public static void main(String[] args) throws Exception {
        new Server().avvia(); //si fa partire il server
    }

    private void avvia() throws Exception {
        ServerSocket ss = new ServerSocket(PORTA); //il server si mette in ascolto sulla porta definita

        // thread che accetta le connessioni
        Thread accettatore = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    new GestoreClient(ss.accept()).start();
                } catch (IOException e) {
                    break;
                }
            }
        });
        accettatore.setDaemon(true); //permette di far andare in background il thread finchè non termina
        accettatore.start(); //viene fatto partire il thread che accetta

        System.out.println("Server in ascolto sulla porta " + PORTA);
        System.out.println("Premi INVIO per iniziare l'estrazione...");
        new Scanner(System.in).nextLine(); //se si preme enter parte l'estrazione

        List<Integer> sacchetto = new ArrayList<>(); //lista che contiene i numeri
        for (int i = 1; i <= 90; i++) sacchetto.add(i); //inserimento dei numeri nella lista
        Collections.shuffle(sacchetto, rnd); //mette in ordine randomico i numeri nella lista

        for (int n : sacchetto) { //viene fatta l'estrazione dei numeri
            synchronized (this) {
                if (finita) break;
                estratti.add(n);
                broadcast("NUMERO " + n); //dice a tutti i client il numero estratto
                System.out.println("\nEstratto: " + n);
                stampaTabellone();
            }
            Thread.sleep(PAUSA_MS);
        }

        synchronized (this) {
            if (!finita) {
                finita = true;
                broadcast("FINE Numeri esauriti");
            }
        }

        System.out.println("\n=== RISULTATI ===");
        vincitori.forEach((premio, nome) -> System.out.println(premio + ": " + nome)); //per ogni vincitore viene scritto il premio e il nome
        ss.close();
    }

    /** Verifica una richiesta di vincita: il server non si fida del client. */
    private synchronized void verifica(GestoreClient g, String premio) {
        if (finita) return;

        if (!Arrays.asList(Cartella.PREMI).contains(premio)) {
            g.invia("RIFIUTATA " + premio + " premio sconosciuto");
            return;
        }
        if (vincitori.containsKey(premio)) {
            g.invia("RIFIUTATA " + premio + " gia' assegnato a " + vincitori.get(premio));
            return;
        }
        if (!g.cartella.haPremio(premio, estratti)) {
            g.invia("RIFIUTATA " + premio + " non valido");
            System.out.println("Richiesta NON valida di " + g.nome + " per " + premio);
            return;
        }

        vincitori.put(premio, g.nome);
        System.out.println(">>> " + premio + " a " + g.nome);
        broadcast("VINCITA " + premio + " " + g.nome);

        if (premio.equals("TOMBOLA")) {
            finita = true;
            broadcast("FINE Tombola di " + g.nome);
        }
    }

    private void broadcast(String msg) {
        for (GestoreClient g : clients) g.invia(msg);
    }

    private void stampaTabellone() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 90; i++) {
            sb.append(estratti.contains(i) ? String.format("%3d", i) : "  .");
            if (i % 10 == 0) sb.append('\n');
        }
        System.out.print(sb);
    }

    /** Un thread per ogni client connesso. */
    private class GestoreClient extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private String nome = "Anonimo";
        private Cartella cartella;

        GestoreClient(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out = new PrintWriter(socket.getOutputStream(), true);

                String prima = in.readLine();
                if (prima != null && prima.startsWith("NOME ")) nome = prima.substring(5).trim();

                cartella = Cartella.casuale(rnd);
                invia("CARTELLA " + cartella);

                // Registrazione + invio dei numeri gia' usciti (se entra a partita iniziata)
                synchronized (Server.this) {
                    clients.add(this);
                    for (int n : estratti) invia("NUMERO " + n);
                }
                System.out.println(nome + " connesso, cartella: " + cartella);

                String riga;
                while ((riga = in.readLine()) != null) {
                    if (riga.startsWith("VINCITA ")) {
                        verifica(this, riga.substring(8).trim().toUpperCase());
                    }
                }
            } catch (IOException e) {
                // client disconnesso
            } finally {
                clients.remove(this);
                System.out.println(nome + " disconnesso");
            }
        }

        synchronized void invia(String msg) {
            out.println(msg);
        }
    }
}