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

    public ConnectedServer(String id, String hostname, int port, boolean isChild, boolean isParent) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.last_announce = LocalDateTime.now();
        this.is_child = isChild;
        this.is_parent = isParent;
        this.is_sibling = false;            // TODO: Do we determine this now or later?
        this.is_connected = true;
    }

    public void updateServer(int load, boolean isChild) {
        setLoad(load);
        this.last_announce = LocalDateTime.now();
        this.is_child = isChild;
    }

    public boolean isTimedOut() {
        if (last_announce != null) {
            LocalDateTime timeNow = LocalDateTime.now();
            LocalDateTime timeoutTime = last_announce.plusSeconds(20);
            return (timeNow.isAfter(timeoutTime));
        }
        last_announce = LocalDateTime.now();
        return false;
    }

    public void setConnectionStatus(boolean isConnected) {
        this.is_connected = isConnected;
    }

    public boolean isConnected() {
        return this.is_connected;
    }

    public void resetTimeout() {
        last_announce = LocalDateTime.now();
    }

    public void setLoad(Integer load) {
        this.load = load;
    }

    public String getId() {
        return this.id;
    }

    public Integer getLoad() {
        return this.load;
    }

    public String getHostname() {
        return this.hostname;
    }

    public int getPort() {
        return this.port;
    }

    public boolean isChild() {
        return is_child;
    }

    public boolean isParent() {
        return is_parent;
    }

    public boolean isSibling() {
        return is_sibling;
    }

    public void setIsSibling(boolean isSibling) {
        this.is_sibling = isSibling;
    }

    public void setIsParent(boolean isParent) {
        this.is_parent = isParent;
    }

    public void setIsChild(boolean isChild) {
        this.is_child = isChild;
    }



    // ----------------------------------- COMPARING ConnectedServers -----------------------------------
    @Override
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

    public int compareTo(ConnectedServer anotherMessage) {
        return anotherMessage.getId().compareTo(this.id);
    }

    public JSONObject toJson() {
        String thisJsonString = MessageProcessor.getGson().toJson(this);
        return MessageProcessor.toJson(thisJsonString, false, "");

    }


    @Override
    public String toString() {
        return id + hostname + Integer.toString(port);
    }
}
