package activitystreamer.client;

import activitystreamer.util.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * To Understand:
 * https://stackoverflow.com/a/18224009         -> But I used ExecutorService (a subinterface of Executor) to get .shutdownNow();
 * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html
 * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html
 */

public class ConnectionManager {

    private ExecutorService executor;
    private static ClientConnection clientConnection;
    private static ConnectionManager connectionManager;
    private TextFrame textFrame;
    private static final Logger log = LogManager.getLogger();

    // Singleton Pattern
    public static ConnectionManager getInstance() {
        if (connectionManager == null) {
            connectionManager = new ConnectionManager();
        }
        return connectionManager;
    }

    // Singleton Pattern for ClientConnectionNew
    public static ClientConnection getInstanceClientConnection() {
        if (clientConnection == null) {
            clientConnection = new ClientConnection();
        }
        return clientConnection;
    }

    /**
     * Starts a GUI instance, uses an ExecutorService to manage connection threads (open & close)
     */
    public ConnectionManager() {

        // Start the GUI
        textFrame = new TextFrame();

        // Instantiate the executor and Client Connection
        executor = Executors.newSingleThreadExecutor();
        clientConnection = getInstanceClientConnection();

        // Execute the client connection
        executor.execute(clientConnection);
    }

    /**
     *  Restarts the connection by shutting down the current connection thread and starting a new one.
     */
    public void restartConnection() {
        log.info("Redirecting...");
        executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
        clientConnection = new ClientConnection();
        executor.execute(clientConnection);
        log.info("Reconnected to " +  getInstanceClientConnection().getSocketAddress());
    }

    /** Displays a given message to the GUI
     * @param obj The object to be displayed
     * @param sentFromUs If the message was originally sent by this client*/
    public void displayMsg(JSONObject obj, boolean sentFromUs) {
        textFrame.setOutputText(obj, sentFromUs);
    }

    /**
     *  Shows the given message to the user on the GUI
     * @param msg (String) the message to show the user on the GUI
     */
    public void showMsgToUser(String msg) {
        textFrame.showClientMessage(msg);
    }

    /**
     * A getter for the TextFrame (GUI) object
     * @return (TextFrame) the GUI object
     */
    public TextFrame getTextFrame() {
        return this.textFrame;
    }


}
