package activitystreamer.server;


import java.util.Objects;

/** Represents a server that a given server knows about, but may not have a direct connection to */
public class ConnectedServer {

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        ConnectedServer server = (ConnectedServer) o;

        // Check their ids are the same - if so, they're the same!
        return Objects.equals(this.getId(), server.getId());
    }
}
