package activitystreamer.server;

import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class ServerRegistry {

    private ArrayList<Connection> server_connections;
    private ConnectedServer grandparent;
    private ConnectedServer parent;
    private ConnectedServer child_root;
    private ConnectedServer this_server;
    private ArrayList<ConnectedServer> siblings_list;
    private Connection parentConnection;
    private ConcurrentHashMap<ConnectedServer, Connection> connectedChildServers;
    private ConcurrentHashMap<String, ConnectedServer> all_servers;

    public ServerRegistry(String id, Integer port, String hostname) {
        this.connectedChildServers = new ConcurrentHashMap<ConnectedServer, Connection>();
        this.child_root = null;
        this.this_server = new ConnectedServer(id, hostname, port, false, false);
        this.siblings_list = new ArrayList<ConnectedServer>();
        this.siblings_list.add(this_server);
        this.parent = null;
        this.parentConnection = null;
        this.server_connections = new ArrayList<Connection>();
        this.all_servers = new ConcurrentHashMap<String, ConnectedServer>();
    }

    private ConnectedServer createNewRecord(JSONObject record, boolean isChild, boolean isParent) {
        String id = record.get("id").toString();
        String hostname = record.get("hostname").toString();
        Integer port = ((Long) record.get("port")).intValue();
        return new ConnectedServer(id, hostname, port, isChild, isParent);
    }

    /** SERVER_ANNOUNCE message received. Update information about that server.
     *
     * @param id The sending server's ID
     * @param load The number of client connections the server has
     * @param hostname The host name of the server
     * @param port The port number of the server
     */
    public void updateRegistry(String id, int load, String hostname, int port, boolean isChild) {
        if (all_servers.containsKey(id)) {
            all_servers.get(id).updateServer(load, isChild);
        }
        else {
            ConnectedServer newConnectedServer = new ConnectedServer(id, hostname, port, isChild, false);
            all_servers.put(id, newConnectedServer);
        }
        if (!isChild) {
            parent.resetTimeout();
        }
    }

    // ------------------------------ PARENT MANAGEMENT ------------------------------

//    public ConnectedServer getNextParent() {
//
//    }

    public boolean isParentConnection(Connection con) {
        return con.equals(this.parentConnection);
    }

    public void setConnectedParent(String id, String hostname, int port, Connection con) {
        if (this.parent != null) {
            all_servers.remove(this.parent.getId());
        }
        this.parent = new ConnectedServer(id, hostname, port, false, true);
        this.parentConnection = con;
        if (!all_servers.containsKey(id)) {
            all_servers.put(id, this.parent);
        }
    }

    public void setNoParent() {
        all_servers.remove(this.parent.getId());
        this.parent = null;
        this.parentConnection = null;
        SessionManager.getInstance().reconnectParentIfDisconnected();
    }

    // ------------------------------ CHILD MANAGEMENT ------------------------------

    public boolean hasRootChild() {
        return child_root != null;
    }

    public ConnectedServer addRootChild(Connection con) {
        child_root = new ConnectedServer(con, true, false);
        all_servers.put(child_root.getId(), child_root);
        server_connections.add(con);
        connectedChildServers.put(child_root, con);
        return child_root;
    }

    public ConnectedServer addConnectedChild(Connection con) {
        ConnectedServer childServer = new ConnectedServer(con, true, false);
        all_servers.put(childServer.getId(), childServer);
        connectedChildServers.put(childServer, con);
        return childServer;
    }

    public void setNextRootChild() {
        connectedChildServers.remove(child_root);
        if (connectedChildServers.isEmpty()) {
            child_root = null;
        }
        else {
            ArrayList<ConnectedServer> childList = getConnectedChildList();
            child_root = childList.get(0);
        }
    }

    public ArrayList<ConnectedServer> getConnectedChildList() {
        ArrayList<ConnectedServer> childList = new ArrayList<ConnectedServer>();
        connectedChildServers.forEach((server, con) -> {
            childList.add(server);
        });
        Collections.sort(childList);
        return childList;
    }

    // ------------------------------ SIBLING MANAGEMENT ------------------------------

    public JSONObject childListToJson() {
        ArrayList<ConnectedServer> childServerList = getConnectedChildList();
        String childServersJsonString = MessageProcessor.getGson().toJson(childServerList);
        return MessageProcessor.toJson(childServersJsonString, true, "sibling_servers");
    }

    public void addSiblingsList(JSONArray siblingsList) {

        Type collectionType = new TypeToken<ArrayList<ConnectedServer>>(){}.getType();
        this.siblings_list = MessageProcessor.getGson().fromJson(siblingsList.toJSONString(), collectionType);
        this.siblings_list.forEach((sibling) -> {
            sibling.setIsChild(false);
            sibling.setIsParent(false);
            sibling.setIsSibling(true);

            if (!all_servers.containsKey(sibling.getId())) {
                all_servers.put(sibling.getId(), sibling);
            }
        });
        Collections.sort(this.siblings_list);
    }

    public void addSibling(JSONObject siblingJson) {
        Type collectionType = new TypeToken<ConnectedServer>(){}.getType();
        ConnectedServer sibling = MessageProcessor.getGson().fromJson(siblingJson.toJSONString(), collectionType);
        this.siblings_list.add(sibling);
        Collections.sort(this.siblings_list);
        if (!all_servers.containsKey(sibling.getId())) {
            all_servers.put(sibling.getId(), sibling);
        }
    }

    // ------------------------------ GENERAL ------------------------------

    public Integer numServersInNetwork() {
        return all_servers.size();
    }

    public ArrayList<Connection> getServerConnections() {
        return server_connections;
    }

    public ArrayList<ConnectedServer> getAllServers() {
        return new ArrayList<>(all_servers.values());
    }

    public void addServerCon(Connection con) {
        server_connections.add(con);
    }

    public boolean isServerCon(Connection con) {
        return server_connections.contains(con);
    }

    // ------------------------------ CONNECTION CLOSING ------------------------------

    public void closeServerCons() {
        server_connections.forEach((con) -> {
            con.writeMsg(MessageProcessor.getShutdownMessage());
        });
        server_connections.clear();
    }

    public void removeCon(Connection con) {
        con.writeMsg(MessageProcessor.getShutdownMessage());
        server_connections.remove(con);
        if (this.parentConnection.equals(con)) {
            setNoParent();
        }
        else if (connectedChildServers.get(this.child_root).equals(con)) {
            setNextRootChild();
        }
        else {
            siblings_list.removeIf((s) ->
                    con.getHostname().equals(s.getHostname()) && con.getPort().equals(s.getPort()));
        }
    }

    public void removeNotConnectedServer(ConnectedServer s) {
        if (grandparent == s) {
            setNoGrandparent();
        }
        if (parent == s) {
            setNoParent();
        }
        if (child_root == s) {
            setNoChildRoot();
        }
        siblings_list.removeIf((sibling) -> s.equals(sibling));
    }

    // ---------------------------------- GETTERS ----------------------------------
    public Connection getParentConnection() {
        return parentConnection;
    }

    public ConnectedServer getParentInfo() {
        return parent;
    }

    public JSONObject getParentJson() {

        String jsonParent = MessageProcessor.getGson().toJson(this.parent);
        return MessageProcessor.toJson(jsonParent, false, "parent");
    }

    // ---------------------------------- SETTERS ----------------------------------
    public void setGrandparent(JSONObject newGrandparent) {
        this.grandparent = createNewRecord(newGrandparent, false, false);
    }

    public void setNoGrandparent() {
        this.grandparent = null;
    }

    public void setNoChildRoot() {
        this.child_root = null;
    }

    public ConnectedServer getThisServer() {
        return this_server;
    }

    public JSONObject toJson() {
        JSONObject thisJson = new JSONObject();
        thisJson.putAll(childListToJson());
        thisJson.put("grandparent", getParentJson());
        return thisJson;
    }


    public boolean isServerConnection(Connection con) {
        return server_connections.contains(con);
    }




    public void setChildRoot(JSONObject newSiblingRoot ) {
        this.child_root = createNewRecord(newSiblingRoot, false, false);
    }

    public ConnectedServer getGrandparent() {
        return grandparent;
    }

    public ConnectedServer getChildRoot() {
        return child_root;
    }

    public void disconnectServer(Connection con) {
        removeCon(con);
        con.closeCon();
    }

    public JSONObject allServersToJson() {
        ArrayList<JSONObject> all_servers_jsons = new ArrayList<JSONObject>();
        all_servers.forEach((id, server_info) -> {
            all_servers_jsons.add(server_info.toJson());
        });
        String allServersJsonString = MessageProcessor.getGson().toJson(all_servers_jsons);
        return MessageProcessor.toJson(allServersJsonString, true, "all_servers");
    }

    public JSONObject getChildRootJson() {
        String jsonSibling = MessageProcessor.getGson().toJson(this.child_root);
        return MessageProcessor.toJson(jsonSibling, false, "child_root");
    }

    public JSONObject getGrandparentJson() {
        String jsonGrandparent = MessageProcessor.getGson().toJson(this.grandparent);
        return MessageProcessor.toJson(jsonGrandparent, false, "grandparent");
    }

    public void setServerStatuses() {
        all_servers.forEach((sid, server) -> {
            if (server.isTimedOut()) {
                server.setConnectionStatus(false);
            }
        });
    }

}
