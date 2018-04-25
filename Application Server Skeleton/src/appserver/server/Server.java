package appserver.server;

import appserver.comm.Message;
import static appserver.comm.MessageTypes.JOB_REQUEST;
import static appserver.comm.MessageTypes.REGISTER_SATELLITE;
import appserver.comm.ConnectivityInfo;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.PropertyHandler;

/**
 *
 * @author Dr.-Ing. Wolf-Dieter Otte
 */
public class Server {

    // Singleton objects - there is only one of them. For simplicity, this is not enforced though ...
    static SatelliteManager satelliteManager = null;
    static LoadManager loadManager = null;
    static ServerSocket serverSocket = null;
    private PropertyHandler serverProperties = null;

    public Server(String serverPropertiesFile) {

        // create satellite and load managers
        satelliteManager = new SatelliteManager();
        loadManager = new LoadManager();
        
        try{
          serverProperties = new PropertyHandler(serverPropertiesFile);
        } catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }
        
        // read server port from server properties file
        int serverPort = Integer.parseInt(serverProperties.getProperty("PORT"));
        
        
        // create server socket
        try {
            serverSocket = new ServerSocket(serverPort);
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
    }

    public void run() {
    // start serving clients in server loop ...

        try{
            while(true){
            System.out.println("Waiting for connections on Port");
            //Accept incomming connections.
            Socket connectionToServer  = serverSocket.accept();
            System.out.println("A connection is established!");
            //Spin off new thread.
            (new ServerThread(connectionToServer)).start();
          }
            
            

        } catch (IOException e) {
            System.err.println(e);
        }
    }

    // objects of this helper class communicate with clients
    private class ServerThread extends Thread {

        Socket client = null;
        ObjectOutputStream writeToNet = null;
        ObjectInputStream readFromNet = null;
        
        Message message = null;

        private ServerThread(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            try {
                // setting up object streams
                // ...
                writeToNet = new ObjectOutputStream(client.getOutputStream());
                readFromNet = new ObjectInputStream(client.getInputStream());
                
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }

            
            // reading message
            try {
                message = (Message) readFromNet.readObject();
            } catch (Exception e) {
                System.err.println("[ServerThread.run] Message could not be read from object stream.");
                e.printStackTrace();
                System.exit(1);
            }

            // processing message
            ConnectivityInfo satelliteInfo = null;
            switch (message.getType()) {
                case REGISTER_SATELLITE:
                    // read satellite info
                    
                    satelliteInfo = (ConnectivityInfo) message.getContent();

                    //String satelliteNamer = satelliteInfo.getName();
                    //int satellitePort = satelliteInfo.getPort();
                    
                    // register satellite
                    synchronized (Server.satelliteManager) {
                        //System.out.println(satelliteInfo.getName());
                        Server.satelliteManager.registerSatellite(satelliteInfo);
                    }

                    // add satellite to loadManager
                    synchronized (Server.loadManager) {
                        Server.loadManager.satelliteAdded(satelliteInfo.getName());
                    }

                    break;

                case JOB_REQUEST:
                    System.err.println("\n[ServerThread.run] Received job request");

                    String satelliteName = null;
                    synchronized (Server.loadManager) {
                try {
                    // get next satellite from load manager
                    satelliteName = Server.loadManager.nextSatellite();
                    
                    // get connectivity info for next satellite from satellite manager
                    satelliteInfo = Server.satelliteManager.getSatelliteForName(satelliteName);
                } catch (Exception ex) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                }
                    }

                    Socket satellite = null;
                    
                    ObjectOutputStream writeToSatellite = null;
                    ObjectInputStream readFromSatellite = null;
                    
                    try {
                        // connect to satellite
                        System.out.println("Connecting to satellite on port: "+satelliteInfo.getPort());
                        satellite = new Socket("127.0.0.1", satelliteInfo.getPort());
                        //System.out.println("Connected to satellite.");
                        
                        // open object streams,
                        
                        writeToSatellite = new ObjectOutputStream(satellite.getOutputStream());
                        
                        //System.out.println("Setup object output stream.");
                        
                        readFromSatellite = new ObjectInputStream(satellite.getInputStream());
                        
                        //System.out.println("Setup object input stream.");
                        
                        
                        
                    } catch (IOException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
               
                    
                    try {
                        // forward message (as is) to satellite,
                        writeToSatellite.writeObject(message);
                        
                        // receive result from satellite and
                        Object messageReply = readFromSatellite.readObject();
                        
                        // write result back to client
                        writeToNet.writeObject(messageReply);
                        
                        
                    } catch (IOException | ClassNotFoundException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    

                    break;

                default:
                    System.err.println("[ServerThread.run] Warning: Message type not implemented");
            }
        }
    }

    // main()
    public static void main(String[] args) {
        // start the application server
        Server server = null;
        if(args.length == 1) {
            server = new Server(args[0]);
        } else {
            server = new Server("../../config/Server.properties");
        }
        server.run();
    }
}