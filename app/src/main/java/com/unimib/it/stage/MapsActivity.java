package com.unimib.it.stage;

import android.Manifest;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_HYBRID;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_SATELLITE;
import static com.google.android.gms.maps.GoogleMap.MAP_TYPE_TERRAIN;

//starting activity
public class MapsActivity extends FragmentActivity implements OnMyLocationButtonClickListener,
        OnMyLocationClickListener, OnMapReadyCallback,AdapterView.OnItemSelectedListener, GoogleMap.OnMapClickListener,
        GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMarkerClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleMap mMap; // map
    private Bundle b; // object used for passing parameters to other activities

    Button bsearch_location; // button for searching a location
    Button bsearch_route; // button for searching a route
    PlaceAutocompleteFragment autocompleteFragmentDeparture; // departure/location search bar
    PlaceAutocompleteFragment autocompleteFragmentArrival; // arrival search bar
    View view1;
    View view2;
    TextView tvdistance;
    TextView tvduration;

    public int MODE; // search mode (1=location, 2=route)
    private MarkerOptions departure = new MarkerOptions(); // departure marker
    private MarkerOptions arrival = new MarkerOptions(); // arrival marker

    ArrayList<LatLng> markerPoints;
    ArrayList<LatLng> points = null;

    private Spinner mSpinner;


    //method performed for creating activity
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // various inizialization
        markerPoints = new ArrayList<LatLng>();
        b = new Bundle();
        MODE = 1;

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        // button for searching a location
        bsearch_location = (Button) findViewById(R.id.search_location);
        bsearch_location.setVisibility(View.GONE);
        bsearch_location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                search_location();
            }
        });

        // button for searching a route
        bsearch_route = (Button) findViewById(R.id.search_route);
        bsearch_route.setVisibility(View.VISIBLE);
        bsearch_route.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                search_route();
            }
        });


        // search bar used to select a location/departure
        autocompleteFragmentDeparture = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.departure_autocomplete_fragment);

        autocompleteFragmentDeparture.getView().setBackgroundColor(Color.WHITE);
        autocompleteFragmentDeparture.setHint("Cerca");
        autocompleteFragmentDeparture.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15);
                mMap.animateCamera(cameraUpdate);
                autocompleteFragmentDeparture.setText(place.getName());
                onMapClick(place.getLatLng());
            }

            @Override
            public void onError(Status status) {
                Toast.makeText(getBaseContext(), status.getStatusMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // search bar used to select a route
        autocompleteFragmentArrival = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.arrival_autocomplete_fragment);

        autocompleteFragmentArrival.getView().setBackgroundColor(Color.WHITE);
        autocompleteFragmentArrival.setHint("Arrivo");
        autocompleteFragmentArrival.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                // if both departure and arrival are already selected, the current arrival is deleted to allow the selection of a new arrival
                if(markerPoints.size()==2)
                    markerPoints.remove(1);

                autocompleteFragmentArrival.setText(place.getName());
                onMapClick(place.getLatLng());
            }

            @Override
            public void onError(Status status) {
                Toast.makeText(getBaseContext(), status.getStatusMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // allows the display of only one search bar to search a place
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction()
                .show(autocompleteFragmentDeparture)
                .hide(autocompleteFragmentArrival)
                .commit();

        // white squares are used to mask the possibility of removing a place from the search bar
        view1 = (View) findViewById(R.id.view1);
        view1.setVisibility(View.VISIBLE);

        view2 = (View) findViewById(R.id.view2);
        view2.setVisibility(View.GONE);


        // textview for viewing data related to a route
        tvdistance = (TextView) findViewById(R.id.infodistance);
        tvdistance.setVisibility(View.GONE);

        tvduration = (TextView) findViewById(R.id.infoduration);
        tvduration.setVisibility(View.GONE);


        // spinner for selection of the type of the maps
        mSpinner = (Spinner) findViewById(R.id.layers_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.layers_array, R.layout.spinner_layout);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(adapter);
        mSpinner.setOnItemSelectedListener(this);
    }

    // method activated for searching a place
    private void search_location() {
        mMap.clear();
        MODE = 1;

        bsearch_location.setVisibility(View.GONE);
        bsearch_route.setVisibility(View.VISIBLE);
        view2.setVisibility(View.GONE);

        tvdistance.setVisibility(View.GONE);
        tvduration.setVisibility(View.GONE);

        autocompleteFragmentDeparture.setHint("Cerca");
        autocompleteFragmentDeparture.setText("");
        autocompleteFragmentArrival.setText("");
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction()
                .show(autocompleteFragmentDeparture)
                .hide(autocompleteFragmentArrival)
                .commit();
    }

    // method activated for searching a route
    private void search_route() {
        MODE = 2;

        bsearch_location.setVisibility(View.VISIBLE);
        bsearch_route.setVisibility(View.GONE);
        view2.setVisibility(View.VISIBLE);

        autocompleteFragmentDeparture.setHint("Partenza");
        FragmentManager fm = getFragmentManager();
        fm.beginTransaction()
                .show(autocompleteFragmentDeparture)
                .show(autocompleteFragmentArrival)
                .commit();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        updateMapType();

        //enabling localization and localization button
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        //enabling zoom keys
        mMap.getUiSettings().setZoomControlsEnabled(true);

        /*disabling toolbar (it is used to continue using this app without having to
        use the official google maps app)*/
        mMap.getUiSettings().setMapToolbarEnabled(false);

        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMapClickListener(this);
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnMarkerClickListener(this);
    }


    @Override
    public void onMyLocationClick(@NonNull Location location) {
        onMapClick(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "Localizzazione..", Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    public void onMapClick(LatLng latLng) {

        if (MODE == 1) {
            mMap.clear();
            markerPoints.clear();
            b.clear();
            b.putDouble("lat", latLng.latitude);
            b.putDouble("lng", latLng.longitude);
            mMap.addMarker(new MarkerOptions().position(latLng).title("StreetView")).showInfoWindow();
            markerPoints.add(latLng);

            departure.position(latLng);
            departure.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .title("Partenza");

            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> address = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if(address.size()!=0) {
                    String cityName = address.get(0).getThoroughfare() + ", "
                                    + address.get(0).getFeatureName();
                    autocompleteFragmentDeparture.setText(cityName);
                }
            } catch (IOException e) {

            }

        }


        if (MODE == 2) {
            // Already two locations
            if (markerPoints.size() == 2) {
                markerPoints.clear();
                mMap.clear();
                autocompleteFragmentDeparture.setText("");
                autocompleteFragmentArrival.setText("");
                tvdistance.setVisibility(View.GONE);
                tvduration.setVisibility(View.GONE);
            }

            // Adding new item to the ArrayList
            markerPoints.add(latLng);

            /**
             * For the start location, the color of marker is RED and
             * for the end location, the color of marker is BLUE.
             */


            if (markerPoints.size() == 1) {
                departure.position(latLng);
                departure.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        .title("Partenza");

                b.clear();
                b.putDouble("lat", latLng.latitude);
                b.putDouble("lng", latLng.longitude);
                mMap.addMarker(departure).showInfoWindow();

                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> address = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    if(address.size()!=0) {
                        String cityName = address.get(0).getThoroughfare() + ", "
                                        + address.get(0).getFeatureName();
                        autocompleteFragmentDeparture.setText(cityName);
                    }
                } catch (IOException e) {

                }

            } else if (markerPoints.size() == 2) {
                mMap.clear();
                arrival.position(latLng);
                arrival.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                mMap.addMarker(arrival);
                mMap.addMarker(departure).showInfoWindow();
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(departure.getPosition(), 15);
                mMap.animateCamera(cameraUpdate);

                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                try {
                    List<Address> address = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    if(address.size()!=0){
                        String cityName = address.get(0).getThoroughfare() + ", "
                                        + address.get(0).getFeatureName();
                        autocompleteFragmentArrival.setText(cityName);
                    }
                } catch (IOException e) {

                }
            }


            // Checks, whether start and end locations are captured
            if (markerPoints.size() >= 2) {
                LatLng origin = markerPoints.get(0);
                LatLng dest = markerPoints.get(1);

                // Getting URL to the Google Directions API
                String url = getDirectionsUrl(origin, dest);

                DownloadTask downloadTask = new DownloadTask();

                // Start downloading json data from Google Directions API
                downloadTask.execute(url);

            }
        }

    }


    @Override
    public boolean onMarkerClick(Marker marker) {
        if(MODE==1)
            marker.remove();
        return false;
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Intent intent = new Intent(this, StreetViewActivity.class);
        intent.putExtras(b);
        intent.putExtra("mode", MODE);
        intent.putExtra("points", points);

        try {
            startActivityForResult(intent, 1);
        }
        catch(Exception e)
        {
            Toast.makeText(getBaseContext(), "Percorso troppo lungo", Toast.LENGTH_SHORT).show();
        }
    }


    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Mode type
        String mode = "mode=walking";

        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + mode;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }

    /**
     * A method to download json data from url
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            Log.d("Downloading url", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }


    // Fetches data from url passed
    private class DownloadTask extends AsyncTask<String, Void, String> {
        ParserTask parserTask = null;

        // Downloading data in non-ui thread
        @Override
        protected String doInBackground(String... url) {

            // For storing data from web service
            String data = "";

            try {
                // Fetching the data from web service
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Executes in UI thread, after the execution of
        // doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            parserTask = new ParserTask();

            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);
        }

    }

    /**
     * A class to parse the Google Places in JSON format
     */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                // Starts parsing data
                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            PolylineOptions lineOptions = null;

            int duration = 0, distance = 0;

            if (result.size() < 1) {
                Toast.makeText(getBaseContext(), "Non esiste un percorso per questi punti", Toast.LENGTH_SHORT).show();
                return;
            }

            // Traversing through all the routes
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();

                // Fetching i-th route
                List<HashMap<String, String>> path = result.get(i);

                // Fetching all the points in i-th route
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    try {
                        double lat = Double.parseDouble(point.get("lat"));
                        double lng = Double.parseDouble(point.get("lng"));
                        LatLng position = new LatLng(lat, lng);
                        points.add(position);

                    } catch (NullPointerException e) {}

                    try {
                        duration += Integer.parseInt(path.get(j).get("duration"));
                        distance += Integer.parseInt(path.get(j).get("distance"));
                    } catch (NumberFormatException e) {}

                }

                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(8);
                lineOptions.color(Color.BLUE);
                lineOptions.geodesic(true);
            }


            setTextView(distance, duration);

            // Drawing polyline in the Google Map for the i-th route
            mMap.addPolyline(lineOptions);

        }


        void setTextView (int distance, int duration) {
            double dist;

            if(distance>=1000) { //1km
                dist = (double) distance / 1000;
                if(dist>=100) { //100km
                    tvdistance.setText("Distanza: " + Integer.toString((int)dist) + " km");
                }
                else {

                    tvdistance.setText("Distanza: " + new DecimalFormat("#.#").format(dist) + " km");
                }
            }
            else {
                tvdistance.setText("Distanza: " + Integer.toString(distance) + " m");
            }


            // default speed walk googlemaps 4.8km/h approx, hypothesized slow running speed 8km/h
            int duration_run = (int) (duration * (4.8 / 8));
            int day = duration_run / (24*60*60);
            int hour = (duration_run - (day*24*60*60)) / (60*60);
            int min = (duration_run - (day*24*60*60 + hour*60*60)) / 60;
            int sec = (duration_run - (day*24*60*60 + hour*60*60 + min*60));

            if(day>0)
                tvduration.setText("Durata: " + Integer.toString(day) + "g "
                                            + Integer.toString(hour) + "h ");
            else if(hour>0)
                tvduration.setText("Durata: " + Integer.toString(hour) + "h "
                                            + Integer.toString(min) + "m ");
            else if (min>0)
                tvduration.setText("Durata: " + Integer.toString(min) + "m "
                                            + Integer.toString(sec) + "s");
            else
                tvduration.setText("Durata: " + Integer.toString(sec) + "s");

            tvdistance.setVisibility(View.VISIBLE);
            tvduration.setVisibility(View.VISIBLE);
        }
    }


    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        updateMapType();
    }



    private void updateMapType() {
        // No toast because this can also be called by the Android framework in onResume() at which
        // point mMap may not be ready yet.
        if (mMap == null) {
            return;
        }

        String layerName = ((String) mSpinner.getSelectedItem());
        if (layerName.equals(getString(R.string.normal))) {
            mMap.setMapType(MAP_TYPE_NORMAL);
        } else if (layerName.equals(getString(R.string.hybrid))) {
            mMap.setMapType(MAP_TYPE_HYBRID);


        } else if (layerName.equals(getString(R.string.satellite))) {
            mMap.setMapType(MAP_TYPE_SATELLITE);
        } else if (layerName.equals(getString(R.string.terrain))) {
            mMap.setMapType(MAP_TYPE_TERRAIN);
        }

    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}