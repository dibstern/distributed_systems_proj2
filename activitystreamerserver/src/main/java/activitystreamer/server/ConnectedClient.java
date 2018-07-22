package activitystreamer.server;

/** This class holds information about a given client the server is directly connected to */

public class ConnectedClient {
    private String username;
    private String secret;
    private int lockRequestServerCount;
    private boolean registered;

    /** Stores basic information about a particular client
     * @param username The client's username
     * @param password The client's password */
    public ConnectedClient(String username, String password) {
        this.username = username;
        this.secret = password;
        this.registered = true;
    }

    /** Stores basic information about a particular client
     * @param username The client's username
     * @param password The client's password
     * @param numKnownServers  The number of servers a given server currently knows about */
    public ConnectedClient(String username, String password, int numKnownServers) {
        this.username = username;
        this.secret = password;
        this.lockRequestServerCount = numKnownServers;
        if (numKnownServers == 0) {
            this.registered = true;
        }
        else {
            this.registered = false;
        }
    }

    /** Increments the number of LOCK_ALLOWED messages received */
    public boolean receivedLockAllowed() {

        if (isRegistered()) {
            System.out.println("In receivedLockAllowed(). Is registered - returning false");
            return false;
        }
        System.out.println("Decrementing lockRequestServerCount");
        this.lockRequestServerCount -= 1;
        if (this.lockRequestServerCount == 0) {

            register();
        }
        else {
            System.out.println("Server Count is not yet zero - it is currently " + this.lockRequestServerCount + " .");
        }
        System.out.println("Registered client? returning isRegistered() == " + isRegistered());
        return isRegistered();
    }

    /*
     * Getters and Setters
     */

    /** Returns the username of a given connected client */
    public String getUsername() {
        return this.username;
    }

    /** Returns the secret of a given connected client */
    public String getSecret() {
        return this.secret;
    }

    /** Checks if username and secret matches client records
     * @param username Username of the client
     * @param secret The secret of the client
     * @return Returns true if username and secret match record, false otherwise */
    public boolean isClient(String username, String secret) {
        return this.username.equals(username) && this.secret.equals(secret);
    }

    /** Sets a client to registered */
    public void register() {
        this.registered = true;
    }

    /** Checks if the client is registered with the network
     * @return true if client is registered, false otherwise */
    public boolean isRegistered() {
        return this.registered;
    }
}
