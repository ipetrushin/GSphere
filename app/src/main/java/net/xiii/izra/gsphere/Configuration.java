package net.xiii.izra.gsphere;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;

public class Configuration {
    private int stay = 20000;
    String teamName, serverURL;
    int wpCount;
    ArrayList<Waypoint> waypoints;

    InputStream inputStream;

    public Configuration(File file) {
        try {
            inputStream = new FileInputStream(file);
        } catch (IOException e) { };
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String csvLine;
        waypoints = new ArrayList<>();
        try {
            csvLine = reader.readLine();
            // reading Team name, number of waypoints, serverURL
            String[] row = csvLine.split(",");
            teamName = row[0];
            wpCount = Integer.parseInt(row[1]);
            serverURL = row[2];

            for (int i = 0; i < wpCount; i++) {
                csvLine = reader.readLine();
                // reading beacon color, latitude and longtitude
                row = csvLine.split(",");
                //Log.d("my", String.format("%s: %lf, %lf, %f", row[0], Double.parseDouble(row[1]), Double.parseDouble(row[2]), Float.parseFloat(row[3])));
                Waypoint wp  = new Waypoint(Double.parseDouble(row[1]), Double.parseDouble(row[2]), Float.parseFloat(row[3]));

                waypoints.add(wp);

            }
        }
        catch (IOException ex) {
            throw new RuntimeException("Error in reading CSV file: "+ex);
        }
        finally {
            try {
                inputStream.close();
            }
            catch (IOException e) {
                throw new RuntimeException("Error while closing input stream: "+e);
            }
        }
        Log.d("my", "Config read successfully");
    }
}