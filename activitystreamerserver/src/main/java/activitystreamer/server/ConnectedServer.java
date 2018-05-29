package activitystreamer.server;


import java.util.Objects;

/** Represents a server that a given server knows about, but may not have a direct connection to */
public class ConnectedServer implements Comparable<ConnectedServer> {

    // Fields we want to store about a given server
    private String id;
    private int load;
    private String hostname;
    private int port;

    public ConnectedServer(String id, String hostname, int port) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    public String getId() {
        return this.id;
    }

    public int getLoad() {
        return this.load;
    }

    public String getHostname() {
        return this.hostname;
    }

    public int getPort() {
        return this.port;
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


    @Override
    public String toString() {
        return id + hostname + Integer.toString(port);
    }
}
