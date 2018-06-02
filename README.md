# distributed_systems_proj2 
A multi-server system for broadcasting Activity Messages between clients,  using a server protocol to provide availability and eventual consistency among the servers, in the presence of possible server failure and network partitioning. Project 2 in Distributed Systems (COMP90015) at the University of Melbourne.

## Building

Enter the `activitystreamerclient` directory & `activitystreamerserver` directory in separate terminals. Ensure maven is installed. Run: `mvn clean install` to build the `.jar` files. Once built, `cd target`, and you can run the jar files from here (`ActivityStreamerClient.jar` & `ActivityStreamerServer.jar`). 

Note that you cannot move the jar files outside of their respective `target` directories, as the jar files use the dependencies in those directories.

## Viewing Code - IntelliJ

To open in IntelliJ, open as two separate projects, one from the `activitystreamerclient` directory and the other from the `activitystreamerserver` directory. Ensure IntelliJ is set to compile and run the code in Java 1.8. 