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

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.danish.task.movies.activity.MovieDatabaseHelper;
import org.notabug.lifeuser.moviedb.R;
import com.danish.task.movies.activity.adapter.SectionsPagerAdapter;
import com.danish.task.movies.activity.fragment.BaseFragment;
import com.danish.task.movies.activity.fragment.ListFragment;
import com.danish.task.movies.activity.fragment.PersonFragment;
import com.danish.task.movies.activity.fragment.ShowFragment;
import com.danish.task.movies.activity.listener.AdapterDataChangedListener;

import java.io.File;
import java.util.List;

/*
* nnuuneoi's article was a great help by programming the permissions for API 23 and higher.
* Article: https://inthecheesefactory.com/blog/things-you-need-to-know-about-android-m-permission-developer-edition
 */

public class MainActivity extends BaseActivity {

    @SuppressWarnings("OctalInteger")
    private final static int SETTINGS_REQUEST_CODE = 0001;
    public final static int RESULT_SETTINGS_PAGER_CHANGED = 1001;
    private final static int REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123;
    private final static int REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124;
    final static int FILTER_REQUEST_CODE = 0002;
    private final static String LIVE_SEARCH_PREFERENCE = "key_live_search";
    private final static String REWATCHED_FIELD_CHANGE_PREFERENCE = "key_rewatched_field_change";
    private final static String PREVIOUS_APPLICATION_VERSION_PREFERENCE = "key_application_version";
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    // Variables used for searching
    private MenuItem mSearchAction;
    private boolean isSearchOpened = false;
    private EditText editSearch;
    private SharedPreferences preferences;
    ViewPager mViewPager;


    public AdapterDataChangedListener mAdapterDataChangedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set the default preference values.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Create the adapter that will return a fragment for each of the four
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this);

        // Set up the ViewPager with the sections adapter.
        /*
      The {@link ViewPager} that will host the section contents.
     */
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int versionNumber;
        try {
            versionNumber = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            versionNumber = -1;
        }

        // The rewatched field has changed to watched, notify users that make use of the database
        // of this change and also tell them that the value is automatically increased by one.
        if (!preferences.getBoolean(REWATCHED_FIELD_CHANGE_PREFERENCE, false) && preferences.getInt(PREVIOUS_APPLICATION_VERSION_PREFERENCE, versionNumber) < 190) {
            File dbFile = getDatabasePath(MovieDatabaseHelper.getDatabaseFileName());

            // If there is a database.
            if (dbFile.exists()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getString(R.string.watched_upgrade_dialog_message))
                        .setTitle(getString(R.string.watched_upgrade_dialog_title));

                builder.setPositiveButton(getString(R.string.watched_upgrade_dialog_positive),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Don't change the values of shows that haven't been watched and have a
                                // rewatch value of zero.
                                MovieDatabaseHelper databaseHelper
                                        = new MovieDatabaseHelper(getApplicationContext());
                                SQLiteDatabase database = databaseHelper.getWritableDatabase();
                                databaseHelper.onCreate(database);

                                Cursor cursor = database.rawQuery("SELECT * FROM " +
                                        MovieDatabaseHelper.TABLE_MOVIES, null);

                                // Go through all rows in the database.
                                cursor.moveToFirst();
                                while (!cursor.isAfterLast()) {
                                    if (!cursor.isNull(cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED))) {
                                        int rewatchedValue = cursor.getInt(cursor.getColumnIndex
                                                (MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED));
                                        // In case of a value of zero, check if the show is watched.
                                        if ((rewatchedValue == 0 && cursor.getInt(cursor.getColumnIndex(
                                                MovieDatabaseHelper.COLUMN_CATEGORIES)) == 1) || rewatchedValue != 0) {
                                            ContentValues watchedValues = new ContentValues();
                                            watchedValues.put(MovieDatabaseHelper.COLUMN_PERSONAL_REWATCHED,
                                                    (rewatchedValue + 1));
                                            database.update(MovieDatabaseHelper.TABLE_MOVIES, watchedValues,
                                                    MovieDatabaseHelper.COLUMN_MOVIES_ID + "="
                                                            + cursor.getInt(cursor.getColumnIndex
                                                            (MovieDatabaseHelper.COLUMN_MOVIES_ID)), null);
                                        }
                                    }
                                    cursor.moveToNext();
                                }

                                database.close();
                            }
                        });

                builder.setNegativeButton(getString(R.string.watched_upgrade_dialog_negative), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Don't do anything.
                    }
                });

                builder.show();

                preferences.edit().putBoolean(REWATCHED_FIELD_CHANGE_PREFERENCE, true).apply();
            }
        }

        if (versionNumber != -1) {
            preferences.edit().putInt(PREVIOUS_APPLICATION_VERSION_PREFERENCE, versionNumber).commit();
        }
    }

    @Override
    void doNetworkWork() {
        // Pass the call to all fragments.
        FragmentManager fragmentManager = getSupportFragmentManager();
        List<Fragment> fragmentList = fragmentManager.getFragments();
        for (Fragment fragment : fragmentList) {

            BaseFragment baseFragment = (BaseFragment) fragment;
            baseFragment.doNetworkWork();
        }

    }

    @Override
    public void onBackPressed() {
        // When search is opened and the user presses back,
        // execute a custom action (removing search query or stop searching)
        if (isSearchOpened) {
            handleMenuSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Search action
        if (id == R.id.action_search) {
            handleMenuSearch();
            return true;
        }

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivityForResult(intent, SETTINGS_REQUEST_CODE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Needs to be explicitly called otherwise
        // onActivityResult won't trigger in the Fragment.

        // This is a hack
        Fragment mCurrentFragment = getSupportFragmentManager()
                .findFragmentByTag("android:switcher:" + R.id.container + ":" + mViewPager.getCurrentItem());
        mCurrentFragment.onActivityResult(requestCode, resultCode, data);

        // Set the adapter again (in case the tabs setting changed).
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_SETTINGS_PAGER_CHANGED) {
            mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), this);
            mViewPager.setAdapter(mSectionsPagerAdapter);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mSearchAction = menu.findItem(R.id.action_search);
        MenuItem mFilterAction = menu.findItem(R.id.action_filter);
        return super.onPrepareOptionsMenu(menu);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS_EXPORT:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper(this);
                    databaseHelper.exportDatabase(this);
                } // else: permission denied
                break;
            case REQUEST_CODE_ASK_PERMISSIONS_IMPORT:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper(this);
                    databaseHelper.importDatabase(this, mAdapterDataChangedListener);
                } // else: permission denied
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Handles input from the search bar and icon.
     */
    private void handleMenuSearch() {
        // Searching will be done in the actionbar.
        ActionBar action = getSupportActionBar();
        if (action == null) {
            Log.e("MainActivity.java", "getSupportActionBar returned null");
            return;
        }

        final boolean liveSearch = preferences.getBoolean(LIVE_SEARCH_PREFERENCE, true);

        // Although the icon changes, the calls are the same,
        // that's why there is a if-statement, to differ
        // requests to start a search from requests to end a search.
        if (isSearchOpened) {
            // Close the search or delete the search query.
            if (editSearch.getText().toString().equals("")) {
                action.setDisplayShowCustomEnabled(true);

                // Change the return key into a search key.
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
                }

                mSearchAction.setIcon(getResources().getDrawable(R.drawable.ic_search));

                isSearchOpened = false;

                // Remove the search view and display the title again.
                action.setCustomView(null);
                action.setDisplayShowTitleEnabled(true);

                cancelSearchInFragment();
            } else {
                // Delete the search query.
                editSearch.setText("");
            }
        } else {
            // Replace the title with a search bar.
            action.setDisplayShowCustomEnabled(true);
            action.setCustomView(R.layout.search_bar);
            action.setDisplayShowTitleEnabled(false);

            editSearch = (EditText) action.getCustomView().findViewById(R.id.editSearch);

            // Execute searchInFragment(query) when the search key is pressed.
            editSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        searchInFragment(editSearch.getText().toString());
                        return true;
                    }
                    return false;
                }
            });

            // Call searchInFragment(query) every time that the search query is changed.
            // (If this is not disabled in the settings.)
            editSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (liveSearch) {
                        searchInFragment(editSearch.getText().toString());
                    }
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            // When clicking on the search icon, focus on the search bar and display the soft keyboard.
            editSearch.requestFocus();

            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editSearch, InputMethodManager.SHOW_IMPLICIT);
            }

            mSearchAction.setIcon(getResources().getDrawable(R.drawable.ic_close));

            isSearchOpened = true;
        }
    }

    /**
     * Do the actual searching in the fragment (that is currently active).
     *
     * @param query the search query.
     */
    private void searchInFragment(String query) {
        // This is a hack
        Fragment mCurrentFragment = getSupportFragmentManager()
                .findFragmentByTag("android:switcher:" + R.id.container + ":" + mViewPager.getCurrentItem());

        if (mCurrentFragment != null) {
            if (mCurrentFragment instanceof ShowFragment) {
                ((ShowFragment) mCurrentFragment).search(query);
            }

            if (mCurrentFragment instanceof ListFragment) {
                ((ListFragment) mCurrentFragment).search(query);
            }

            if (mCurrentFragment instanceof PersonFragment) {
                ((PersonFragment) mCurrentFragment).search(query);
            }
        }
    }

    /**
     * Cancel the searching process in the fragment.
     */
    private void cancelSearchInFragment() {
        // This is a hack
        Fragment mCurrentFragment = getSupportFragmentManager()
                .findFragmentByTag("android:switcher:" + R.id.container + ":" + mViewPager.getCurrentItem());

        if (mCurrentFragment != null) {
            if (mCurrentFragment instanceof ShowFragment) {
                ((ShowFragment) mCurrentFragment).cancelSearch();
            }

            if (mCurrentFragment instanceof ListFragment) {
                ((ListFragment) mCurrentFragment).cancelSearch();
            }

            if (mCurrentFragment instanceof PersonFragment) {
                ((PersonFragment) mCurrentFragment).cancelSearch();
            }
        }
    }
}
