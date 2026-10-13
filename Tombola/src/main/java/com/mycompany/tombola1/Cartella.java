package com.mycompany.tombola1;

import java.util.*;

/**
 * Cartella della tombola: 3 righe x 5 numeri (15 numeri distinti).
 * Ogni numero sta nella sua "colonna" (decina): 1-9, 10-19, ..., 80-90.
 * Classe usata sia dal server (per generare e verificare) sia dal client (per segnare).
 */
public class Cartella {

    public static final String[] PREMI  = {"AMBO", "TERNO", "QUATERNA", "CINQUINA", "TOMBOLA"};
    public static final int[]    SOGLIE = {2, 3, 4, 5, 15};

    private final int[][] righe; // 3 righe, 5 numeri ciascuna, in ordine crescente

    private Cartella(int[][] righe) {
        this.righe = righe;
    }

    /** Genera una cartella casuale. */
    public static Cartella casuale(Random rnd) {
        int[][] r = new int[3][5];
        Set<Integer> usati = new HashSet<>();

        for (int i = 0; i < 3; i++) {
            // scelgo 5 colonne diverse su 9 per questa riga
            List<Integer> colonne = new ArrayList<>();
            for (int c = 0; c < 9; c++) colonne.add(c);
            Collections.shuffle(colonne, rnd);
            List<Integer> scelte = new ArrayList<>(colonne.subList(0, 5));
            Collections.sort(scelte);

            for (int j = 0; j < 5; j++) {
                int col = scelte.get(j);
                int min = (col == 0) ? 1 : col * 10;
                int max = (col == 8) ? 90 : col * 10 + 9;
                int n;
                do {
                    n = min + rnd.nextInt(max - min + 1);
                } while (!usati.add(n)); // evita doppioni nella cartella
                r[i][j] = n;
            }
        }
        return new Cartella(r);
    }

    /** Ricostruisce la cartella dal formato di rete "a,b,c,d,e;f,g,...;...". */
    public static Cartella daStringa(String s) {
        String[] parti = s.split(";");
        int[][] r = new int[3][5];
        for (int i = 0; i < 3; i++) {
            String[] numeri = parti[i].split(",");
            for (int j = 0; j < 5; j++) {
                r[i][j] = Integer.parseInt(numeri[j].trim());
            }
        }
        return new Cartella(r);
    }

    /** Formato di rete. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) sb.append(';');
            for (int j = 0; j < 5; j++) {
                if (j > 0) sb.append(',');
                sb.append(righe[i][j]);
            }
        }
        return sb.toString();
    }

    public boolean contiene(int n) {
        for (int[] riga : righe)
            for (int x : riga)
                if (x == n) return true;
        return false;
    }

    /** Verifica se, con i numeri usciti, la cartella realizza il premio indicato. */
    public boolean haPremio(String premio, Set<Integer> usciti) {
        int idx = Arrays.asList(PREMI).indexOf(premio);
        if (idx < 0) return false;
        int soglia = SOGLIE[idx];

        int totale = 0, maxRiga = 0;
        for (int[] riga : righe) {
            int cnt = 0;
            for (int x : riga)
                if (usciti.contains(x)) cnt++;
            totale += cnt;
            maxRiga = Math.max(maxRiga, cnt);
        }
        return (soglia == 15) ? totale == 15 : maxRiga >= soglia;
    }

    /** Disegno 3x9 della cartella; i numeri segnati appaiono tra parentesi quadre. */
    public String disegna(Set<Integer> segnati) {
        String bordo = "+----+----+----+----+----+----+----+----+----+\n";
        StringBuilder sb = new StringBuilder(bordo);
        for (int[] riga : righe) {
            String[] celle = new String[9];
            Arrays.fill(celle, "    ");
            for (int n : riga) {
                int col = (n == 90) ? 8 : n / 10;
                celle[col] = segnati.contains(n) ? String.format("[%2d]", n) : String.format(" %2d ", n);
            }
            sb.append('|');
            for (String c : celle) sb.append(c).append('|');
            sb.append('\n').append(bordo);
        }
        return sb.toString();
    }
}