package activitystreamer.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;

/**
 *
 */
public class ServerRegistry {

    private ArrayList<ConnectedServer> childServers;
    private ConnectedServer grandparent;
    private ConnectedServer parent;
    private ConnectedServer siblingRoot;


    public ServerRegistry(ConnectedServer grandparent, ConnectedServer parent, ConnectedServer sibling)
    {
        this.childServers = new ArrayList<ConnectedServer>();
        this.grandparent = grandparent;
        this.parent = parent;
        this.siblingRoot = sibling;
    }

    private ConnectedServer createNewRecord(JSONObject record)
    {
        String id = record.get("id").toString();
        String hostname = record.get("hostname").toString();
        int port = (int) record.get("port");
        return new ConnectedServer(id, hostname, port);
    }

    // A child server has crashed -- remove from ArrayList
    public void removeChildConnection(ConnectedServer child) {
        childServers.remove(child);
    }

    public void addChildConnection(ConnectedServer child) {
        childServers.add(child);
    }

    // Set the first child server in the ArrayList as the designated child root
    public ConnectedServer designateChildAsRoot() {
        return childServers.get(0);
    }

    // GETTERS AND SETTERS
    public ConnectedServer getGrandparent() {
        return this.grandparent;
    }

    public ConnectedServer getParent() {
        return this.parent;
    }

    public ConnectedServer getSiblingRoot() {
        return this.siblingRoot;
    }

    public void setGrandparent(JSONObject newGrandparent) {
        this.grandparent = createNewRecord(newGrandparent);
    }

    public void setParent(JSONObject newParent) {
        this.parent = createNewRecord(newParent);
    }

    public void setSiblingRoot(JSONObject newSiblingRoot ) {
        this.siblingRoot = createNewRecord(newSiblingRoot);
    }


}
