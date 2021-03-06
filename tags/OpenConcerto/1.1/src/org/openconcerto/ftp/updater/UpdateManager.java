/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2011 OpenConcerto, by ILM Informatique. All rights reserved.
 * 
 * The contents of this file are subject to the terms of the GNU General Public License Version 3
 * only ("GPL"). You may not use this file except in compliance with the License. You can obtain a
 * copy of the License at http://www.gnu.org/licenses/gpl-3.0.html See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each file.
 */
 
 package org.openconcerto.ftp.updater;

import org.openconcerto.ftp.FTPUtils;
import org.openconcerto.ftp.IFtp;
import org.openconcerto.utils.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Properties;

import javax.swing.JOptionPane;

public class UpdateManager implements Runnable {
    private Thread thread;
    private String login;
    private String pass;
    private String server;
    private String file;
    private static boolean stop = false;

    private static final int UPDATE_COUNT = 2;
    private static int counter = UPDATE_COUNT;
    private boolean enabled;

    UpdateManager() {

        final Properties props = new Properties();
        final File f = new File("Configuration/update.properties");
        if (f.exists()) {
            try {
                props.load(new FileInputStream("Configuration/update.properties"));
                this.login = props.getProperty("login");
                this.pass = props.getProperty("pass");
                this.server = props.getProperty("ftpserver");
                this.file = props.getProperty("file");
                this.enabled = Boolean.parseBoolean(props.getProperty("enabled"));
                if (!this.enabled) {
                    System.out.println("Mise à jour désactivées");
                } else {
                    this.thread = new Thread(this);
                    this.thread.setDaemon(true);
                    this.thread.setPriority(Thread.MIN_PRIORITY);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Mise à jour désactivées (fichier de configuration manquant)");
        }

    }

    public static synchronized void start() {

        UpdateManager u = new UpdateManager();
        if (!u.isStarted()) {
            u.startWatcher();
        } else {
            throw new RuntimeException("UpdateManager already started");
        }
    }

    public static synchronized void stop() {
        stop = true;
    }

    public static synchronized void forceUpdate() {
        counter = UPDATE_COUNT;

    }

    private boolean isStarted() {
        if (thread == null) {
            return false;
        }
        return this.thread.isAlive();
    }

    private void startWatcher() {
        if (this.enabled) {
            this.thread.start();
        }
    }

    @Override
    public void run() {
        System.err.println("UpdateManager started");

        while (!stop) {
            if (counter >= UPDATE_COUNT) {
                counter = 0;
                // Update
                final IFtp ftp = new IFtp();
                BufferedReader bReaderRemote = null;
                try {
                    ftp.connect(this.server);

                    boolean logged = ftp.login(this.login, this.pass);

                    if (!logged) {
                        throw new IllegalStateException("Identifiants refusés");
                    }
                    bReaderRemote = new BufferedReader(new InputStreamReader(ftp.retrieveFileStream(this.file)));

                    int newVersion = Integer.parseInt(bReaderRemote.readLine());

                    BufferedReader bReaderLocal = null;
                    int currentVersion = -1;
                    try {
                        bReaderLocal = new BufferedReader(new FileReader(".version"));
                        currentVersion = Integer.parseInt(bReaderLocal.readLine());
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        System.err.println(".version manquant");

                    } finally {
                        if (bReaderLocal != null) {
                            bReaderLocal.close();
                        }
                    }

                    // / marche pas: ftp.quit();

                    if (newVersion > currentVersion) {
                        Object[] options = { "Maintenant", "Plus tard" };
                        int res = JOptionPane.showOptionDialog(null, "Une mise à jour est disponible. Installer la mise à jour:", "Mises à jour automatiques", JOptionPane.DEFAULT_OPTION,
                                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                        if (res == 0) {
                            update(newVersion);
                        }

                    }

                } catch (Exception e) {
                    Object[] options = { "Réessayer dans 1 minute", "Abandonner" };
                    int res = JOptionPane.showOptionDialog(null, "Impossible de se connecter au serveur de mises à jour.\n" + e.getMessage(), "Mises à jour automatiques", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                    if (res == 0) {
                        counter--;
                    } else {
                        stop = true;
                    }
                    e.printStackTrace();

                } finally {
                    try {
                        System.err.println("Disconnect start");
                        if (bReaderRemote != null) {
                            bReaderRemote.close();
                        }
                        // ftp.abort();
                        // ftp.logout();
                        // ftp.disconnect();
                        ftp.quit();
                        System.err.println("Disconnected start");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
            if (!stop) {
                try {
                    // Sleep 1 minute
                    Thread.sleep(60 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            counter++;
        }
        System.err.println("UpdateManager stopped");
    }

    private void update(int toVersion) {
        System.out.println("Update application");
        File tempDir = new File("Update");
        if (tempDir.exists()) {
            FileUtils.rmR(tempDir);

        }
        tempDir.mkdir();
        File f = new File("Update/.version");
        FileOutputStream fOp;
        try {
            fOp = new FileOutputStream(f);

            PrintStream out = new PrintStream(fOp);
            out.println(toVersion);
            out.close();
        } catch (FileNotFoundException e1) {
            JOptionPane.showMessageDialog(null, "Impossible de créer le .version");
        }
        final IFtp ftp = new IFtp();
        try {
            ftp.connect(this.server);
            boolean logged = ftp.login(this.login, this.pass);
            if (!logged) {
                JOptionPane.showMessageDialog(null, "Impossible d'accéder au serveur FTP pour récupérer les fichiers");
                ftp.disconnect();
                return;
            }
            String dir = this.file;
            int i = dir.lastIndexOf('.');
            if (i > 0) {
                dir = dir.substring(0, i);
            }

            boolean cwdOk = ftp.changeWorkingDirectory(dir);
            if (!cwdOk) {
                JOptionPane.showMessageDialog(null, "Impossible d'accéder au dossier " + dir + " du serveur FTP");
                ftp.disconnect();
                return;
            }
            FTPUtils.saveR(ftp, tempDir);

            if (tempDir.getAbsolutePath().contains("workspace") && !tempDir.getAbsolutePath().contains("dist")) {
                JOptionPane.showMessageDialog(null, "Mise à jour desactivée en version non 'dist'");
                ftp.disconnect();
                return;
            }
            final String updaterFilename = "Update/update.exe";
            if (!new File(updaterFilename).exists()) {
                JOptionPane.showMessageDialog(null, "Le fichier 'Update/update.exe' est manquant.");
                ftp.disconnect();
                return;
            }
            Runtime.getRuntime().exec(updaterFilename);
            ftp.disconnect();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Impossible de récupérer les fichiers");

        }

    }
}
