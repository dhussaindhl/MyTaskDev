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

package com.danish.task.movies.activity.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONException;
import org.json.JSONObject;
import com.danish.task.movies.activity.MovieDatabaseHelper;
import org.notabug.lifeuser.moviedb.R;
import com.danish.task.movies.activity.activity.DetailActivity;
import com.danish.task.movies.activity.activity.FilterActivity;
import com.danish.task.movies.activity.activity.MainActivity;
import com.danish.task.movies.activity.adapter.ShowBaseAdapter;
import com.danish.task.movies.activity.listener.AdapterDataChangedListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import static android.os.Build.VERSION_CODES.M;

/**
 * Shows the shows that are stored in the local database.
 */
public class ListFragment extends BaseFragment implements AdapterDataChangedListener {

    private static boolean mDatabaseUpdate;
    final private int REQUEST_CODE_ASK_PERMISSIONS_EXPORT = 123;
    final private int REQUEST_CODE_ASK_PERMISSIONS_IMPORT = 124;
    private ArrayList<JSONObject> mShowBackupArrayList;
    private ArrayList<JSONObject> mSearchShowBackupArrayList;
    private boolean usedFilter = false;
    private SQLiteDatabase mDatabase;
    private MovieDatabaseHelper mDatabaseHelper;

    public ListFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new ListFragment object and returns it.
     *
     * @return the newly created ListFragment object.
     */
    public static ListFragment newInstance() {
        return new ListFragment();
    }

    /**
     * Creates and sets a new adapter when ListFragment is resumed.
     */
    public static void databaseUpdate() {
        mDatabaseUpdate = true;
    }

    @Override
    public void onAdapterDataChangedListener() {
        updateShowViewAdapter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDatabaseHelper = new MovieDatabaseHelper(getActivity().getApplicationContext());

        // Get all entries from the database,
        // put them in JSONObjects and send them to the ShowBaseAdapter.
        mShowArrayList = new ArrayList<>();

        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        createShowList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View fragmentView = inflater.inflate(R.layout.fragment_show, container, false);
        showShowList(fragmentView);
        return fragmentView;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onResume() {
        if (mDatabaseUpdate) {
            // The database is updated, load the changes into the array list.
            updateShowViewAdapter();
            mShowBackupArrayList = (ArrayList<JSONObject>) mShowArrayList.clone();
            mDatabaseUpdate = false;
        }
        super.onResume();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.database_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export) {
            // Ask the user for writing to storage (and thus also automatically reading) permission.
            if (android.os.Build.VERSION.SDK_INT >= M) {
                int hasWriteExternalStoragePermission = getActivity()
                        .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (hasWriteExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        final Activity activity = getActivity();
                        showMessageOKCancel(getActivity().getApplicationContext().getResources().getString(R.string.no_permission_dialog_message),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(activity, new String[]
                                                        {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                REQUEST_CODE_ASK_PERMISSIONS_EXPORT);
                                    }
                                });
                        return true;
                    }
                    ActivityCompat.requestPermissions(getActivity(), new String[]
                                    {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_CODE_ASK_PERMISSIONS_EXPORT);

                    return true;
                }
            }

            // Export the database.
            MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper
                    (getActivity().getApplicationContext());
            databaseHelper.exportDatabase(getActivity());
            return true;
        }

        // Import action
        if (id == R.id.action_import) {

            // Ask the user for reading from storage (and thus also automatically writing) permission.
            if (android.os.Build.VERSION.SDK_INT >= M) {
                int hasWriteExternalStoragePermission = getActivity()
                        .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (hasWriteExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        final Activity activity = getActivity();
                        ((MainActivity) activity).mAdapterDataChangedListener = this;
                        showMessageOKCancel(getActivity().getApplicationContext().getResources().getString(R.string.no_permission_dialog_message),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(activity, new String[]
                                                        {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                                REQUEST_CODE_ASK_PERMISSIONS_IMPORT);
                                    }
                                });
                        return true;
                    }
                    ActivityCompat.requestPermissions(getActivity(), new String[]
                                    {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_CODE_ASK_PERMISSIONS_IMPORT);

                    return true;
                }
            }

            // Import the database.
            MovieDatabaseHelper databaseHelper = new MovieDatabaseHelper(getActivity().getApplicationContext());
            databaseHelper.importDatabase(getActivity(), this);
            return true;
        }

        // Filter action
        if (id == R.id.action_filter) {
            // Start the FilterActivity
            Intent intent = new Intent(getActivity().getApplicationContext(), FilterActivity.class);
            intent.putExtra("categories", true);
            intent.putExtra("most_popular", false);
            intent.putExtra("dates", false);
            intent.putExtra("keywords", false);
            if (mShowBackupArrayList == null) {
                mShowBackupArrayList = (ArrayList<JSONObject>) mShowArrayList.clone();
            }
            getActivity().startActivityForResult(intent, FILTER_REQUEST_CODE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // If the user comes back from the filter activity, apply the filter.
        if (requestCode == FILTER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            usedFilter = true;
            filterAdapter();
        }
    }

    /**
     * Create and set the new adapter to update the show view.
     */
    private void updateShowViewAdapter() {
        mShowArrayList = getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC");
        mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                                           preferences.getBoolean(SHOWS_LIST_PREFERENCE, true));
        if (!mSearchView) {
            mShowView.setAdapter(mShowAdapter);
            scrollView.setAdapter(mShowAdapter);
        }
    }

    /**
     * Filters the shows based on the settings in the FilterActivity.
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    private void filterAdapter() {
        // Get the parameters from the filter activity and reload the adapter
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences
                (FilterActivity.FILTER_PREFERENCES, Context.MODE_PRIVATE);

        // Clone the ArrayList as the original needs to be kept
        // in case the filter settings are changed (and removed shows might need to be shown again).
        if (!mSearchView) {
            mShowArrayList = (ArrayList<JSONObject>) mShowBackupArrayList.clone();
        } else {
            mSearchShowArrayList = (ArrayList<JSONObject>) mSearchShowBackupArrayList.clone();
        }

        // Sort the ArrayList based on the chosen order.
        String sortPreference;
        if ((sortPreference = sharedPreferences.getString(FilterActivity.FILTER_SORT, null)) != null) {
            switch (sortPreference) {
                case "best_rated":
                    if (mSearchView) {
                        Collections.sort(mSearchShowArrayList, new Comparator<JSONObject>() {
                            @Override
                            public int compare(JSONObject firstObject, JSONObject secondObject) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    return Integer.compare(firstObject
                                            .optInt(ShowBaseAdapter.KEY_RATING), secondObject
                                            .optInt(ShowBaseAdapter.KEY_RATING)) * -1;
                                } else {
                                    return ((Integer) firstObject
                                            .optInt(ShowBaseAdapter.KEY_RATING))
                                            .compareTo(secondObject
                                                    .optInt(ShowBaseAdapter.KEY_RATING)) * -1;
                                }
                            }
                        });
                    } else {
                        Collections.sort(mShowArrayList, new Comparator<JSONObject>() {
                            @SuppressWarnings("UseCompareMethod")
                            @Override
                            public int compare(JSONObject firstObject, JSONObject secondObject) {
                                // * -1 is to make the order descending instead of ascending.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    return Integer.compare(firstObject
                                            .optInt(ShowBaseAdapter.KEY_RATING), secondObject
                                            .optInt(ShowBaseAdapter.KEY_RATING)) * -1;
                                } else {
                                    return ((Integer) firstObject
                                            .optInt(ShowBaseAdapter.KEY_RATING))
                                            .compareTo(secondObject
                                                    .optInt(ShowBaseAdapter.KEY_RATING)) * -1;
                                }
                            }
                        });
                    }
                    break;
                case "release_date":
                    if (mSearchView) {
                        Collections.sort(mSearchShowArrayList, new Comparator<JSONObject>() {
                            @Override
                            public int compare(JSONObject firstObject, JSONObject secondObject) {
                                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                                Date firstDate, secondDate;

                                try {
                                    firstDate = simpleDateFormat.parse(firstObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE));
                                    secondDate = simpleDateFormat.parse(secondObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                    return 0;
                                }

                                return (firstDate.getTime() > secondDate.getTime() ? -1 : 1);
                            }
                        });
                    } else {
                        Collections.sort(mShowArrayList, new Comparator<JSONObject>() {
                            @Override
                            public int compare(JSONObject firstObject, JSONObject secondObject) {
                                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                                Date firstDate, secondDate;

                                try {
                                    firstDate = simpleDateFormat.parse(firstObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE));
                                    secondDate = simpleDateFormat.parse(secondObject.optString(ShowBaseAdapter.KEY_RELEASE_DATE));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                    return 0;
                                }

                                return (firstDate.getTime() > secondDate.getTime() ? -1 : 1);
                            }
                        });
                    }
                    break;
                case "alphabetic_order":
                    if (mSearchView) {
                        Collections.sort(mSearchShowArrayList, new Comparator<JSONObject>() {
                            @Override
                            public int compare(JSONObject firstObject, JSONObject secondObject) {
                                return firstObject.optString(ShowBaseAdapter.KEY_TITLE)
                                        .compareToIgnoreCase(secondObject.optString(ShowBaseAdapter.KEY_TITLE));
                            }
                        });
                    } else {
                        Collections.sort(mShowArrayList, new Comparator<JSONObject>() {
                            @Override
                            public int compare(JSONObject firstObject, JSONObject secondObject) {
                                return firstObject.optString(ShowBaseAdapter.KEY_TITLE)
                                        .compareToIgnoreCase(secondObject.optString(ShowBaseAdapter.KEY_TITLE));
                            }
                        });
                    }
                    break;
            }
        }

        // Remove the movies that should not be displayed from the list.
        ArrayList<String> selectedCategories = FilterActivity.convertStringToArrayList
                (sharedPreferences.getString(FilterActivity.FILTER_CATEGORIES, null), ", ");
        // Filter the search list if the user was searching, otherwise filter the normal list.
        if (mSearchView && selectedCategories != null) {

            Iterator iterator = mSearchShowArrayList.iterator();
            while (iterator.hasNext()) {
                int columnWatched = ((JSONObject) iterator.next())
                        .optInt(MovieDatabaseHelper.COLUMN_CATEGORIES);
                boolean shouldKeep = false;
                for (int i = 0; i < selectedCategories.size(); i++) {
                    if (columnWatched == DetailActivity.getCategoryNumber(selectedCategories.get(i))) {
                        shouldKeep = true;
                        break;
                    }
                }

                if (!shouldKeep) {
                    iterator.remove();
                }
            }
        } else if (selectedCategories != null) {
            Iterator iterator = mShowArrayList.iterator();
            while (iterator.hasNext()) {
                int columnWatched = ((JSONObject) iterator.next())
                        .optInt(MovieDatabaseHelper.COLUMN_CATEGORIES);
                boolean shouldKeep = false;
                for (int i = 0; i < selectedCategories.size(); i++) {
                    if (columnWatched == DetailActivity.getCategoryNumber(selectedCategories.get(i))) {
                        shouldKeep = true;
                        break;
                    }
                }

                if (!shouldKeep) {
                    iterator.remove();
                }
            }
        }

        // Remove shows that do not contain certain genres from the list.
        ArrayList withGenres = FilterActivity.convertStringToIntegerArrayList
                (sharedPreferences.getString
                        (FilterActivity.FILTER_WITH_GENRES, null), ", ");

        if (withGenres != null && !withGenres.isEmpty()) {
            for (int i = 0; i < withGenres.size(); i++) {
                if (mSearchView) {
                    for (int j = 0; j < mSearchShowArrayList.size(); j++) {
                        JSONObject showObject = mSearchShowArrayList.get(j);
                        ArrayList<Integer> idList = FilterActivity.convertStringToIntegerArrayList
                                (showObject.optString(ShowBaseAdapter.KEY_GENRES), ",");

                        if (!idList.contains(withGenres.get(i))) {
                            mSearchShowArrayList.remove(j);
                        }
                    }
                } else {
                    for (int j = 0; j < mShowArrayList.size(); j++) {
                        JSONObject showObject = mShowArrayList.get(j);
                        ArrayList<Integer> idList = FilterActivity.convertStringToIntegerArrayList
                                (showObject.optString(ShowBaseAdapter.KEY_GENRES), ",");

                        if (!idList.contains(withGenres.get(i))) {
                            mShowArrayList.remove(j);
                        }
                    }
                }
            }
        }

        // Remove shows that contain certain genres from the list.
        ArrayList withoutGenres = FilterActivity.convertStringToIntegerArrayList
                (sharedPreferences.getString
                        (FilterActivity.FILTER_WITHOUT_GENRES, null), ", ");
        if (withoutGenres != null && !withoutGenres.isEmpty()) {
            for (int i = 0; i < withoutGenres.size(); i++) {
                if (mSearchView) {
                    for (int j = 0; j < mSearchShowArrayList.size(); j++) {
                        JSONObject showObject = mSearchShowArrayList.get(j);
                        ArrayList<Integer> idList = FilterActivity.convertStringToIntegerArrayList
                                (showObject.optString(ShowBaseAdapter.KEY_GENRES), ",");

                        if (idList.contains(withoutGenres.get(i))) {
                            mSearchShowArrayList.remove(j);
                        }
                    }
                } else {
                    for (int j = 0; j < mShowArrayList.size(); j++) {
                        JSONObject showObject = mShowArrayList.get(j);
                        ArrayList<Integer> idList = FilterActivity.convertStringToIntegerArrayList
                                (showObject.optString(ShowBaseAdapter.KEY_GENRES), ",");

                        if (idList.contains(withoutGenres.get(i))) {
                            mShowArrayList.remove(j);
                        }
                    }
                }
            }
        }

        // Set a new adapter with the cloned (and filtered) ArrayList.
        if (mSearchView) {
            mShowView.setAdapter(new ShowBaseAdapter(mSearchShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)));
            scrollView.setAdapter(new ShowBaseAdapter(mSearchShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)));
        } else {
            mShowView.setAdapter(new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)));
            scrollView.setAdapter(new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)));
        }
    }

    /**
     * Creates the ShowBaseAdapter with the ArrayList containing shows from the database and
     * genres loaded from the API.
     */
    private void createShowList() {
        mShowGenreList = new HashMap<>();
        mShowArrayList = getShowsFromDatabase(null, MovieDatabaseHelper.COLUMN_ID + " DESC");
        mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, true));

        if (mShowView != null) {
            mShowView.setAdapter(mShowAdapter);
            //scrollView.setAdapter(mShowAdapter);
        }
        if (scrollView != null) {

            scrollView.setAdapter(mShowAdapter);
        }
        // Load the genres
        new GenreList().execute("tv");
    }

    /**
     * Retrieves the shows from the database.
     *
     * @param searchQuery the text (if any) that the title should contain.
     * @param order       the order of the shows.
     * @return an ArrayList filled with the shows from the database
     * (optionally filtered and sorted on the given query and order).
     */
    private ArrayList<JSONObject> getShowsFromDatabase(String searchQuery, String order) {
        // Add the order that the output should be sorted on.
        // When no order is specified leave the string empty.
        String dbOrder;
        if (order != null) {
            dbOrder = " ORDER BY " + order;
        } else {
            dbOrder = "";
        }

        open();
        mDatabaseHelper.onCreate(mDatabase);

        Cursor cursor;
        // Search for shows that fulfill the searchQuery and fit in the list.
        if (searchQuery != null && !searchQuery.equals("")) {
            cursor = mDatabase.rawQuery("SELECT * FROM " + MovieDatabaseHelper.TABLE_MOVIES
                    + " WHERE " + MovieDatabaseHelper.COLUMN_TITLE + " LIKE '%"
                    + searchQuery + "%'" + dbOrder, null);
        } else {
            cursor = mDatabase.rawQuery("SELECT * FROM " +
                    MovieDatabaseHelper.TABLE_MOVIES + dbOrder, null);
        }

        return convertDatabaseListToArrayList(cursor);
    }

    /**
     * Goes through all the shows with the given cursor and adds them to the ArrayList.
     *
     * @param cursor the cursor containing the shows.
     * @return an ArrayList with all the shows from the cursor.
     */
    private ArrayList<JSONObject> convertDatabaseListToArrayList(Cursor cursor) {
        ArrayList<JSONObject> dbShowsArrayList = new ArrayList<>();

        cursor.moveToFirst();
        // Convert the cursor object to a JSONObject (that way the code can be reused).
        while (!cursor.isAfterLast()) {
            JSONObject showObject = new JSONObject();
            try {
                // Use the ShowBaseAdapter naming standards.
                showObject.put(ShowBaseAdapter.KEY_ID, cursor.getInt
                        (cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_MOVIES_ID))
                );
                if (!cursor.isNull(cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_PERSONAL_RATING))) {
                    showObject.put(ShowBaseAdapter.KEY_RATING, cursor.getInt(
                            cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_PERSONAL_RATING)
                    ));
                } else {
                    showObject.put(ShowBaseAdapter.KEY_RATING, cursor.getInt(
                            cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_RATING)
                    ));
                }
                showObject.put(ShowBaseAdapter.KEY_IMAGE, cursor.getString(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_IMAGE)
                ));
                showObject.put(ShowBaseAdapter.KEY_POSTER, cursor.getString(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_ICON)
                ));
                showObject.put(ShowBaseAdapter.KEY_TITLE, cursor.getString(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_TITLE)
                ));
                showObject.put(ShowBaseAdapter.KEY_DESCRIPTION, cursor.getString(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_SUMMARY)
                ));
                showObject.put(ShowBaseAdapter.KEY_GENRES, cursor.getString(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_GENRES_IDS)
                ));
                showObject.put(MovieDatabaseHelper.COLUMN_CATEGORIES, cursor.getInt(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_CATEGORIES)
                ));

                showObject.put(ShowBaseAdapter.KEY_DATE_MOVIE, cursor.getString(
                        cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_RELEASE_DATE)
                ));

                // Add a name key-value pair if it is a series.
                // (Otherwise ShowBaseAdapter won't recognise it as a series)
                if (cursor.getInt(cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_MOVIE)) != 1) {
                    showObject.put(ShowBaseAdapter.KEY_NAME, cursor.getInt(
                            cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_TITLE)
                    ));

                    // Same goes for release date.
                    showObject.put(ShowBaseAdapter.KEY_DATE_SERIES, cursor.getString(
                            cursor.getColumnIndex(MovieDatabaseHelper.COLUMN_RELEASE_DATE)
                    ));
                }
            } catch (JSONException je) {
                je.printStackTrace();
            }

            // Add the JSONObject to the list and move on to the next one.
            dbShowsArrayList.add(showObject);
            cursor.moveToNext();
        }
        cursor.close();
        close();

        return dbShowsArrayList;
    }

    /**
     * Gets (or creates) a writable database.
     *
     * @throws SQLException if the database cannot be opened for writing.
     */
    private void open() throws SQLException {
        mDatabase = mDatabaseHelper.getWritableDatabase();
    }

    /**
     * Closes the writable database.
     */
    private void close() {
        mDatabaseHelper.close();
    }

    /**
     * Shows a dialog with the given message and a positive (triggering the given listener) and
     * negative (does nothing) button.
     *
     * @param message    the message to be displayed
     * @param okListener the listener that should be triggered
     *                   when the user presses the positive button.
     */
    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(getActivity())
                .setMessage(message)
                .setPositiveButton(getActivity().getApplicationContext().getResources().getString(R.string.no_permission_dialog_ok), okListener)
                .setNegativeButton(getActivity().getApplicationContext().getResources().getString(R.string.no_permission_dialog_cancel), null)
                .create()
                .show();
    }

    /**
     * Sets a new adapter only containing the shows that fit the search query.
     * If a filter is being used, the retrieved shows will be filtered
     * and the adapter will be replaced again.
     *
     * @param query the text that the show title should contain.
     */
    public void search(String query) {
        if (!query.equals("")) {
            mSearchView = true;
            mSearchShowArrayList = getShowsFromDatabase(query, MovieDatabaseHelper.COLUMN_ID + " DESC");
            mSearchShowBackupArrayList = (ArrayList<JSONObject>) mSearchShowArrayList.clone();
            mSearchShowAdapter = new ShowBaseAdapter(mSearchShowArrayList, mShowGenreList,
                    preferences.getBoolean(SHOWS_LIST_PREFERENCE, true));
            mShowView.setAdapter(mSearchShowAdapter);
            scrollView.setAdapter(mSearchShowAdapter);
            // Only use the filter if the user has gone to the FilterActivity in this session.
            if (usedFilter) {
                filterAdapter();
            }
        }
    }
}
