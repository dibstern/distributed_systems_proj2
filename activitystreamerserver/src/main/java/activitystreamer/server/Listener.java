package activitystreamer.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;

/**
 * A class that listens for connections on a given port number, and handles the logic for accepting these
 * new connections.
 */
public class Listener extends Thread {
    private static final Logger log = LogManager.getLogger();
    private ServerSocket serverSocket = null;
    private boolean term = false;
    private int portnum;

    public Listener() throws IOException {
        portnum = Settings.getLocalPort(); // keep our own copy in case it changes later
        serverSocket = new ServerSocket(portnum);
        start();
    }

    /** Listens for new connections whilst the given server is alive.
     * Handles the accepting of new connections.  */
    @Override
    public void run() {
        log.info("listening for new connections on " + portnum);
        while (!term) {
            Socket connectionSocket;
            try {
                connectionSocket = serverSocket.accept();
                SessionManager.getInstance().incomingConnection(connectionSocket);
            }
            catch (IOException e) {
                log.info("received exception, shutting down");
                term = true;
            }
        }
    }

    /**
     * Sets term, which indicates if the server is running or not
     * @param term Server status
     */
    public void setTerm(boolean term) {
        this.term = term;
        if (term) interrupt();
    }


}
