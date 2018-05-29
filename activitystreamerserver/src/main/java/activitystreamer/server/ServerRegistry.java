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
        String hostname = record.get("hostname").toString();
        int port = (int) record.get("port");
        return new ConnectedServer(hostname, port);
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
    public JSONObject getGrandparent() {
        String jsonGrandparent = MessageProcessor.getGson().toJson(this.grandparent);
        return MessageProcessor.toJson(jsonGrandparent, false, "grandparent");
    }

    public JSONObject getParent() {

        String jsonParent = MessageProcessor.getGson().toJson(this.parent);
        return MessageProcessor.toJson(jsonParent, false, "parent");
    }

    public JSONObject getSiblingRoot() {
        String jsonSibling = MessageProcessor.getGson().toJson(this.siblingRoot);
        return MessageProcessor.toJson(jsonSibling, false, "sibling");
    }

    public void setGrandparent(JSONObject newGrandparent) {
        this.grandparent = createNewRecord(newGrandparent);
    }

    public void setParent(String hostname, int port) {
        this.parent = new ConnectedServer(hostname, port);
    }

    public void setSiblingRoot(JSONObject newSiblingRoot ) {
        this.siblingRoot = createNewRecord(newSiblingRoot);
    }


}
