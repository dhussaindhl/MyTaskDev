/*
 * Copyright (c) 2018.
 *
 * This file is part of MovieDB.
 *
 * MovieDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MovieDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MovieDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.danish.task.movies.activity.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

import org.notabug.lifeuser.moviedb.R;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * This class contains some basic functionality that would
 * otherwise be duplicated in multiple activities.
 */
@SuppressLint("Registered")
public class BaseActivity extends AppCompatActivity {

    private final static String API_LANGUAGE_PREFERENCE = "key_api_language";
    private ConnectivityReceiver connectivityReceiver;
    private IntentFilter intentFilter;

    /**
     * Returns the language that is used by the phone.
     * Usage: this is only meant to be used at the end of the API url.
     * Otherwise an ampersand needs to be added manually at the end
     * and the possibility that an empty string can be returned
     * (which will interfere with the manual ampersand) must be
     * taken into account.
     */
    public static String getLanguageParameter(Context context) {
        String languageParameter = "&language=";

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String userPickedLanguage = preferences.getString(API_LANGUAGE_PREFERENCE, null);
        if (userPickedLanguage != null && !userPickedLanguage.isEmpty()) {
            return languageParameter + userPickedLanguage;
        } else if (!Locale.getDefault().getLanguage().equals("en")) {
            return languageParameter + Locale.getDefault().getLanguage();
        }
        return "";
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Creates the toolbar.
     */
    void setNavigationDrawer() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /**
     * Creates a home button in the toolbar.
     */
    void setBackButtons() {
        // Add back button to the activity.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
    }

    /**
     * Checks if a network is available.
     * If/Once a network connection is established, it calls doNetworkWork().
     */
    public void checkNetwork() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            connectivityManager.registerNetworkCallback(builder.build(),
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network network) {

                            new Thread(new Runnable(){
                                @Override
                                public void run(){
                                    //your code that access internet here
                                    doNetworkWork();
                                }
                            }).start();
                        }
                    });
        } else {
            connectivityReceiver = new ConnectivityReceiver();
            intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(connectivityReceiver,
                    intentFilter);
        }

        // Check if there is an Internet connection, if not tell the user.
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            Toast.makeText(getApplicationContext(), getApplicationContext().getResources()
                    .getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show();
        } else {
            // Check if the server is online.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!InetAddress.getByName("themoviedb.org").isReachable(3000)) {
                         //   Toast.makeText(getApplicationContext(), getString(R.string.tmdb_server_unreachable), Toast.LENGTH_SHORT).show();
                        }
                    } catch (UnknownHostException uhe) {
                        uhe.printStackTrace();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }).start();
        }
    }

    void doNetworkWork() {}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Back button
        if (id == android.R.id.home) {
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && connectivityReceiver != null) {
            registerReceiver(connectivityReceiver, intentFilter);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && connectivityReceiver != null) {
            unregisterReceiver(connectivityReceiver);
        }
    }

    public class ConnectivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager
                    = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE );
            NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetInfo != null && activeNetInfo.isConnectedOrConnecting()) {
                doNetworkWork();
            }
        }
    }
}
