package com.example.BookMate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

/**
 * NetworkMonitor.java — Dynamic Network & WiFi State Monitor
 * Week 10 & 11: Detects device connectivity transitions (WiFi / Cellular).
 *
 * Implements:
 * 1) Dynamic BroadcastReceiver listening to system broadcasts:
 *    - ConnectivityManager.CONNECTIVITY_ACTION
 *    - "android.net.wifi.WIFI_STATE_CHANGED"
 * 2) Modern ConnectivityManager.NetworkCallback (API 24+) for instant,
 *    battery-efficient reactive callbacks.
 */
public class NetworkMonitor {

    public interface NetworkChangeListener {
        void onNetworkChanged(boolean isOnline);
    }

    private final Context context;
    private final NetworkChangeListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BroadcastReceiver broadcastReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isRegistered = false;
    private Boolean lastReportedState = null;
    private int registrationGeneration;

    public NetworkMonitor(Context context, NetworkChangeListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /**
     * Checks if the device is currently connected to WiFi, cellular, or ethernet.
     */
    public static boolean isOnline(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            Network activeNet = cm.getActiveNetwork();
            return activeNet != null && hasInternet(cm.getNetworkCapabilities(activeNet));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasInternet(NetworkCapabilities caps) {
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Registers both the BroadcastReceiver (Lab 10) and NetworkCallback for reliable updates.
     */
    public synchronized void start() {
        if (isRegistered) return;
        isRegistered = true;
        final int generation = ++registrationGeneration;
        lastReportedState = null;

        // Immediate initial check
        notifyState(isOnline(context), generation);

        // 1. Dynamic BroadcastReceiver (Lab 10 requirement)
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                notifyState(isOnline(ctx), generation);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.STATE_CHANGE");
        context.registerReceiver(broadcastReceiver, filter);

        // 2. Modern ConnectivityManager NetworkCallback (API 24+)
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                        notifyState(hasInternet(capabilities), generation);
                    }

                    @Override
                    public void onLost(Network network) {
                        mainHandler.post(() -> notifyState(isOnline(context), generation));
                    }
                };
                cm.registerDefaultNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Unregisters receiver and callbacks to prevent memory leaks.
     */
    public synchronized void stop() {
        if (!isRegistered) return;
        isRegistered = false;
        registrationGeneration++;
        mainHandler.removeCallbacksAndMessages(null);

        if (broadcastReceiver != null) {
            try {
                context.unregisterReceiver(broadcastReceiver);
            } catch (Exception ignored) {
            }
            broadcastReceiver = null;
        }

        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }
    }

    private void notifyState(boolean online, int generation) {
        mainHandler.post(() -> {
            if (!isRegistered || generation != registrationGeneration) return;
            if (lastReportedState != null && lastReportedState == online) return;
            lastReportedState = online;
            if (listener != null) {
                listener.onNetworkChanged(online);
            }
        });
    }
}
