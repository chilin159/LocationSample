package com.example.chilin_wang.locationsample;
import android.app.Application;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
public class LocationSampleApplication extends Application {
    private RefWatcher mRefWatcher;

    @Override public void onCreate() {
        super.onCreate();
        mRefWatcher = LeakCanary.install(this);
    }
}