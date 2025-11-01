package de.hauke_stieler.geonotes.common;

public class GeoPoint {
    private final double lat;
    private final double lon;

    public GeoPoint(double lat, double lon){
        this.lat = lat;
        this.lon = lon;
    }

    public double getLatitude() {
        return lat;
    }

    public double getLongitude() {
        return lon;
    }
}
