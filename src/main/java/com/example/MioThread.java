package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MioThread extends Thread {
    private static final String VERSIONE_PROTOCOLLO = "ARCH/1.0";

    private static final String[][] TIPI = {
        {"txt", "text/plain; charset=utf-8"},
        {"md",  "text/markdown; charset=utf-8"},
        {"csv", "text/csv; charset=utf-8"}
    };

    private Socket s;

    public MioThread(Socket s) {
        this.s = s;
    }

    @Override
    public void run() {
        try (
            Socket socket = this.s;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            System.out.println("connesso");

            String input = in.readLine();
            if (input == null || input.trim().isEmpty()) {
                inviaRisposta(out, "400 Bad Request", "text/plain; charset=utf-8", "Richiesta vuota o non valida.\n");
                return;
            }

            String[] parti = input.trim().split("\\s+");

            // 1. Parti diverse da tre -> 400 Bad Request
            if (parti.length != 3) {
                inviaRisposta(out, "400 Bad Request", "text/plain; charset=utf-8", "Sintassi errata. Uso corretto: LEGGI /risorsa ARCH/1.0\n");
                return;
            }

            String comando = parti[0];
            String risorsa = parti[1];
            String versione = parti[2];

            // 2. Risorsa che non inizia con / -> 400 Bad Request
            if (!risorsa.startsWith("/")) {
                inviaRisposta(out, "400 Bad Request", "text/plain; charset=utf-8", "La risorsa deve iniziare con '/'.\n");
                return;
            }

            // 3. Versione che non inizia con ARCH/ -> 400 Bad Request
            if (!versione.startsWith("ARCH/")) {
                inviaRisposta(out, "400 Bad Request", "text/plain; charset=utf-8", "Formato versione non valido.\n");
                return;
            }

            // 4. Versione ARCH/ diversa da ARCH/1.0 -> 505 Version Not Supported
            if (!versione.equals(VERSIONE_PROTOCOLLO)) {
                inviaRisposta(out, "505 Version Not Supported", "text/plain; charset=utf-8", "Versione del protocollo non supportata.\n");
                return;
            }

            // 5. Comando diverso da LEGGI -> 501 Not Implemented
            if (!comando.equals("LEGGI")) {
                inviaRisposta(out, "501 Not Implemented", "text/plain; charset=utf-8", "Comando \"" + comando + "\" non supportato.\n");
                return;
            }
            
            // Estrazione del nome file rimuovendo la '/' iniziale
            String nomeFile = risorsa.substring(1);

            // Controllo del nome della Tappa 2 (vuoto, '.', '/', '\', spazi)
            if (nomeFile.isEmpty() || nomeFile.contains(".") || nomeFile.contains("/") || nomeFile.contains("\\") || nomeFile.contains(" ")) {
                inviaRisposta(out, "400 Bad Request", "text/plain; charset=utf-8", "Nome risorsa non valido.\n");
                return;
            }

            File cartella = new File("documenti");
            File fileTrovato = null;
            String contentTypeTrovato = null;

            // Ricerca nella tabella TIPI nell'ordine: txt, md, csv
            for (String[] rigaTipo : TIPI) {
                String estensione = rigaTipo[0];
                String mimeType = rigaTipo[1];

                File candidato = new File(cartella, nomeFile + "." + estensione);
                if (candidato.isFile()) {
                    fileTrovato = candidato;
                    contentTypeTrovato = mimeType;
                    break;
                }
            }

            if (fileTrovato == null) {
                inviaRisposta(out, "404 Not Found", "text/plain; charset=utf-8", "La risorsa \"" + risorsa + "\" non esiste.\n");
                return;
            }

            // Invio risposta positiva con prefisso ARCH/1.0
            try {
                byte[] dati = Files.readAllBytes(fileTrovato.toPath());
                String contenuto = new String(dati, StandardCharsets.UTF_8);

                out.println(VERSIONE_PROTOCOLLO + " 200 OK");
                out.println("Content-Type: " + contentTypeTrovato);
                out.println("Content-Length: " + dati.length);
                out.println(); // Riga vuota che chiude le intestazioni
                out.print(contenuto);
                out.flush();
            } catch (IOException e) {
                System.out.println(e.getMessage());
                inviaRisposta(out, "500 Internal Server Error", "text/plain; charset=utf-8", "Errore interno durante la lettura del file.\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Ogni risposta inizia ora con "ARCH/1.0 <codice> <frase>"
    private void inviaRisposta(PrintWriter out, String stato, String contentType, String corpo) {
        byte[] dati = corpo.getBytes(StandardCharsets.UTF_8);
        out.println(VERSIONE_PROTOCOLLO + " " + stato);
        out.println("Content-Type: " + contentType);
        out.println("Content-Length: " + dati.length);
        out.println();
        out.print(corpo);
        out.flush();
    }
}