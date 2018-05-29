package activitystreamer.server;

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

    // A child server has crashed -- remove from ArrayList
    public void removeChildConnection(ConnectedServer child) {
        childServers.remove(child);
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

    public void setGrandparent(ConnectedServer newGrandparent) {
        this.grandparent = newGrandparent;
    }

    public void setParent(ConnectedServer newParent) {
        this.parent = newParent;
    }

    public void setSiblingRoot(ConnectedServer newSiblingRoot) {
        this.siblingRoot = newSiblingRoot;
    }


}
