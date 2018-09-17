package com.unimib.it.stage;

import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.StreetViewPanoramaCamera;
import com.google.android.gms.maps.model.StreetViewPanoramaLocation;
import com.google.vr.sdk.widgets.pano.VrPanoramaEventListener;
import com.google.vr.sdk.widgets.pano.VrPanoramaView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

public class StreetViewActivity extends FragmentActivity implements OnStreetViewPanoramaReadyCallback {

    private static final float ZOOM_BY = 0.25f;

    private StreetViewPanorama mStreetViewPanorama;
    private LatLng latLng;

    private static final String TAG = StreetViewActivity.class.getSimpleName();
    private VrPanoramaView panoWidgetView;
    public boolean loadImageSuccessful;
    /** Tracks the file to be loaded across the lifetime of this app. **/
    private Uri fileUri;
    /** Configuration information for the panorama. **/
    private VrPanoramaView.Options panoOptions = new VrPanoramaView.Options();
    private ImageLoaderTask backgroundImageLoaderTask;





    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Bundle b;
        b = getIntent().getExtras();
        latLng = new LatLng(b.getDouble("lat"), b.getDouble("lng"));


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streetview);

        SupportStreetViewPanoramaFragment streetViewPanoramaFragment =
                (SupportStreetViewPanoramaFragment)
                        getSupportFragmentManager().findFragmentById(R.id.streetviewactivity);

        streetViewPanoramaFragment.getStreetViewPanoramaAsync(this);

        panoWidgetView = (VrPanoramaView) findViewById(R.id.pano_view);
        panoWidgetView.setEventListener(new ActivityEventListener());
        // Initial launch of the app or an Activity recreation due to rotation.
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, this.hashCode() + ".onNewIntent()");
        // Save the intent. This allows the getIntent() call in onCreate() to use this new Intent during
        // future invocations.
        setIntent(intent);
        // Load the new image.
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        // Determine if the Intent contains a file to load.
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Log.i(TAG, "ACTION_VIEW Intent recieved");

            fileUri = intent.getData();
            if (fileUri == null) {
                Log.w(TAG, "No data uri specified. Use \"-d /path/filename\".");
            } else {
                Log.i(TAG, "Using file " + fileUri.toString());
            }

            panoOptions.inputType = intent.getIntExtra("inputType", VrPanoramaView.Options.TYPE_MONO);
            Log.i(TAG, "Options.inputType = " + panoOptions.inputType);
        } else {
            Log.i(TAG, "Intent is not ACTION_VIEW. Using default pano image.");
            fileUri = null;
            panoOptions.inputType = VrPanoramaView.Options.TYPE_MONO;
        }

        // Load the bitmap in a background thread to avoid blocking the UI thread. This operation can
        // take 100s of milliseconds.
        if (backgroundImageLoaderTask != null) {
            // Cancel any task from a previous intent sent to this activity.
            backgroundImageLoaderTask.cancel(true);
        }
        backgroundImageLoaderTask = new ImageLoaderTask();
        backgroundImageLoaderTask.execute(Pair.create(fileUri, panoOptions));
    }


    @Override
    public void onStreetViewPanoramaReady(StreetViewPanorama streetViewPanorama) {

        mStreetViewPanorama=streetViewPanorama;
        mStreetViewPanorama.setPosition(latLng);
        mStreetViewPanorama.setStreetNamesEnabled(false);
        mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(new StreetViewPanorama.OnStreetViewPanoramaChangeListener() {

            @Override
            public void onStreetViewPanoramaChange(StreetViewPanoramaLocation streetViewPanoramaLocation) {
                if (streetViewPanoramaLocation == null || streetViewPanoramaLocation.links == null) {
                    Toast.makeText(StreetViewActivity.this, "StreetView non presente per questo luogo", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent();
                    setResult(Activity.RESULT_CANCELED, intent);
                    finish();
                }
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK ) {
            Intent intent = new Intent();
            setResult(Activity.RESULT_CANCELED, intent);
            backgroundImageLoaderTask.cancel(true);
            finish();
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        {
            onZoomIn();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        {
            onZoomOut();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * When the panorama is not ready the PanoramaView cannot be used. This should be called on
     * all entry points that call methods on the Panorama API.
     */
    private boolean checkReady() {
        if (mStreetViewPanorama == null)
            return false;

        return true;
    }

    public void onZoomIn() {
        if (!checkReady()) {
            return;
        }

        mStreetViewPanorama.animateTo(
                new StreetViewPanoramaCamera.Builder().zoom(
                        mStreetViewPanorama.getPanoramaCamera().zoom + ZOOM_BY)
                        .tilt(mStreetViewPanorama.getPanoramaCamera().tilt)
                        .bearing(mStreetViewPanorama.getPanoramaCamera().bearing)
                        .build(), 10);
    }

    public void onZoomOut() {
        if (!checkReady()) {
            return;
        }

        mStreetViewPanorama.animateTo(
                new StreetViewPanoramaCamera.Builder().zoom(
                        mStreetViewPanorama.getPanoramaCamera().zoom - ZOOM_BY)
                        .tilt(mStreetViewPanorama.getPanoramaCamera().tilt)
                        .bearing(mStreetViewPanorama.getPanoramaCamera().bearing)
                        .build(), 10);
    }


    class ImageLoaderTask extends AsyncTask<Pair<Uri, VrPanoramaView.Options>, Void, Boolean>{


        @Override
        protected Boolean doInBackground(Pair<Uri, VrPanoramaView.Options>... fileInformation) {
            VrPanoramaView.Options panoOptions = null;  // It's safe to use null VrPanoramaView.Options.
            InputStream istr = null;
            InputStream istr1 = null;
            InputStream istr2 = null;

            int mode = getIntent().getIntExtra("mode",1);
            ArrayList<LatLng> points = getIntent().getParcelableArrayListExtra("points");

            if (mode == 1){
                if (fileInformation == null || fileInformation.length < 1
                        || fileInformation[0] == null || fileInformation[0].first == null) {
                    try {
                        Bundle b;
                        b = getIntent().getExtras();

                        String str;
                        str = "https://maps.googleapis.com/maps/api/streetview?size=4096x2048&location=" + b.getDouble("lat") + "," + b.getDouble("lng") +"&heading=0&fov=120";
                        URL url = new URL(str);
                        istr = url.openConnection().getInputStream();

                        String str1;
                        str1 = "https://maps.googleapis.com/maps/api/streetview?size=4096x2048&location=" + b.getDouble("lat") + "," + b.getDouble("lng") +"&heading=120&fov=120";
                        URL url1 = new URL(str1);
                        istr1 = url1.openConnection().getInputStream();

                        String str2;
                        str2 = "https://maps.googleapis.com/maps/api/streetview?size=4096x2048&location=" + b.getDouble("lat") + "," + b.getDouble("lng") +"&heading=240&fov=120";
                        URL url2 = new URL(str2);
                        istr2 = url2.openConnection().getInputStream();

                        panoOptions = new VrPanoramaView.Options();
                        panoOptions.inputType = VrPanoramaView.Options.TYPE_MONO;
                    } catch (IOException e) {
                        Log.e(TAG, "Could not decode default bitmap: " + e);
                        return false;
                    }
                } else {
                    try {
                        istr = new FileInputStream(new File(fileInformation[0].first.getPath()));
                        panoOptions = fileInformation[0].second;
                    } catch (IOException e) {
                        Log.e(TAG, "Could not load file: " + e);
                        return false;
                    }
                }

                Bitmap m1 = BitmapFactory.decodeStream(istr);
                Bitmap m2 = BitmapFactory.decodeStream(istr1);
                Bitmap m3 = BitmapFactory.decodeStream(istr2);

                Bitmap m4 = combineImages(m1, m2);
                Bitmap m = combineImages(m4, m3);



                panoWidgetView.loadImageFromBitmap(m, panoOptions);

            } else if (mode == 2) {
                int i = 0;
                int j = 0;
                double latitude = points.get(i).latitude;
                double longitude = points.get(i).longitude;
                double p = 1.700;
                double n = 10000;

                while (i < points.size()-1){
                    double lat1 = points.get(i).latitude;
                    double lon1 = points.get(i).longitude;
                    double lat2 = points.get(i + 1).latitude;
                    double lon2 = points.get(i + 1).longitude;
                    double lat3 = (lat1 + lat2)/2;
                    double lon3 = (lon1 + lon2)/2;

                    if (lat1 == lat2 & lon1 == lon2){
                        j = 4;
                    }

                    if (j == 0){
                        if (Math.abs(lat1 - latitude)*n>p || Math.abs(lon1 - longitude)*n>p){
                            latitude = (lat1 + latitude)/2;
                            longitude = (lon1 + longitude)/2;
                        } else {
                            latitude = lat1;
                            longitude = lon1;
                            j++;
                        }

                    } else if (j == 1){
                        double lat4 = (lat1 + lat3)/2;
                        double lon4 = (lon1 + lon3)/2;
                        if (Math.abs(lat4 - latitude)*n>p || Math.abs(lon4 - longitude)*n>p){
                            latitude = (lat4 + latitude)/2;
                            longitude = (lon4 + longitude)/2;
                        } else {
                            latitude = lat4;
                            longitude = lon4;
                            j++;
                        }

                    } else if (j == 2){
                        if (Math.abs(lat3 - latitude)*n>p || Math.abs(lon3 - longitude)*n>p){
                            latitude = (lat3 + latitude)/2;
                            longitude = (lon3 + longitude)/2;
                        } else {
                            latitude = lat3;
                            longitude = lon3;
                            j++;
                        }

                    } else if (j == 3){
                        double lat4 = (lat3 + lat2)/2;
                        double lon4 = (lon3 + lon2)/2;
                        if (Math.abs(lat4 - latitude)*n>p || Math.abs(lon4 - longitude)*n>p) {
                            latitude = (lat4 + latitude)/2;
                            longitude = (lon4 + longitude)/2;
                        } else {
                            latitude = lat4;
                            longitude = lon4;
                            j-=3;
                            i++;
                        }

                    } else {
                        latitude = lat1;
                        longitude = lon1;
                        i+=2;
                        j-=4;
                    }

                    if (fileInformation == null || fileInformation.length < 1
                            || fileInformation[0] == null || fileInformation[0].first == null) {
                        try {
                            String str;
                            str = "https://maps.googleapis.com/maps/api/streetview?size=4096x2048&location=" + latitude + "," + longitude +"&heading=0&fov=120";
                            URL url = new URL(str);
                            istr = url.openConnection().getInputStream();

                            String str1;
                            str1 = "https://maps.googleapis.com/maps/api/streetview?size=4096x2048&location=" + latitude + "," + longitude +"&heading=120&fov=120";
                            URL url1 = new URL(str1);
                            istr1 = url1.openConnection().getInputStream();

                            String str2;
                            str2 = "https://maps.googleapis.com/maps/api/streetview?size=4096x2048&location=" + latitude + "," + longitude +"&heading=240&fov=120";
                            URL url2 = new URL(str2);
                            istr2 = url2.openConnection().getInputStream();

                            panoOptions = new VrPanoramaView.Options();
                            panoOptions.inputType = VrPanoramaView.Options.TYPE_MONO;
                        } catch (IOException e) {
                            Log.e(TAG, "Could not decode default bitmap: " + e);
                            return false;
                        }
                    } else {
                        try {
                            istr = new FileInputStream(new File(fileInformation[0].first.getPath()));
                            panoOptions = fileInformation[0].second;
                        } catch (IOException e) {
                            Log.e(TAG, "Could not load file: " + e);
                            return false;
                        }
                    }

                    Bitmap m1 = BitmapFactory.decodeStream(istr);
                    Bitmap m2 = BitmapFactory.decodeStream(istr1);
                    Bitmap m3 = BitmapFactory.decodeStream(istr2);

                    Bitmap m4 = combineImages(m1, m2);
                    Bitmap m = combineImages(m4, m3);

                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch(InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                    }

                    panoWidgetView.loadImageFromBitmap(m, panoOptions);

                }

            }

            try {
                istr.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close input stream: " + e);
            }

            return true;
        }


        public Bitmap combineImages(Bitmap c, Bitmap s) { // can add a 3rd parameter 'String loc' if you want to save the new image - left some code to do that at the bottom
            Bitmap cs = null;

            int width, height = 0;

            if(c.getHeight() > s.getHeight()) {
                width = c.getWidth() + s.getWidth();
                height = c.getHeight();
            } else {
                width = c.getWidth() + s.getWidth();
                height = s.getHeight();
            }

            cs = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            Canvas comboImage = new Canvas(cs);

            comboImage.drawBitmap(c, 0f, 0f, null);
            comboImage.drawBitmap(s, c.getWidth(), 0f, null);

            return cs;
        }
    }

    /*
     * Listen to the important events from widget.
     */
    private class ActivityEventListener extends VrPanoramaEventListener {
        /*
         * Called by pano widget on the UI thread when it's done loading the image.
         */
        @Override
        public void onLoadSuccess() {
            loadImageSuccessful = true;
        }

        /**
         * Called by pano widget on the UI thread on any asynchronous error.
         */
        @Override
        public void onLoadError(String errorMessage) {
            loadImageSuccessful = false;
            Toast.makeText(
                    StreetViewActivity.this, "Error loading pano: " + errorMessage, Toast.LENGTH_LONG)
                    .show();
            Log.e(TAG, "Error loading pano: " + errorMessage);
        }
    }

}
