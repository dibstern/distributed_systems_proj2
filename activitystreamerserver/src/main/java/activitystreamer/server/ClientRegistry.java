package activitystreamer.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;

public class ClientRegistry {

    private ArrayList<ClientRecord> clientRecords;

    // Client Records can either start empty, or they can be provided
    public ClientRegistry() {
        this.clientRecords = new ArrayList<ClientRecord>();
    }

    public ClientRegistry(ArrayList<ClientRecord> providedClientRecords) {
        this.clientRecords = providedClientRecords;
    }


    // Receive a JSONObject msg labeled "CLIENT_REGISTRY" and has a field "registry" & use it to update existing records

    /**
     *
     *
     * Assume:
     *  - Has a "command" field labeled "CLIENT_REGISTRY"
     *  - Has a "registry" field with a valid JSONArray (this is ensured anyway)
     * @param msg
     */
    public void updateRecords(JSONObject msg) throws org.json.simple.parser.ParseException {
        Object registryObject = msg.get("registry");
        JSONArray registry;
        if (registryObject instanceof JSONArray) {
            registry = (JSONArray) registryObject;
        }
        else {
            throw new ParseException(2);
        }


    }

}
