package activitystreamer.server;


import activitystreamer.util.Settings;
import org.json.simple.JSONObject;

import java.time.LocalDateTime;
import java.util.Objects;

/** Represents a server that a given server knows about, but may not have a direct connection to */
public class ConnectedServer implements Comparable<ConnectedServer> {

    // Fields we want to store about a given server
    private String id;
    private Integer load;
    private String hostname;
    private int port;
    private LocalDateTime last_announce;
    private boolean is_child;
    private boolean is_parent;
    private boolean is_sibling;
    private boolean is_connected;

    /** Creates a new ConnectedServer
     * @param id The id of the new server
     * @param hostname The hostname of the new server
     * @param port The portnumber of the new server
     * @param isChild True if the new server is a child of this server
     * @param isParent True if the new server is the parent of this server  */
    public ConnectedServer(String id, String hostname, int port, boolean isChild, boolean isParent) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.last_announce = LocalDateTime.now();
        this.is_child = isChild;
        this.is_parent = isParent;
        this.is_sibling = false;
        this.is_connected = true;
    }

    /** Update the load of a server and if it is one of our children
     * @param load The current load of the server
     * @param isChild True if server is a child of ours, false otherwise */
    public void updateServer(int load, boolean isChild) {
        setLoad(load);
        this.last_announce = LocalDateTime.now();
        this.is_child = isChild;
    }

    /** Checks if a server connection has timed out
     * @return  true if server connection has timed out, false otherwise */
    public boolean isTimedOut() {
        if (last_announce != null) {
            LocalDateTime timeNow = LocalDateTime.now();
            LocalDateTime timeoutTime = last_announce.plusSeconds(20);
            // Check if time between last announce and now is greater than timeout boundary
            return (timeNow.isAfter(timeoutTime));
        }
        last_announce = LocalDateTime.now();
        return false;
    }

    /** Update the status of a connection to a server
     * @param isConnected true if server is connected, false otherwise */
    public void setConnectionStatus(boolean isConnected) {
        this.is_connected = isConnected;
    }

    /** Checks if a connected server is still connected
     * @return true if still connected, false otherwise */
    public boolean isConnected() {
        return this.is_connected;
    }

    /** Server sent a SERVER_ANNOUCEN, so update last_announce to current time*/
    public void resetTimeout() {
        last_announce = LocalDateTime.now();
    }

    /** Update the load of the server
     * @param load New server load*/
    public void setLoad(Integer load) {
        this.load = load;
    }

    /** Get a server's id
     * @return server's id */
    public String getId() {
        return this.id;
    }

    /**Get a server's current load
     * @return server's current load */
    public Integer getLoad() {
        return this.load;
    }

    /** Get the hostname of the server
     * @return server's hostname */
    public String getHostname() {
        return this.hostname;
    }

    /** Get the port number of the server
     * @return server's port number */
    public int getPort() {
        return this.port;
    }

    /** Check if the server is a child of ours
     * @return true if server is one of our children, false otherwise */
    public boolean isChild() {
        return is_child;
    }

    /** Check if the server is our parent
     * @return true if server is our parent, false otherwise */
    public boolean isParent() {
        return is_parent;
    }

    /** Check if the server is a sibling of ours
     * @return true if server is one of our siblings, false otherwise */
    public boolean isSibling() {
        return is_sibling;
    }

    /** Set the server as our sibling, if isSibling true
     * @param isSibling true is server is one of our siblings, false otherwise  */
    public void setIsSibling(boolean isSibling) {
        this.is_sibling = isSibling;
    }

    /** Set the server as our parent, if isParent true
     * @param isParent true if server is our parent, false otherwise  */
    public void setIsParent(boolean isParent) {
        this.is_parent = isParent;
    }

    /** Set the server as one of our children, if isChild true
     * @param isChild true is server is one of our children, false otherwise  */
    public void setIsChild(boolean isChild) {
        this.is_child = isChild;
    }



    // ----------------------------------- COMPARING ConnectedServers -----------------------------------
    @Override
    /** Checks/comapres one ConnectedServer to another
     * @param obj ConnectedServer record comparing to
     * @return true if same record, false otherwise */
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!ConnectedServer.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final ConnectedServer other = (ConnectedServer) obj;
        if ((this.id == null) ? (other.getId() != null) : !this.id.equals(other.getId())) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    /** Compares two messages to see if are the same
     * @param anotherMessage The message to compare to
     * @return returns the id of the messages */
    public int compareTo(ConnectedServer anotherMessage) {
        return anotherMessage.getId().compareTo(this.id);
    }

    /** Converts an object to a JSONObject for sending
     * @return JSONObject the JSONObject to be sent */
    public JSONObject toJson() {
        String thisJsonString = MessageProcessor.getGson().toJson(this);
        return MessageProcessor.toJson(thisJsonString, false, "");

    }


    @Override
    public String toString() {
        String child_status = (this.is_child ? "is child, " : "not child, ");
        String parent_status = (this.is_parent ? "is parent, " : "not parent, ");
        String sibling_status = (this.is_sibling ? "is sibling, " : "not sibling, ");
        String connected_status = (this.is_connected ? "is connected, " : "not connected, ");
        return "{" + hostname + ":" + Integer.toString(port) + " (id=" + id + "), load: " + load + ", last_announce: " +
                last_announce.toLocalTime() + ", " + child_status + parent_status + sibling_status + connected_status +
                "}";
    }
}
