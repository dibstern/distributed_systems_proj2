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
 * This class stores and handles all ServerRecords a given server knows about.
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
    private ConnectedServer rootSibling;
    private ConcurrentHashMap<ConnectedServer, Connection> connectedChildServers;
    private ConcurrentHashMap<String, ConnectedServer> all_servers;

    /** Create a new server registry for a given server
     * @param id The server's id
     * @param port The server's port
     * @param hostname The server's hostname  */
    public ServerRegistry(String id, Integer port, String hostname) {
        this.connectedChildServers = new ConcurrentHashMap<ConnectedServer, Connection>();
        this.child_root = null;
        this.this_server = new ConnectedServer(id, hostname, port, false, false);
        this.siblings_list = new ArrayList<ConnectedServer>();
        this.siblings_list.add(this_server);
        this.rootSibling = this_server;
        this.parent = null;
        this.parentConnection = null;
        this.server_connections = new ConcurrentHashMap<Connection, ConnectedServer>();
        this.unauthorised_connections = new ArrayList<Connection>();
        this.all_servers = new ConcurrentHashMap<String, ConnectedServer>();
    }

    /** Creates a new server record
     * @param record The JSONObject representation of the server record
     * @param isChild true if server is one of our children
     * @param isParent true if server is our parent
     * @return A ConnectedServer record representing the new server */
    public ConnectedServer createNewRecord(JSONObject record, boolean isChild, boolean isParent) {
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
            // Already aware of server, so just update the record
            all_servers.get(id).updateServer(load, isChild);
        }
        else {
            // Unaware of server previously, create a new record and add to storage
            ConnectedServer newConnectedServer = new ConnectedServer(id, hostname, port, isChild, false);
            System.out.println("Updating Registry -> Adding connection to server_connections: " + hostname + ":" + port);
            all_servers.put(id, newConnectedServer);
        }
        if (!isChild) {
            // Message came from a server that is not one of our children, reset timeout counter
            parent.resetTimeout();
        }
    }

    // ------------------------------ PARENT MANAGEMENT ------------------------------

    /** Checks if the given connection is the connection to our parent server
     * @param con The connection to examine
     * @return true if connection is to our parent server, false otherwise */
    public boolean isParentConnection(Connection con) {
        return con.equals(this.parentConnection);
    }

    /** Set a given server as our parent server
     * @param id The parent server's id
     * @param hostname The parent server's hostname
     * @param port The parent server's port number
     * @param con The connection to the parent server
     * @return a ConnectedServer record representing our parent server */
    public ConnectedServer setConnectedParent(String id, String hostname, int port, Connection con) {
        System.out.println("Setting parent connection: " + con);
        if (this.parent != null) {
            all_servers.remove(this.parent.getId());
        }
        // Create a new record and store
        this.parent = new ConnectedServer(id, hostname, port, false, true);
        this.parentConnection = con;
        if (!all_servers.containsKey(id)) {
            System.out.println("Setting connected Parent -> Adding connection to server_connections: " + port);
            all_servers.put(id, this.parent);
        }
        // Remove connection from unauthorised_connections list and add to server_connections
        System.out.println("this.parentConnection = " + this.parentConnection);
        unauthorised_connections.remove(con);
        server_connections.put(con, this.parent);

        // Update child server's that they have a new grandparent
        String msg = MessageProcessor.getGrandparentUpdateMsg(getParentJson());
        SessionManager.getInstance().forwardToChildren(msg);

        // Check if parent was previously our sibling, and remove from siblings list if true
        if (siblings_list.contains(parent)) {
            removeSibling(parent);
        }
        return this.parent;
    }

    /** Server is the root server in the network, set parent to null */
    public void setNoParent() {
        System.out.println("Removing Parent Connection" + this.parentConnection);
        all_servers.remove(this.parent.getId());
        this.parent = null;
        this.parentConnection = null;
        // Alert child servers that they have no grandparent
        String msg = MessageProcessor.getGrandparentUpdateMsg(getParentJson());
        SessionManager.getInstance().forwardToChildren(msg);
    }

    // ------------------------------ CHILD MANAGEMENT ------------------------------

    /** Determine if the server has a child server designated as the root
     * @return true if have designated a root child, false otherwise */
    public boolean hasRootChild() {
        return child_root != null;
    }

    /** Set a server to be our root child (the server all other children connect to if we crash and grandparent does
     * not exist
     * @param con The connection to the root child
     * @param id The id of the root child
     * @param hostname The hostname of the root child
     * @param port The port of the root child
     * @return the ConnectedServer record of our root child*/
    public ConnectedServer addRootChild(Connection con, String id, String hostname, Integer port) {
        System.out.println("Adding Root Child -> Adding connection to server_connections: " + port);
        child_root = new ConnectedServer(id, hostname, port, true, false);
        all_servers.put(child_root.getId(), child_root);
        server_connections.put(con, child_root);
        connectedChildServers.put(child_root, con);
        return child_root;
    }

    /** Add a new server child connection to our local storage
     * @param con The connection to the root child
     * @param id The id of the root child
     * @param hostname The hostname of the root child
     * @param port The port of the root child
     * @return the ConnectedServer record of our new child */
    public ConnectedServer addConnectedChild(Connection con, String id, String hostname, Integer port) {
        ConnectedServer childServer = new ConnectedServer(id, hostname, port, true, false);
        System.out.println("Adding Connected Child -> Adding connection to server_connections: " + port);
        server_connections.put(con, childServer);
        all_servers.put(childServer.getId(), childServer);
        connectedChildServers.put(childServer, con);
        return childServer;
    }

    /** The original child root has crashed, and we need to set a new designated root child (if one exists) */
    public void setNextRootChild() {
        // Remove existing child_root as has crashed
        connectedChildServers.remove(child_root);
        if (connectedChildServers.isEmpty()) {
            // No other child connections, set root to null
            child_root = null;
        }
        else {
            // Get the first child in our list of children and set as the root
            ArrayList<ConnectedServer> childList = getConnectedChildList();
            child_root = childList.get(0);
        }
    }

    /** Get the list of all child servers currently connected to us
     * @return The ArrayList of all servers that are our children */
    public ArrayList<ConnectedServer> getConnectedChildList() {
        ArrayList<ConnectedServer> childList = new ArrayList<ConnectedServer>();
        connectedChildServers.forEach((server, con) -> {
            childList.add(server);
        });
        // Sort the list
        Collections.sort(childList);
        return childList;
    }

    /** Get the connections to all of our child servers
     * @return The connection for each child server we are connected to */
    public ConcurrentHashMap<ConnectedServer, Connection> getConnectedChildConnections() {
        return this.connectedChildServers;
    }

    // ------------------------------ SIBLING MANAGEMENT ------------------------------

    /** Convert our list of child servers to a JSONObject, so it can be sent across the network
     * @return the JSONObject holding our list of child server */
    public JSONObject childListToJson() {
        ArrayList<ConnectedServer> childServerList = getConnectedChildList();
        String childServersJsonString = MessageProcessor.getGson().toJson(childServerList);
        return MessageProcessor.toJson(childServersJsonString, true, "sibling_servers");
    }

    /** Have received a list of siblings from our parent server - need to create a ConnectedServer record for each of
     * these and add them to our local storage.
     * @param siblingsList The JSONArray containing all of our sibling servers */
    public void addSiblingsList(JSONArray siblingsList) {

        // Convert the JSONArray into a form we can storage
        Type collectionType = new TypeToken<ArrayList<ConnectedServer>>(){}.getType();
        this.siblings_list = MessageProcessor.getGson().fromJson(siblingsList.toJSONString(), collectionType);
        // Set the servers as our siblings
        this.siblings_list.forEach((sibling) -> {
            sibling.setIsChild(false);
            sibling.setIsParent(false);
            sibling.setIsSibling(true);

            if (!all_servers.containsKey(sibling.getId())) {
                System.out.println("Adding Siblings List -> Adding connection to all_servers: " + sibling.getPort());
                all_servers.put(sibling.getId(), sibling);
            }
        });
        // Sort list of siblings and set first server to be the designated root sibling
        Collections.sort(this.siblings_list);
        setRootSibling();
//        setRootSibling(siblings_list.get(0));
    }

    /** A new server has connected to our parent server - update records to include new sibling
     * @param siblingJson The JSONObject representation of our new sibling */
    public void addSibling(JSONObject siblingJson) {
        Type collectionType = new TypeToken<ConnectedServer>(){}.getType();
        // Convert JSOBObject into a type we can use
        ConnectedServer sibling = MessageProcessor.getGson().fromJson(siblingJson.toJSONString(), collectionType);
        // Set record as one of our siblings
        sibling.setIsSibling(true);
        sibling.setIsParent(false);
        sibling.setIsChild(false);
        if (!siblings_list.contains(sibling)) {
            // Add to siblings list, if not already aware of server
            this.siblings_list.add(sibling);
            Collections.sort(this.siblings_list);
            if (!all_servers.containsKey(sibling.getId())) {

                System.out.println("Adding Siblings -> Adding connection to server_connections: " + sibling.getPort());
                all_servers.put(sibling.getId(), sibling);
            }
            // Reset the root sibling, in case the new server is to be the new designated root
            setRootSibling();
        }
    }

    /** Sets the root sibling we are to connect to if our parent crashes AND we do not have a grandparent server */
    public void setRootSibling() {
        if (!siblings_list.isEmpty()) {
            this.rootSibling = siblings_list.get(0);
        }
        else {
            System.out.println("SIBLINGS LIST IS EMPTY - CANNOT ADD rootSibling!");
        }
    }

    /** Our parent server has crashed - remove all records of this server and set parent to null */
    public void removeCrashedParent() {
        all_servers.remove(this.parent.getId());
        removeFromServerCon(this.parent);
        this.parent = null;
    }

    /** One of our children has crashed - remove from records and update child root if crashed server was the
     * designated child
     * @param crashedChild The server that crashed */
    public void removeCrashedChild(ConnectedServer crashedChild) {
        all_servers.remove(crashedChild.getId());
        removeFromServerCon(crashedChild);
        connectedChildServers.remove(crashedChild);
        if (child_root.equals(crashedChild)) {
            setNextRootChild();
        }
    }

    /** One of our siblings has crashed - remove from records and update designated root if crashed server was the
     * designated sibling server
     * @param crashedSibling The server that crashed */
    public void removeCrashedSibling(ConnectedServer crashedSibling) {
        all_servers.remove(crashedSibling.getId());
        removeFromServerCon(crashedSibling);
        siblings_list.remove(crashedSibling);
        if (rootSibling == null) {
            System.out.println("ERROR - NO ROOT SIBLING SET, CANNOT REMOVE CRASHED SIBLING!");
        }
        else if (rootSibling.equals(crashedSibling)) {
            // Check if the root sibling was the server that crashed - update if required
            setRootSibling();
        }
    }

    /** Remove a sibling from the siblings list, and reassign the root sibling, if necessary
     * @param sibling The sibling to be removed from the list */
    public void removeSibling(ConnectedServer sibling) {
        siblings_list.remove(sibling);
        setRootSibling();
    }

    /** A server in the network (that we are not directly connected to) has crashed - remove from our records
     * @param removeThisServer The server that crashed */
    public void removeFromServerCon(ConnectedServer removeThisServer) {
        ConcurrentHashMap<Connection, ConnectedServer> new_server_connections = new ConcurrentHashMap<>();
        server_connections.forEach((con, server) -> {
            if (!server.equals(removeThisServer)) {
                new_server_connections.put(con, server);
            }
        });
        this.server_connections = new_server_connections;
    }

    /** Retrieve a Server record for a given connection
     * @param con The connection to the server
     * @return the record of the server */
    public ConnectedServer getServerFromCon(Connection con) {
        return server_connections.get(con);
    }

    /** Retrieve a server record for a given server id
     * @param serverId the id of the server we want the record for
     * @return The ConnectedServer record for that server, if exists, otherwise null */
    public ConnectedServer getServerInfo(String serverId) {
        if (all_servers.containsKey(serverId)) {
            return all_servers.get(serverId);
        }
        return null;
    }


    // ------------------------------ GENERAL ------------------------------

    /** Get the number of servers we know about on the network
     * @return The number of servers on the network that we are aware of */
    public Integer numServersInNetwork() {
        return all_servers.size();
    }

    /** Get all servers we are connected to
     * @return A hashmap of the connections and server records for all servers we are connected to  */
    public ConcurrentHashMap<Connection, ConnectedServer> getServerConnections() {
        return server_connections;
    }

    /** Return the records of all the servers we are aware of
     * @return Records for all servers we know about on the network */
    public ArrayList<ConnectedServer> getAllServers() {
        return new ArrayList<>(all_servers.values());
    }

    /** Add a new server connection to our holding list
     * @param con the connection to add*/
    public void addServerCon(Connection con) {
        unauthorised_connections.add(con);
    }

    /** Check if a given connection is a connection to a server
     * @return true if connection is server connection, false otherwise  */
    public boolean isServerCon(Connection con) {
        return server_connections.containsKey(con) || unauthorised_connections.contains(con);
    }

    // ------------------------------ CONNECTION CLOSING ------------------------------

    /** Close all connections we have to a server, and send a shutdown message to these connections */
    public void closeServerCons() {
        server_connections.forEach((con, server) -> {
            con.writeMsg(MessageProcessor.getShutdownMessage(this_server.getId()));
        });
        server_connections.clear();
    }

    // ---------------------------------- GETTERS ----------------------------------

    /** Get the server record of our parent and convert into JSONObject format so it can be sent across the network
     * @return The JSONObject representation of our parent server record */
    public JSONObject getParentJson() {

        String jsonParent = MessageProcessor.getGson().toJson(this.parent);
        return MessageProcessor.toJson(jsonParent, false, "parent");
    }

    // ---------------------------------- SETTERS ----------------------------------
    /** Set our grandparent to a new server record
     * @param newGrandparent The JSONObject representation of our new grandparent server */
    public void setGrandparent(JSONObject newGrandparent) {
        this.grandparent = createNewRecord(newGrandparent, false, false);
    }

    /** No grandparent exists, so set to null */
    public void setNoGrandparent() {
        this.grandparent = null;
    }

    /** We have no children, so set child root to null  */
    public void setNoChildRoot() {
        this.child_root = null;
    }

    /** Get this server's record
     * @return The ConnectedServer record for this server  */
    public ConnectedServer getThisServer() {
        return this_server;
    }

    /** A helper function that returns JSONObject representations of our children and parent
     * @return a JSOBObject representation of child servers and parent server */
    public JSONObject toJson() {
        JSONObject thisJson = new JSONObject();
        thisJson.putAll(childListToJson());
        thisJson.put("grandparent", getParentJson());
        return thisJson;
    }

    /** Get all servers we are connected to that are not our children
     * @return a queue of all server connections that are not our children */
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

    /** Check if we are the root sibling, and will therefore have all siblings connect to us
     * @return true if we are the designated root sibling, false otherwise */
    public boolean amRootSibling() {
        Collections.sort(siblings_list);
        return (siblings_list.size() > 1 && siblings_list.get(0).equals(this_server) || siblings_list.size() == 1);
    }

    /** Adds a server to the queue of servers we will attempt to initiate a connection to, during a network partition
     * @param servers List of all servers that are not our children
     * @param server Our server record */
    public void tryToAdd(ConcurrentLinkedQueue<ConnectedServer> servers, ConnectedServer server) {
        if (server != null && (server.getPort() != Settings.getLocalPort()) ) {
            System.out.println("Adding " + server + " to connections to try");
            servers.add(server);
        }
    }

    /** Return the connection to our parent
     * @return Connection to our parent */
    public Connection getParentConnection() {
        return parentConnection;
    }

    /** Return the record of our parent server
     * @return The ConnecteServer record representing our parent server */
    public ConnectedServer getParentInfo() {
        return parent;
    }

    /** Return the record of our grandparent
     * @return ConnectedServer record of our grandparent */
    public ConnectedServer getGrandparent() {
        return grandparent;
    }

    //-----------------------------GENERAL HELPER FUNCTIONS-------------------------------------------------------

    @Override
    /** Assists in converting a JSONObject into string format */
    public String toString() {
        return "{\nserver_connections: " + serverConnectionsString() + "unauthorised_connections: " + unAuthConStr() +
                ", grandparent: " + strOrNull(grandparent) +
                ", parent: " + strOrNull(parent) + ", parentConnection: " + strOrNull(parentConnection) +
                ", child_root: " + strOrNull(child_root) + ", this_server: " + strOrNull(this_server) + ", " +
                ", sibling_list: " + siblingListStr() + ", connectedChildServers: " + connectedChildServStr() +
                ", all_servers: " + allServersStr() + "}";
    }

    /** Checks if a server record is null or not
     * @return String representation of server record, otherwise null */
    public String strOrNull(ConnectedServer server) {
        return (server != null ? server.toString() : "null");
    }

    /** Checks if a connection  is null or not
     * @return String representation of connection record, otherwise null */
    public String strOrNull(Connection con) {
        return (con != null ? con.toString() : "null");
    }

    /** Converts a server connection to a string
     * @return String representation of server connection */
    public String serverConnectionsString() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        server_connections.forEach((con, server) -> {
            str.append(con.toString() + " : " + server.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    /** Converts an unauthorised server connection to a string
     * @return String representation of unauthorised server connection */
    public String unAuthConStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        unauthorised_connections.forEach((con) -> {
            str.append(con.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    /** Converts a siblings list to a string
     * @return string representation of siblings list */
    public String siblingListStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        siblings_list.forEach((sibling) -> {
            str.append(sibling.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    /** Converts a list of child server records to a string
     * @return string representation of child server records list */
    public String connectedChildServStr() {
        StringBuilder str = new StringBuilder();
        str.append("{");
        connectedChildServers.forEach((child, con) -> {
            str.append(child.toString() + " : " + con.toString() + ",\n");
        });
        str.append("}\n");
        return str.toString();
    }

    /** Converrts a list of servers into a string
     * @return String representation of a list of servers */
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
