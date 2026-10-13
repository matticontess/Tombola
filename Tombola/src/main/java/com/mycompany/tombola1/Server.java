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

    private static final int PORTA = 5000;
    private static final int PAUSA_MS = 2000;

    private final List<GestoreClient> clients = new CopyOnWriteArrayList<>();
    private final Set<Integer> estratti = new LinkedHashSet<>();      // il tabellone
    private final Map<String, String> vincitori = new LinkedHashMap<>(); // premio -> nome
    private final Random rnd = new Random();
    private volatile boolean finita = false;

    public static void main(String[] args) throws Exception {
        new Server().avvia();
    }

    private void avvia() throws Exception {
        ServerSocket ss = new ServerSocket(PORTA);

        // Thread che accetta le connessioni per tutta la partita
        Thread accettatore = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    new GestoreClient(ss.accept()).start();
                } catch (IOException e) {
                    break;
                }
            }
        });
        accettatore.setDaemon(true);
        accettatore.start();

        System.out.println("Server in ascolto sulla porta " + PORTA);
        System.out.println("Premi INVIO per iniziare l'estrazione...");
        new Scanner(System.in).nextLine();

        List<Integer> sacchetto = new ArrayList<>();
        for (int i = 1; i <= 90; i++) sacchetto.add(i);
        Collections.shuffle(sacchetto, rnd);

        for (int n : sacchetto) {
            synchronized (this) {
                if (finita) break;
                estratti.add(n);
                broadcast("NUMERO " + n);
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
        vincitori.forEach((premio, nome) -> System.out.println(premio + ": " + nome));
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