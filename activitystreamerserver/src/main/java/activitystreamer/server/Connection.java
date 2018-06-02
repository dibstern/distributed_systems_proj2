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

    private synchronized void printDebugMessages(String msg, boolean sending) {

        if (PRINT_SERVER_STATUS) {
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
    public void closeCon(String thisServerId) {
        if (open) {
            log.info("closing connection " + Settings.socketAddress(socket));
            try {
                try {
                    if (!hasLoggedOut) {
                        writeMsg(MessageProcessor.getShutdownMessage(thisServerId));
                    }
                }
                catch (Exception e) {
                    // do nothing
                }
                finally {
                    // Sleep for 2 seconds to allow the receipt of shutdown messages to prompt servers to close connections gracefully
                    // SessionManager.getInstance().delayThread(2000);
                    term = true;
                    inreader.close();
                    out.close();
                    open = false;
                }
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
     *
     * TODO: Insert while(retry) loop and a 2 sec delay after a broken network connection
     */
    public void run() {

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

            String closeContext = "Close Connection Context: connection closed naturally (in run, in Connection)";
            // retry = false;
            // Connection is crashed, not closed gracefully. Thus, reconnect to a new parent
            if (SessionManager.getInstance().getServerRegistry().isParentConnection(this) &&
                    !SessionManager.getInstance().isReconnecting()) {
                SessionManager.getInstance().reconnectParentIfDisconnected();
            }
            SessionManager.getInstance().deleteClosedConnection(this, closeContext);
            this.setHasLoggedOut(true);
            this.closeCon(SessionManager.getServerId());
        }
        catch (IOException e) {
            log.error("connection " + Settings.socketAddress(socket) + " closed with exception: " + e);
            String closeContext = "Close Connection Context: connection closed with exception (in run, in Connection)";
            System.out.println(closeContext);
            boolean isParent = SessionManager.getInstance().getServerRegistry().isParentConnection(this);
            System.out.println("Is this parent connection? Answer: " + isParent);
            if (isParent && !SessionManager.getInstance().isReconnecting()) {
                SessionManager.getInstance().reconnectParentIfDisconnected();
            }
        }
        finally {
            open = false;
            // SessionManager.getInstance().reconnectParentIfDisconnected();
        }
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setHasLoggedOut(boolean loggedOut) {
        this.hasLoggedOut = loggedOut;
    }

    @Override
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

    public Integer getPort() {
        return this.socket.getLocalPort();
        // return this.socket.getPort()
    }

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
