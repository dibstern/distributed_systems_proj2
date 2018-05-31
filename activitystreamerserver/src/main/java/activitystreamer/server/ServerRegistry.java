package activitystreamer.server;

import activitystreamer.util.Settings;
import com.google.gson.reflect.TypeToken;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 */
public class ServerRegistry {

    private ConcurrentHashMap<Connection, ConnectedServer> server_connections;
    private ArrayList<Connection> unauthorised_connections;
    private ConnectedServer grandparent;
    private ConnectedServer parent;
    private Connection parentConnection;
    private ConnectedServer child_root;
    private ConnectedServer this_server;
    private ArrayList<ConnectedServer> siblings_list;
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
        this.server_connections = new ConcurrentHashMap<Connection, ConnectedServer>();
        this.unauthorised_connections = new ArrayList<Connection>();
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
            System.out.println("Updating Registry -> Adding connection to server_connections: " + hostname + ":" + port);
            all_servers.put(id, newConnectedServer);
        }
        if (!isChild) {
            parent.resetTimeout();
        }
    }

    // ------------------------------ PARENT MANAGEMENT ------------------------------

    public boolean isParentConnection(Connection con) {
        return con.equals(this.parentConnection);
    }

    public ConnectedServer setConnectedParent(String id, String hostname, int port, Connection con) {
        System.out.println("Setting parent connection: " + con);
        if (this.parent != null) {
            all_servers.remove(this.parent.getId());
        }
        this.parent = new ConnectedServer(id, hostname, port, false, true);
        this.parentConnection = con;
        if (!all_servers.containsKey(id)) {
            System.out.println("Setting connected Parent -> Adding connection to server_connections: " + port);
            all_servers.put(id, this.parent);
        }
        System.out.println("this.parentConnection = " + this.parentConnection);
        unauthorised_connections.remove(con);
        server_connections.put(con, this.parent);

        String msg = MessageProcessor.getGrandparentUpdateMsg(getParentJson());
        SessionManager.getInstance().forwardServerMsg(con, msg);

        return this.parent;
    }

    public void setNoParent() {
        System.out.println("Removing Parent Connection" + this.parentConnection);
        all_servers.remove(this.parent.getId());
        this.parent = null;
        this.parentConnection = null;
        // TODO: Is the server_connections being
    }

    // ------------------------------ CHILD MANAGEMENT ------------------------------

    public boolean hasRootChild() {
        return child_root != null;
    }

    public ConnectedServer addRootChild(Connection con, String id, String hostname, Integer port) {
        System.out.println("Adding Root Child -> Adding connection to server_connections: " + port);
        child_root = new ConnectedServer(id, hostname, port, true, false);
        all_servers.put(child_root.getId(), child_root);
        server_connections.put(con, child_root);
        connectedChildServers.put(child_root, con);
        return child_root;
    }

    public ConnectedServer addConnectedChild(Connection con, String id, String hostname, Integer port) {
        ConnectedServer childServer = new ConnectedServer(id, hostname, port, true, false);
        System.out.println("Adding Connected Child -> Adding connection to server_connections: " + port);
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
                System.out.println("Adding Siblings List -> Adding connection to all_servers: " + sibling.getPort());
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

            System.out.println("Adding Siblings -> Adding connection to server_connections: " + sibling.getPort());
            all_servers.put(sibling.getId(), sibling);
        }
    }

    // ------------------------------ GENERAL ------------------------------

    public Integer numServersInNetwork() {
        return all_servers.size();
    }

    public ConcurrentHashMap<Connection, ConnectedServer> getServerConnections() {
        return server_connections;
    }

    public ArrayList<ConnectedServer> getAllServers() {
        return new ArrayList<>(all_servers.values());
    }

    public void addServerCon(Connection con) {
        unauthorised_connections.add(con);
    }

    public boolean isServerCon(Connection con) {
        return server_connections.containsKey(con) || unauthorised_connections.contains(con);
    }

    // ------------------------------ CONNECTION CLOSING ------------------------------

    public void closeServerCons() {
        server_connections.forEach((con, server) -> {
            con.writeMsg(MessageProcessor.getShutdownMessage());
        });
        server_connections.clear();
    }

    public void removeCon(Connection con) {
        con.writeMsg(MessageProcessor.getShutdownMessage());
        server_connections.remove(con);
        if (this.parentConnection != null && this.parentConnection.equals(con)) {
            setNoParent();
        }
        else if (this.child_root != null && connectedChildServers.containsKey(this.child_root) &&
                connectedChildServers.get(this.child_root).equals(con)) {
            setNextRootChild();
        }
        else {
            siblings_list.removeIf((s) ->
                    con.getHostname().equals(s.getHostname()) && con.getPort().equals(s.getPort()));
        }
        // TODO: Do we want to also remove the record?
        // removeFromAllServers(con);
    }

    public void removeFromAllServers(Connection con) {
        for (String sid : all_servers.keySet()) {
            ConnectedServer server = all_servers.get(sid);
            if (con.getHostname().equals(server.getHostname()) && con.getPort().equals(server.getPort())) {
                all_servers.remove(sid);
                break;
            }
        }
    }

    // ---------------------------------- GETTERS ----------------------------------

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

    public ConcurrentLinkedQueue<ConnectedServer> getConsToTry() {
        ConcurrentLinkedQueue<ConnectedServer> consToTry = new ConcurrentLinkedQueue<ConnectedServer>();



        // Add all other siblings, if we are not the root sibling
        if (!amRootSibling()) {
            siblings_list.forEach((sibling) -> {
                if (sibling != this.this_server) {
                    tryToAdd(consToTry, sibling);
                }
            });
        }

        // Add all remaining servers that aren't already included and that aren't children
        all_servers.forEach((id, server) -> {
            if (!consToTry.contains(server) && !server.isChild()) {
                tryToAdd(consToTry, server);
            }
        });
        return consToTry;
    }

    public boolean amRootSibling() {
        return (siblings_list.size() > 1 && siblings_list.get(0).equals(this_server));
    }

    public ConcurrentLinkedQueue<ConnectedServer> tryToAdd(ConcurrentLinkedQueue<ConnectedServer> servers, ConnectedServer server) {
        if (server != null && (server.getPort() != Settings.getLocalPort()) ) {
            System.out.println("Adding " + server + " to connections to try");
            servers.add(server);
        }
        return servers;
    }

    public Connection getParentConnection() {
        return parentConnection;
    }

    public ConnectedServer getParentInfo() {
        return parent;
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
        con.setHasLoggedOut(true);
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

        // TODO: Do we want to delete records?
        // all_servers.remove(c.getId());
    }


    @Override
    public String toString() {
        return "{\nserver_connections: " + serverConnectionsString() + "unauthorised_connections: " + unAuthConStr() +
                ", grandparent: " + strOrNull(grandparent) +
                ", parent: " + strOrNull(parent) + ", parentConnection: " + strOrNull(parentConnection) +
                ", child_root: " + strOrNull(child_root) + ", this_server: " + strOrNull(this_server) + ", " +
                ", sibling_list: " + siblingListStr() + ", connectedChildServers: " + connectedChildServStr() +
                ", all_servers: " + allServersStr() + "}";
    }

    public String strOrNull(ConnectedServer server) {
        return (server != null ? server.toString() : "null");
    }

    public String strOrNull(Connection con) {
        return (con != null ? con.toString() : "null");
    }

    public String serverConnectionsString() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        server_connections.forEach((con, server) -> {
            str.append(con.toString() + " : " + server.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    public String unAuthConStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        unauthorised_connections.forEach((con) -> {
            str.append(con.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    public String siblingListStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        siblings_list.forEach((sibling) -> {
            str.append(sibling.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    public String connectedChildServStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        connectedChildServers.forEach((child, con) -> {
            str.append(child.toString() + " : " + con.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    public String allServersStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        all_servers.forEach((id, server) -> {
            str.append(id + " : " + server.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

}
