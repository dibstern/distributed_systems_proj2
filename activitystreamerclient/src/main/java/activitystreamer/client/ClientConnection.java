package activitystreamer.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import activitystreamer.util.Settings;

/**
 * This class handles the running of a client, including the coordinate of sending and receiving messages to/from a
 * server and initiating a connection with a given server.
 */
public class ClientConnection implements Runnable  {
    private static final Logger log = LogManager.getLogger();
    // volatile -> https://stackoverflow.com/a/16506040 - TODO: Might not be necessary here. Was it meant for elsewhere?
    private volatile ClientSessionHandler sessionHandler;

    // Connection
    private Socket socket = null;

    // Message Setup
    private DataInputStream in;
    private BufferedReader inreader;
    private DataOutputStream out;
    private PrintWriter outwriter;

    // Managing Server connection
    private boolean open = false;
    private boolean term = false;

    public ClientConnection() {
        connectToServer();
        open = true;
    }

    /** Sends a message to the server
     * @param msg The message to be sent */
    public void sendMessage(JSONObject msg) {
        if (open) {
            outwriter.write(msg.toString() + "\n");
            outwriter.flush();
        }
    }

    /**
     * Sends a logout message and closes the connection
     */
    public void logout() {
        sendMessage(ClientMessageHandler.getLogoutMsg());
        disconnect();
    }

    /** Disconnects from a server by closing the connection and socket */
    public void disconnect() {
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                inreader.close();
                out.close();
                socket.close();
                open = false;
            }
            catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
            }
        }
    }

    /**
     * Connects to the server, starting up a client thread for that server.
     */
    public void connectToServer() {

        try {
            // Create a socket
            socket = new Socket(Settings.getRemoteHostname(), Settings.getRemotePort());

            // Setup Message Passing
            in = new DataInputStream(socket.getInputStream());
            inreader = new BufferedReader(new InputStreamReader(in));

            out = new DataOutputStream(socket.getOutputStream());
            outwriter = new PrintWriter(out, true);

            open = true;
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
            log.fatal("Failed to start up a client thread: " + e);      // Is this mandated? Why not "Failed to connect to server"?
            System.exit(-1);
        }
        catch (IOException e) {
            e.printStackTrace();
            log.fatal("Failed to start up a client thread: " + e);      // Is this mandated? Why not "Failed to connect to server"?
            System.exit(-1);
        }
    }

    /**
     * Sends an activity object
     * @param activityObj The object to be sent to the server
     */
    public void sendActivityObject(JSONObject activityObj) {
        sendMessage(ClientMessageHandler.getActivityObjectMsg(activityObj));
    }

    /**
     * Receives server messages and sends them to be processed.
     */
    public void run() {

        // Sets up Authentication & Login
        sessionHandler = new ClientSessionHandler();

        // Actively process Server Messages, close if ***process returns true*** (change this)
        try {
            String data = "";
            while (!term && (data = inreader.readLine()) != null) {
                term = sessionHandler.process(data);
            }
            // TODO: Close the GUI when disconnected! This code doesn't run if the Thread is shutdown (so GUI remains
            // TODO: intact for REDIRECTs)
            log.debug("disconnected from " + Settings.socketAddress(socket));
            log.debug("Message received: " + data);
            disconnect();
        }
        catch (IOException e) {
            log.error("disconnected from " + Settings.socketAddress(socket) + " closed with exception: " + e);
            disconnect();
        }
        open = false;
    }
}
