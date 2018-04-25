package appserver.satellite;

import appserver.job.Job;
import appserver.comm.ConnectivityInfo;
import appserver.job.UnknownToolException;
import appserver.comm.Message;
import static appserver.comm.MessageTypes.JOB_REQUEST;
import static appserver.comm.MessageTypes.REGISTER_SATELLITE;
import appserver.job.Tool;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.PropertyHandler;


/**
 * Class [Satellite] Instances of this class represent computing nodes that execute jobs by
 * calling the callback method of tool implementation, loading the tools code dynamically over a network
 * or locally, if a tool got executed before.
 *
 * @author Dr.-Ing. Wolf-Dieter Otte
 */
public class Satellite extends Thread {

    private ConnectivityInfo satelliteInfo = new ConnectivityInfo();
    private ConnectivityInfo serverInfo = new ConnectivityInfo();
    private HTTPClassLoader classLoader = null;
    private Hashtable toolsCache = null;
    private PropertyHandler satelliteConfig = null;
    private PropertyHandler classConfig = null;
    private PropertyHandler serverConfig = null;
    final private String serverHost;
    final private int serverPort;
    //
    final private int satellitePort;

    public Satellite(String satellitePropertiesFile, String classLoaderPropertiesFile, String serverPropertiesFile) {
        
        
        //Initialize Hashtable
        toolsCache = new Hashtable();
        
        // read the configuration information from the file name passed in
        // ---------------------------------------------------------------
        try{
          satelliteConfig = new PropertyHandler(satellitePropertiesFile);
          classConfig = new PropertyHandler(classLoaderPropertiesFile);
          serverConfig = new PropertyHandler(serverPropertiesFile);
        } catch (Exception e){
            e.printStackTrace();
            System.exit(1);
        }

        satelliteInfo = new ConnectivityInfo();
        
        satelliteInfo.setName(satelliteConfig.getProperty("NAME"));
        System.out.println("Set name: "+satelliteConfig.getProperty("NAME"));
        
        satellitePort = Integer.parseInt(satelliteConfig.getProperty("PORT"));
        satelliteInfo.setPort(satellitePort);
        System.out.println("Set Port: "+satellitePort);
        


        // get connectivity information of the server
        serverHost = serverConfig.getProperty("HOST");
        serverPort = Integer.parseInt(serverConfig.getProperty("PORT"));


        // read class loader config
        // get class loader connectivity properties and create class loader
        String classHost = classConfig.getProperty("HOST");
        String classPortString = classConfig.getProperty("PORT");

        initToolLoader(classHost, classPortString);


        // create tools cache
        // -------------------
    }

    @Override
    public void run() {

        Socket appServerSocket = null;
        ObjectOutputStream writeToServer = null;
        Message satelliteRegister = null;
        try {
            appServerSocket = new Socket(serverHost, serverPort);
            
            writeToServer = new ObjectOutputStream(appServerSocket.getOutputStream());
            
            satelliteRegister = new Message(3, satelliteInfo);
            
            writeToServer.writeObject(satelliteRegister);
            
        } catch (IOException ex) {
            Logger.getLogger(Satellite.class.getName()).log(Level.SEVERE, null, ex);
        }

        
        


        // create server socket
        // ---------------------------------------------------------------
        try{
          ServerSocket serverSocket;
            serverSocket = new ServerSocket(satellitePort);

          while(true){
            System.out.println("Waiting for connections on Port #" + satellitePort);
            Socket connectionToServer;
            //Accept incomming connections.
            connectionToServer  = serverSocket.accept();
            System.out.println("A connection to a client is established!");
            //Spin off new thread.
            (new SatelliteThread(connectionToServer, this)).start();
          }

        } catch (IOException e) {
            System.err.println(e);
        }



        // start taking job requests in a server loop
        // ---------------------------------------------------------------

    }

    // inner helper class that is instanciated in above server loop and processes job requests
    private class SatelliteThread extends Thread {

        Satellite satellite = null;
        Socket jobRequestSocket = null;
        ObjectOutputStream writeToServer = null;
        ObjectInputStream readFromServer = null;   
        Message message = null;

        SatelliteThread(Socket jobRequest, Satellite satellite) {
            this.jobRequestSocket = jobRequest;
            this.satellite = satellite;
        }

        @Override
        public void run() {
            // setting up object streams
            try {
                writeToServer = new ObjectOutputStream(jobRequestSocket.getOutputStream());
                readFromServer = new ObjectInputStream(jobRequestSocket.getInputStream());
                
            } catch (IOException ex) {
                Logger.getLogger(Satellite.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            // reading message
            try {
                message = (Message) readFromServer.readObject();
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(Satellite.class.getName()).log(Level.SEVERE, null, ex);
            }


            // processing message
            switch (message.getType()) {
                case JOB_REQUEST:
                    //Cast message to Job object
                    Job job = (Job) message.getContent();
                    
                    //Get tool name from message.
                    String toolName = job.getToolName();
                    System.out.println("Retrieveing tool: "+toolName);


                    Tool tool = null;

            {
                try {
                    tool = getToolObject(toolName);
                } catch (Exception e) {
                    Logger.getLogger(Satellite.class.getName()).log(Level.SEVERE, null, e);
                }
            }

                    
                    //Call the tool with the job paramters
                    //Get results back as an object.
                    Object resultObject = tool.go(job.getParameters());

                    //send results back to server
                    try {
                        writeToServer.writeObject(resultObject);
                    } catch (IOException ex) {
                        Logger.getLogger(Satellite.class.getName()).log(Level.SEVERE, null, ex);
                    }


                    break;

                default:
                    System.err.println("[SatelliteThread.run] Warning: Message type not implemented");
            }
        }
    }

    /*
     * Aux method to get a tool object, given the fully qualified class string
     *
     */
    public Tool getToolObject(String toolName) throws IOException, UnknownOperationException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        Tool toolObject;
        System.out.println(toolName);

        //Check if tool is not already in cache
        if ((toolObject = (Tool) toolsCache.get(toolName)) == null) {     
            System.out.println("Tool is not already in cache.");
            System.out.println("\nTool's Class: " + toolName);
            if (toolName == null) {
                throw new UnknownOperationException();
            }

            Class toolClass = classLoader.loadClass(toolName);
            toolObject = (Tool) toolClass.newInstance();
            toolsCache.put(toolName, toolObject);
        } else {
            System.out.println("Tool: \"" + toolName + "\" already in Cache");
        }
        
        
        return toolObject;
    }
    
    // create class loader
        // -------------------
        private void initToolLoader(String host, String portString) {

            if ((host != null) && (portString != null)) {
                try {
                    classLoader = new HTTPClassLoader(host, Integer.parseInt(portString));
                } catch (NumberFormatException nfe) {
                    System.err.println("Wrong Portnumber, using Defaults");
                }
            } else {
                System.err.println("configuration data incomplete, using Defaults");
            }

            if (classLoader == null) {
                System.err.println("Could not create HTTPClassLoader, exiting ...");
                System.exit(1);
            }
        }

    public static void main(String[] args) {
        // start a satellite
        Satellite satellite = new Satellite(args[0], args[1], args[2]);
        //Satellite satellite = new Satellite("../../config/Satellite.Earth.properties", "../../config/WebServer.properties", "../../config/Server.properties");
        //Satellite satellite = new Satellite("../../config/Satellite.Mercury.properties", "../../config/WebServer.properties", "../../config/Server.properties");
        satellite.run();
    }
}