package com.example.chilin_wang.locationsample;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class LocationSampleActivity extends AppCompatActivity implements LocationListener, GpsStatus.Listener {

    private static final String TAG = "locationtest";
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int INSIDE_HOME = 0;
    private static final int OUTSIDE_HOME = 1;
    LocationManager mLocationManager;
    Location mLocation, mPreLocation;
    Thread mThread = null;
    Thread mComputeTimeThread = null;
    private Button mStartButton, mStopButton, mSetHomeButton, mSetOutsideButton,mOpenDoorButton;
    private EditText mResult, mSettingHomeResult, mSettingOutsideResult;
    private float mSnr = 0;
    private String mLocationState;
    //for open door trigger,because initial states always home(need to set home's state)
    private int mLocationStateNow = INSIDE_HOME;
    private GpsStatus mGpsStatus;
    private List<Float> mGpsSatelliteList = new ArrayList();
    private int mStartDetectCount = 0;
    private boolean mIsFirstChangedToNetWork = false;
    /*setting location of home/outside,compute radious between home and outside
    * if location(either gps or network) from home is farer then radious, it will be adust to outside*/
    private boolean mSetHomeNow = false;
    private boolean mSetOutsideNow = false;
    private int mSetHomeCount = 0;
    private Location mHomeLocation, mOutsideLocation;
    private List<Location> mSettingLocation = new ArrayList();
    private float mRadious = 0;
    private float mAccuracy = 0;
    private float mDistance = 0;
    private float mAverageGpsSnrValue;
    private int mAverageGpsSnrNum;

    private OnClickListener startLocationListener = new OnClickListener() {
        public void onClick(View v) {
            if (!mSetHomeNow && !mSetOutsideNow) {
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 10, 0, LocationSampleActivity.this);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 10, 0, LocationSampleActivity.this);
                    mLocationManager.addGpsStatusListener(LocationSampleActivity.this);
                    startDetect();

                } else {
                    Toast.makeText(getApplicationContext(), "Please turn on location", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Setting location of Home/Outside now, can't start", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private OnClickListener stopLocationListener = new OnClickListener() {
        public void onClick(View v) {
            mLocationManager.removeUpdates(LocationSampleActivity.this);
            mLocationManager.removeGpsStatusListener(LocationSampleActivity.this);
            if (mThread != null) {
                mThread.interrupt();
                mThread = null;
            }
            if(mComputeTimeThread != null){
                mComputeTimeThread.interrupt();
                mComputeTimeThread = null;
            }
            if (mLocation != null) {
                mLocation.reset();
                mLocation = null;
            }
            if (mPreLocation != null) {
                mPreLocation.reset();
                mPreLocation = null;
            }
            mSetHomeNow = false;
            mSetOutsideNow = false;
        }
    };
    private OnClickListener setHomeListener = new OnClickListener() {
        public void onClick(View v) {

            if (mSetOutsideNow) {
                Toast.makeText(getApplicationContext(), "Setting location of Outside now, can't start", Toast.LENGTH_SHORT).show();
            } else {
                mSettingLocation.clear();
                mLocationManager.removeUpdates(LocationSampleActivity.this);
                mLocationManager.removeGpsStatusListener(LocationSampleActivity.this);
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mSetHomeNow = true;
                    mSetHomeCount = 0;
                    mHomeLocation = null;
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 2, 0, LocationSampleActivity.this);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 2, 0, LocationSampleActivity.this);
                    startDetect();
                    mSettingHomeResult.setText("Detect location,please wait a few minute");
                } else {
                    Toast.makeText(getApplicationContext(), "Please turn on location and network", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    private OnClickListener setOutsideListener = new OnClickListener() {
        public void onClick(View v) {
            if (mSetHomeNow) {
                Toast.makeText(getApplicationContext(), "Setting location of Home now, can't start", Toast.LENGTH_SHORT).show();
            } else {
                mSettingLocation.clear();
                mLocationManager.removeUpdates(LocationSampleActivity.this);
                mLocationManager.removeGpsStatusListener(LocationSampleActivity.this);
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mSetOutsideNow = true;
                    mSetHomeCount = 0;
                    mOutsideLocation = null;
                    mAverageGpsSnrValue = 0;
                    mAverageGpsSnrNum = 0;
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 2, 0, LocationSampleActivity.this);
                    mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 2, 0, LocationSampleActivity.this);
                    mLocationManager.addGpsStatusListener(LocationSampleActivity.this);
                    startDetect();
                    mSettingOutsideResult.setText("Detect location,please wait a few minute");
                } else {
                    Toast.makeText(getApplicationContext(), "Please turn on location", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    private OnClickListener openDoorListener = new OnClickListener() {
        public void onClick(View v) {
            if(mRadious > 0 && !mSetHomeNow && !mSetOutsideNow) {
                if (mLocationStateNow == INSIDE_HOME) {
                    mStartButton.callOnClick();
                    startComputeTime();
                    Toast.makeText(getApplicationContext(),"Start detect location!",Toast.LENGTH_LONG).show();
                } else if (mLocationStateNow == OUTSIDE_HOME) {
                    mResult.setText("Final result : Inside home , start action");
                    mLocationStateNow = INSIDE_HOME;
                }
            } else {
                Toast.makeText(getApplicationContext(),"Please setting location of Home and Outside",Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_sample);
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        initView();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mStopButton.callOnClick();
    }

    @Override
    public void onLocationChanged(Location location) {
        //detect user's location
        mPreLocation = mLocation;
        if (isBetterLocation(location, mPreLocation)) {
            mLocation = location;
            if (mSetHomeNow) {
            /*Setting location of home,detect 5 times of location to improve accurate
            use getAccuracy to improve accurate,the lower of the accuracy, the more accurate it has*/
                mSetHomeCount++;
                if (mSetHomeCount < 5) {
                    mSettingLocation.add(location);
                    mSettingHomeResult.setText("Count:" + mSetHomeCount + "\n" + location.getLatitude() + "," +
                            location.getLongitude() + "\nAccuracy:" + location.getAccuracy());
                } else {
                    mSettingLocation.add(location);
                    mHomeLocation = findBestLocation();

                    if (mOutsideLocation != null) {
                        mRadious = mHomeLocation.distanceTo(mOutsideLocation);
                        mSettingHomeResult.setText("Location:" + mHomeLocation.getLatitude() + "," + mHomeLocation.getLongitude());
                        mSettingOutsideResult.setText(mOutsideLocation.getLatitude() + "," + mOutsideLocation.getLongitude() +
                                "\nAccuracy : " + mOutsideLocation.getAccuracy() + "\nRadious :" + mRadious +
                                "\nAverage snr:" + mAverageGpsSnrValue + "\nAverage snr num:" + mAverageGpsSnrNum);
                    }
                    mSetHomeNow = false;
                    mLocationManager.removeUpdates(LocationSampleActivity.this);
                }
            } else if (mSetOutsideNow) {
                mSetHomeCount++;
                if (mSetHomeCount < 20) {
                    mSettingLocation.add(location);
                    StringBuilder output = computeGpsSnr();

                    mSettingOutsideResult.setText("Count:" + mSetHomeCount + "\n" + location.getLatitude() + "," +
                            location.getLongitude() + "\nAccuracy:" + location.getAccuracy() + "\nSNR :" + output);
                } else {
                    mSettingLocation.add(location);
                    mOutsideLocation = findBestLocation();
                    //use outsidelocation's accuracy to be the deviation,because gps is more accuracy than network
                    mAccuracy = mOutsideLocation.getAccuracy();
                    //compute average of GPS Snr in outside
                    computeGpsSnr();
                    mAverageGpsSnrValue = mAverageGpsSnrValue / 20;
                    mAverageGpsSnrNum = mAverageGpsSnrNum / 20;
                    if (mHomeLocation != null) {
                        mRadious = mHomeLocation.distanceTo(mOutsideLocation);
                        mSettingOutsideResult.setText("Location:" + mOutsideLocation.getLatitude() + "," + mOutsideLocation.getLongitude() +
                                "\nAccuracy : " + mOutsideLocation.getAccuracy() +
                                "\nAverage snr:" + mAverageGpsSnrValue + "\nAverage snr num:" + mAverageGpsSnrNum);
                        mSettingHomeResult.setText(mHomeLocation.getLatitude() + "," + mHomeLocation.getLongitude() + "\nRadious :" + mRadious);
                    }
                    mSetOutsideNow = false;
                    mLocationManager.removeUpdates(LocationSampleActivity.this);
                    mLocationManager.removeGpsStatusListener(LocationSampleActivity.this);
                }

            } else {
                boolean showResult = true;

                //if receive gps and network simultaneously,don't show network's result
                if (mPreLocation != null && mPreLocation.getProvider().equals(LocationManager.GPS_PROVIDER) &&
                        mLocation.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
                    mIsFirstChangedToNetWork = true;
                }
                //if previous location and now location both are network, locationstate is Inside(if gps signal is fine)
                if (mPreLocation != null && mLocation.getProvider().equals(LocationManager.NETWORK_PROVIDER)
                        && mLocation.getProvider().equals(mPreLocation.getProvider())) {
                    mIsFirstChangedToNetWork = false;
                }
                StringBuilder output = new StringBuilder();
                float snrAverageValue = 0;
                for (int i = 0; i < mGpsSatelliteList.size(); i++) {
                    snrAverageValue = snrAverageValue + mGpsSatelliteList.get(i);
                    output.append("\nSnr " + (i + 1) + " : " + mGpsSatelliteList.get(i));
                }
                Date lastUpdate = new Date(mLocation.getTime());
                DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault());
            /*if there is radious of home to outside,
            compute distance of location from home*/
                if (mRadious > 0) {
                    mDistance = mLocation.distanceTo(mHomeLocation);
                    float deviation = 0;
                    if (snrAverageValue / mGpsSatelliteList.size() < mAverageGpsSnrValue / 2 ||
                            mGpsSatelliteList.size() < mAverageGpsSnrNum / 2 ||
                            mLocation.getAccuracy() > (mRadious / 2 + mAccuracy)) {
                        deviation = mLocation.getAccuracy();
                    }
                    if (mDistance <= mRadious + deviation) {
                        mLocationState = "InSide home,by Radious";
                        mLocationStateNow = INSIDE_HOME;
                    } else if (mDistance <= mRadious + mAccuracy + deviation) {
                        mLocationState = "Close to home,by Radious";
                        mLocationStateNow = INSIDE_HOME;
                    } else if (mDistance > mRadious + mAccuracy + deviation) {
                        mLocationState = "OutSide home,by Radious";
                        mLocationStateNow = OUTSIDE_HOME;
                    }
                } else {
                    //if doesn't have the radious from home to outside,just adjust by signal of gps and network
                    if (mLocation.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                        mLocationState = "OutSide building";
                    } else {
                        if (mIsFirstChangedToNetWork) {
                            showResult = false;
                        } else {
                            mLocationState = "InSide building";
                        }
                    }
                }
                if (showResult) {
                    mResult.setText(mLocation.getLatitude() + "," + mLocation.getLongitude() +
                            "\nAccuracy:" + mLocation.getAccuracy() + "\nSpeed:" + mLocation.getSpeed() +
                            "\nProvider:" + mLocation.getProvider() + "\nGPS SNR:" + output +
                            "\nLast Updated: " + format.format(lastUpdate) +
                            "\ndistance: " + mDistance + "\nLocationState: " + mLocationState /*+
                        "\nmLocation =" + mLocation + "\nmPreLocation =" + mPreLocation*/);
                }
                startDetect();
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onGpsStatusChanged(int event) {
        if (mLocation != null) {
            mGpsStatus = mLocationManager.getGpsStatus(null);
            getGpsStatus(event, mGpsStatus);
        }
    }

    private void getGpsStatus(int event, GpsStatus status) {
        if (status != null) {
            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                int max = status.getMaxSatellites();
                Iterator<GpsSatellite> sat = status.getSatellites().iterator();
                mGpsSatelliteList.clear();
                int count = 0;
                while (sat.hasNext() && count <= max) {
                    GpsSatellite satellite = sat.next();
                    mGpsSatelliteList.add(satellite.getSnr());
                    count++;
                }
            }
        }
    }

    private void initView() {
        mStartButton = (Button) findViewById(R.id.start_location_button);
        mStopButton = (Button) findViewById(R.id.stop_location_button);
        mSetHomeButton = (Button) findViewById(R.id.set_home_location);
        mSetOutsideButton = (Button) findViewById(R.id.set_outside_location);
        mOpenDoorButton = (Button) findViewById(R.id.open_door_button);
        mResult = (EditText) findViewById(R.id.location_result_editText);
        mSettingHomeResult = (EditText) findViewById(R.id.setting_home_editText);
        mSettingOutsideResult = (EditText) findViewById(R.id.setting_outside_editText);


        mStartButton.setOnClickListener(startLocationListener);
        mStopButton.setOnClickListener(stopLocationListener);
        mSetHomeButton.setOnClickListener(setHomeListener);
        mSetOutsideButton.setOnClickListener(setOutsideListener);
        mOpenDoorButton.setOnClickListener(openDoorListener);
    }

    private void startDetect() {
        Log.d(TAG, "startDetect()" + "!mStartDetectCount=" + mStartDetectCount);
        if (mThread != null) {
            return;
        }
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000 * 40);
                } catch (InterruptedException e) {
                    return;
                }
                if (mLocation == null && mPreLocation == null) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (mSetHomeNow) {
                                if (mSetHomeCount == 0)
                                    mSettingHomeResult.setText("Cannot detect location,please connect to network");
                            } else if (mSetOutsideNow) {
                                if (mSetHomeCount == 0)
                                    mSettingOutsideResult.setText("Cannot detect location");
                            } else {
                                mResult.setText("Cannot detect location");
                            }
                        }
                    });
                } else if (mLocation != null && mPreLocation != null) {
                    if (mPreLocation.getProvider().equals(mLocation.getProvider())
                            && mPreLocation.getLongitude() == mLocation.getLongitude()
                            && mPreLocation.getLatitude() == mLocation.getLatitude()) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mResult.setText("Cannot detect locationchanged!\nlast LocationState :" + mLocationState +
                                        "\nmLocation =" + mLocation + "\nmPreLocation =" + mPreLocation);
                            }
                        });
                    }
                }
                //detect twice times to improve accuracy
                mPreLocation = mLocation;
                mThread = null;
                if (mStartDetectCount == 0) {
                    mStartDetectCount++;
                    startDetect();
                } else {
                    mStartDetectCount = 0;
                }
            }
        });
        mThread.start();
    }

    private void startComputeTime(){
        if (mComputeTimeThread != null) {
            return;
        }
        mComputeTimeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000 * 60);
                } catch (InterruptedException e) {
                    return;
                }
                if(mLocationStateNow == INSIDE_HOME){
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mResult.setText("Final Result : Inside home,don't action");
                        }
                    });
                } else if (mLocationStateNow == OUTSIDE_HOME){
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mResult.setText("Final Result : Outside home,start action");
                        }
                    });
                }
                mStopButton.callOnClick();
            }
        });
        mComputeTimeThread.start();
    }

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private Location findBestLocation() {
        /*1.find the counts of the same accuracy of location
        * 2.if there are the same counts of different accuracy,
        * choose more accurate(the less of accuracy,the more accurate) one
        * 3.use the location that the sum of distance is smallest*/
        Location location = null;
        float temp = 0;
        float sumOfDistance = 0;
        List<Float> listOfAccuracy = new ArrayList();
        List<Integer> countOfAccuracy = new ArrayList();
        List<Float> listOfDistance = new ArrayList();
        int i = 0;
        int j = 0;

        for (i = 0; i < mSettingLocation.size(); i++) {
            boolean isSame = false;
            for (j = 0; j < listOfAccuracy.size(); j++) {
                if (mSettingLocation.get(i).getAccuracy() == listOfAccuracy.get(j)) {
                    isSame = true;
                    break;
                }
            }
            if (!isSame) {
                listOfAccuracy.add(mSettingLocation.get(i).getAccuracy());
            }
        }
        for (i = 0; i < listOfAccuracy.size(); i++) {
            int count = 0;
            for(j = 0; j < mSettingLocation.size(); j++){
                if(listOfAccuracy.get(i) == mSettingLocation.get(j).getAccuracy()){
                    count++;
                }
            }
            countOfAccuracy.add(count);
        }
        i = 0;
        j = 1;
        while (j < countOfAccuracy.size()) {
            if (countOfAccuracy.get(i) < countOfAccuracy.get(j)) {
                temp = listOfAccuracy.get(j);
                i = j;
            } else if(countOfAccuracy.get(i) > countOfAccuracy.get(j)){
                temp = listOfAccuracy.get(i);
            } else if(countOfAccuracy.get(i) == countOfAccuracy.get(j)){
                if(listOfAccuracy.get(i) < listOfAccuracy.get(j)){
                    temp = listOfAccuracy.get(i);
                } else {
                    temp = listOfAccuracy.get(j);
                    i = j;
                }
            }
            j++;
        }

        for (i = 0; i < mSettingLocation.size(); i++) {
            sumOfDistance = 0;
            if (mSettingLocation.get(i).getAccuracy() == temp) {
                for (j = 0; j < mSettingLocation.size(); j++) {
                    if (i != j) {
                        sumOfDistance = sumOfDistance + mSettingLocation.get(i).distanceTo(mSettingLocation.get(j));
                    }
                }
            }
            listOfDistance.add(sumOfDistance);
        }
        i = 0;
        j = 1;
        while (j < listOfDistance.size()) {
            if (listOfDistance.get(i) == 0) {
                i = j;
            } else if (listOfDistance.get(i) != 0 && listOfDistance.get(j) != 0) {
                if (listOfDistance.get(i) > listOfDistance.get(j)) {
                    temp = listOfDistance.get(j);
                    i = j;
                } else {
                    temp = listOfDistance.get(i);
                }
            } else if (listOfDistance.get(i) != 0 && listOfDistance.get(j) == 0) {
                temp = listOfDistance.get(i);
            }
            j++;
        }
        int locationNum = 0;
        for (i = 0; i < listOfDistance.size(); i++) {
            if (listOfDistance.get(i) == temp) {
                location = mSettingLocation.get(i);
                locationNum = i;
            }
        }

        StringBuilder output = new StringBuilder();
        for (i = 0; i < mSettingLocation.size(); i++) {
            output.append("\nAccuracy of " + i + " : " + mSettingLocation.get(i).getAccuracy());
            for (j = i + 1; j < mSettingLocation.size(); j++) {
                float compareDistance = mSettingLocation.get(i).distanceTo(mSettingLocation.get(j));
                //output.append("\nlocation " + i + "compare to location " + j + " : " + compareDistance);
            }
        }
        output.append("\nLocation num = " + locationNum + "\ndistance sum :" + listOfDistance.get(locationNum));
        if (mSetHomeNow) {
            mSettingHomeResult.setText(output);
        } else if (mSetOutsideNow) {
            mSettingOutsideResult.setText(output);
        }
        return location;
    }

    private StringBuilder computeGpsSnr() {
        //compute average of GPS Snr in outside
        StringBuilder output = new StringBuilder();
        float snrsum = 0;
        for (int i = 0; i < mGpsSatelliteList.size(); i++) {
            snrsum = snrsum + mGpsSatelliteList.get(i);
            output.append("\nSnr " + (i + 1) + " : " + mGpsSatelliteList.get(i));
        }
        output.append("\naverage snr:" + snrsum / mGpsSatelliteList.size() + "\ntotal snr num:" + mGpsSatelliteList.size());
        if (mGpsSatelliteList.size() > 0) {
            mAverageGpsSnrValue = mAverageGpsSnrValue + snrsum / mGpsSatelliteList.size();
            mAverageGpsSnrNum = mAverageGpsSnrNum + mGpsSatelliteList.size();
        }
        return output;
    }
}

