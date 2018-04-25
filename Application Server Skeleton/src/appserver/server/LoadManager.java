package appserver.server;

import java.util.ArrayList;

/**
 *
 * @author Dr.-Ing. Wolf-Dieter Otte
 */
public class LoadManager {

    static ArrayList satellites = null;
    static int lastSatelliteIndex = -1;

    public LoadManager() {
        satellites = new ArrayList<String>();
    }

    public void satelliteAdded(String satelliteName) {
        satellites.add(satelliteName);
        System.out.println("[LoadManager.satelliteAdded] " + satelliteName + " added");
    }

    public void satelliteRemoved(String satelliteName) {
        satellites.remove(satelliteName);
    }

    public String nextSatellite() throws Exception {
        
        int numberSatellites;
        
        synchronized (satellites) {
            numberSatellites = satellites.size();
            if(numberSatellites == 0) {
                throw new Exception("No Satellites available");
            }
            
            if(lastSatelliteIndex+1 == numberSatellites) {
                lastSatelliteIndex = -1;
            }
            lastSatelliteIndex++;
        }

        return (String) satellites.get(lastSatelliteIndex);
    }
}