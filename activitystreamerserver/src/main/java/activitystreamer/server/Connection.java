package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

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
    private boolean hasLoggedOut;

    private static final boolean DEBUG = true;
    private static final boolean PRINT_SERVER_STATUS = false;

    Connection(Socket socket) throws IOException {

        in = new DataInputStream(socket.getInputStream());
        inreader = new BufferedReader(new InputStreamReader(in));
        out = new DataOutputStream(socket.getOutputStream());
        outwriter = new PrintWriter(out, true);
        this.socket = socket;
        open = true;
        hasLoggedOut = false;
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
            printDebugMessages(msg, true);
            return true;
        }
        return false;
    }

    /** Prints a message to assist in debugging
     * @param msg The message to be printed
     * @param sending If the message is in the process of being sent */
    private synchronized void printDebugMessages(String msg, boolean sending) {

        if (PRINT_SERVER_STATUS) {
            // Print out the status of the server
            JSONObject msgJson = MessageProcessor.toJson(msg, false, "");
            if (msgJson.containsKey("command") && msgJson.get("command").toString().equals("SERVER_ANNOUNCE")) {
                String serverHostname = msgJson.get("hostname").toString();
                int serverLoad = ((Long) msgJson.get("load")).intValue();
                int serverPort = ((Long) msgJson.get("port")).intValue();
                String registryString = msgJson.get("registry").toString();
                log.info("    Server " + serverPort + " has load: " + serverLoad + ". Registry: " + registryString);
            }
            else if (DEBUG) {
                String messageAction = (sending ? "Sending: " : "Receiving: ");
                log.info(messageAction + msg);
            }
        }
        if (DEBUG) {
            String messageAction = (sending ? "Sending: " : "Receiving: ");
            log.info(messageAction + msg);
        }
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
                open = false;
            }
            catch (IOException e) {
                // already closed?
                log.error("received exception closing the connection " + Settings.socketAddress(socket) + ": " + e);
                open = false;
            }
        }
    }

    /**
     * Using threaded connections so server can have multiple clients - runs the connection thread whilst the server
     * still exists, and there are messages being sent to the connection.
     */
    public void run() {
        String closeContext;
        try {
            String data;
            while (!term && (data = inreader.readLine()) != null) {
                term = SessionManager.getInstance().process(this, data);
                // System.out.println("Processing: " + data);
                printDebugMessages(data, false);
            }
            log.debug("connection closed to " + Settings.socketAddress(socket) + " after reading null data");

            // Ensure the user is recorded as being logged out in the ClientRegistry
            SessionManager.getInstance().ensureLogoutDisconnectedClient(this);
            closeContext = "Close Connection Context: connection closed naturally (in run, in Connection)";
            System.out.println(closeContext);

        }
        catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            closeContext = "Close Connection Context: connection closed with exception (in run, in Connection)";
            System.out.println(closeContext);
        }
        finally {
            boolean isParent = SessionManager.getInstance().getServerRegistry().isParentConnection(this);
            System.out.println("Is this parent connection? Answer: " + isParent);
            if (isParent && !SessionManager.getInstance().isReconnecting()) {
                SessionManager.getInstance().reconnectParentIfDisconnected();
            }
            open = false;
            SessionManager.getInstance().deleteClosedConnection(this);
            this.closeCon();
        }
    }

    /** Checks if a connection is open
     * @return returns true if connection open, false otherwise */
    public boolean isOpen() {
        return this.open;
    }

    @Override
    /** Compares two connections to check if the same
     * @param obj The connection we are comparing to
     * @return true if connections are the same/match, false otherwise */
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!Connection.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final Connection other = (Connection) obj;
        if ((this.socket == null) ? (other.socket != null) : !(Settings.socketAddress(this.socket).equals(Settings.socketAddress(other.socket)))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.socket != null ? (Settings.socketAddress(this.socket)).hashCode() : 0);
        return hash;
    }

    /** Gets the port number of the given connection
     * @return the connection's port number */
    public Integer getPort() {
        return this.socket.getLocalPort();
    }

    /** Gets the hostname of the given connection
     * @return the connection's hostname*/
    public String getHostname() {
        String localAddress = this.socket.getLocalAddress().toString();
        return localAddress.replaceAll("[\\,/]", "");
        // return this.socket.getInetAddress().toString();
    }

    @Override
    public String toString() {
        return "(Local Hostname & Port: " + getHostname() + ":" + getPort() + "; SocketAddress: " +
                Settings.socketAddress(this.socket) + "; RemoteSocketAddress: " + this.socket.getRemoteSocketAddress() + ")";
    }

}
