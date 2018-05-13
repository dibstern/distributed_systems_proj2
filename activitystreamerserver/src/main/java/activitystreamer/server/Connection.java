package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;

/** This class handles all of the connections a server has */
public class Connection extends Thread {
    private static final Logger log = LogManager.getLogger();
    private DataInputStream in;
    private DataOutputStream out;
    private BufferedReader inreader;
    private PrintWriter outwriter;
    private boolean open = false;
    private Socket socket;
    private boolean term = false;

    Connection(Socket socket) throws IOException {

        in = new DataInputStream(socket.getInputStream());
        inreader = new BufferedReader(new InputStreamReader(in));
        out = new DataOutputStream(socket.getOutputStream());
        outwriter = new PrintWriter(out, true);
        this.socket = socket;
        open = true;
        start();
    }

    /**
     * Returns true if the message was written, otherwise false
     * @param msg The message to be written
     * @return true if message successfully written, otherwise false
     */
    public boolean writeMsg(String msg) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (open) {
            outwriter.println(msg);
            return true;
        }
        return false;
    }

    /**
     * Closes a connection and handles appropriate shutdown
     */
    public void closeCon() {
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                term = true;
                inreader.close();
                out.close();
            }
            catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }

    /**
     * Using threaded connections so server can have multiple clients - runs the connection thread whilst the server
     * still exists, and there are messages being sent to the connnection.
     */
    public void run() {
        try {
            String data;
            while (!term && (data = inreader.readLine()) != null) {
                term = Control.getInstance().process(this, data);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket));
            Control.getInstance().deleteClosedConnection(this);
            in.close();
        } catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            Control.getInstance().deleteClosedConnection(this);
        }
        open = false;
    }


}
