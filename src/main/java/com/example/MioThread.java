package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MioThread extends Thread {
    private static final String[] ESTENSIONI_AMMESSE = {"txt", "md", "csv"};
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
                inviaErrore(out, "ERR SYNTAX");
                return;
            }

            String[] parti = input.trim().split("\\s+");

            // Controllo comando e numero di argomenti
            if (parti.length != 2 || !parti[0].equals("LEGGI")) {
                inviaErrore(out, "ERR SYNTAX");
                return;
            }

            String nomeFile = parti[1];

            if (nomeFile.contains(".") || nomeFile.contains("/") || nomeFile.contains("\\") || nomeFile.contains(" ")) {
                inviaErrore(out, "ERR SYNTAX");
                return;
            }

            File cartella = new File("documenti");
            File fileTrovato = null;
            String estensioneTrovata = null;

            // Ricerca nell'ordine esatto dell'array: txt, md, csv
            for (String est : ESTENSIONI_AMMESSE) {
                File candidato = new File(cartella, nomeFile + "." + est);
                if (candidato.isFile()) {
                    fileTrovato = candidato;
                    estensioneTrovata = est;
                    break;
                }
            }

            if (fileTrovato == null) {
                inviaErrore(out, "ERR NOTFOUND");
                return;
            }

            // Invio risposta positiva secondo il protocollo della Tappa 2
            try (BufferedReader lettoreFile = new BufferedReader(
                    new InputStreamReader(new FileInputStream(fileTrovato), StandardCharsets.UTF_8))) {
                
                out.println("OK FILE");
                out.println("TYPE " + estensioneTrovata);
                out.println(); // Riga vuota che chiude l'intestazione

                String riga;
                while ((riga = lettoreFile.readLine()) != null) {
                    out.println(riga);
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                inviaErrore(out, "ERR INTERNAL");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void inviaErrore(PrintWriter out, String messaggioErrore) {
        out.println(messaggioErrore);
        out.println(); // Riga vuota dopo la riga di stato (senza intestazioni)
    }
}