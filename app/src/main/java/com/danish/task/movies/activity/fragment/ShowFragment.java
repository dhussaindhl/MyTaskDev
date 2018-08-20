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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.danish.task.movies.activity.ConfigHelper;
import org.notabug.lifeuser.moviedb.R;
import com.danish.task.movies.activity.activity.BaseActivity;
import com.danish.task.movies.activity.activity.FilterActivity;
import com.danish.task.movies.activity.adapter.SectionsPagerAdapter;
import com.danish.task.movies.activity.adapter.ShowBaseAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;

import static com.danish.task.movies.activity.activity.BaseActivity.getLanguageParameter;

public class ShowFragment extends BaseFragment {

    private static final String ARG_LIST_TYPE = "arg_list_type";
    private AsyncTask mSearchTask;
    private String mListType;
    private boolean mSearchView;
    private String mSearchQuery;
    private String filterParameter = "";
    private boolean filterChanged = false;
    private String mMode;
    // Variables for scroll detection
    private int visibleThreshold = 3; // Three times the amount of items in a row
    private int currentPage = 0;
    private int currentSearchPage = 0;
    private int previousTotal = 0;
    private boolean loading = true;
    private int pastVisibleItems;
    private int visibleItemCount;
    private int totalItemCount;

    private boolean mShowListLoaded = false;

    public ShowFragment() {
        // Required empty public constructor
    }

    /**
     * Creates a new ShowFragment object (with listType as Bundle string) and returns it.
     *
     * @param listType the type of list (movies or series) that needs to be loaded.
     * @return the newly created ShowFragment object.
     */
    public static ShowFragment newInstance(String listType) {
        ShowFragment fragment = new ShowFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LIST_TYPE, listType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mListType = getArguments().getString(ARG_LIST_TYPE);
        } else {
            // Movie is the default case.
            mListType = SectionsPagerAdapter.MOVIE;
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        visibleThreshold *= preferences.getInt(GRID_SIZE_PREFERENCE, 3);

        createShowList(mListType);
    }

    @Override
    public void doNetworkWork() {
        if (!mGenreListLoaded) {
            new GenreList().execute(mListType);
        }

        if (!mShowListLoaded) {
            new ShowList().execute(mListType, "1");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View fragmentView = inflater.inflate(R.layout.fragment_show, container, false);
        showShowList(fragmentView);
        return fragmentView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Filter action
        if (id == R.id.action_filter) {
            // Start the FilterActivity
            Intent intent = new Intent(getActivity().getApplicationContext(), FilterActivity.class);
            intent.putExtra("mode", mListType);
            getActivity().startActivityForResult(intent, FILTER_REQUEST_CODE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StringConcatenationInLoop")
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILTER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            filterShows();
        }
    }

    /**
     * Filters the list of shows based on the preferences set in FilterActivity.
     */
    private void filterShows() {
        // Get the parameters from the filter activity and reload the adapter
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences
                (FilterActivity.FILTER_PREFERENCES, Context.MODE_PRIVATE);

        String sortPreference;
        if ((sortPreference = sharedPreferences.getString(FilterActivity.FILTER_SORT, null)) != null)
            switch (sortPreference) {
                case "best_rated":
                    filterParameter = "sort_by=vote_average.desc";
                    break;
                case "release_date":
                    filterParameter = "sort_by=release_date.desc";
                    break;
                case "alphabetic_order":
                    filterParameter = "sort_by=original_title.desc";
                    break;
                default:
                    // This will also be selected when 'most_popular' is checked.
                    filterParameter = "sort_by=popularity.desc";
                    break;
            }

        // Add the dates as constraints to the new API call.
        String datePreference;
        if ((datePreference = sharedPreferences.getString(FilterActivity.FILTER_DATES, null)) != null) {
            switch (datePreference) {
                case "in_theater":
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    String today = simpleDateFormat.format(new Date());

                    Calendar calendar = GregorianCalendar.getInstance();
                    calendar.setTime(new Date());
                    calendar.add(Calendar.DAY_OF_YEAR, -31);
                    String monthAgo = simpleDateFormat.format(calendar.getTime());

                    filterParameter += "&primary_release_date.gte=" + monthAgo
                            + "&primary_release_date.lte=" + today;
                    break;
                case "between_dates":
                    String startDate;
                    if ((startDate = sharedPreferences.getString
                            (FilterActivity.FILTER_START_DATE, null)) != null) {
                        filterParameter += "&primary_release_date.gte=" + startDate;
                    }

                    String endDate;
                    if ((endDate = sharedPreferences.getString
                            (FilterActivity.FILTER_END_DATE, null)) != null) {
                        filterParameter += "&primary_release_date.lte=" + endDate;
                    }
                    break;
                default:
                    // Do nothing.
                    break;
            }
        }

        // Add the genres to be included as constraints to the API call.
        ArrayList withGenres = FilterActivity.convertStringToArrayList
                (sharedPreferences.getString
                        (FilterActivity.FILTER_WITH_GENRES, null), ", ");
        if (withGenres != null && !withGenres.isEmpty()) {
            filterParameter += "&with_genres=";
            for (int i = 0; i < withGenres.size(); i++) {
                filterParameter += withGenres.get(i);

                if (!((i + 1) == withGenres.size())) {
                    filterParameter += ",";
                }
            }
        }

        // Add the genres to be excluded as constraints to the API call.
        ArrayList withoutGenres = FilterActivity.convertStringToArrayList
                (sharedPreferences.getString
                        (FilterActivity.FILTER_WITHOUT_GENRES, null), ", ");
        if (withoutGenres != null && !withoutGenres.isEmpty()) {
            filterParameter += "&without_genres=";
            for (int i = 0; i < withoutGenres.size(); i++) {
                filterParameter += withoutGenres.get(i);

                if (!((i + 1) == withoutGenres.size())) {
                    filterParameter += ",";
                }
            }
        }

        // Add keyword-IDs as the constraints to the API call.
        String withKeywords;
        if (!(withKeywords = sharedPreferences.getString
                (FilterActivity.FILTER_WITH_KEYWORDS, "")).equals("")) {
            filterParameter += "&with_keywords=" + withKeywords;
        }

        String withoutKeywords;
        if (!(withoutKeywords = sharedPreferences.getString
                (FilterActivity.FILTER_WITHOUT_KEYWORDS, "")).equals("")) {
            filterParameter += "&without_keywords=" + withoutKeywords;
        }

        filterChanged = true;
        new ShowList().execute(mMode, "1");
    }

    /**
     * Loads a list of shows from the API.
     *
     * @param mode determines if series or movies are retrieved.
     */
    private void createShowList(String mode) {
        mMode = mode;

        // Create a MovieBaseAdapter and load the first page
        mShowArrayList = new ArrayList<>();
        mShowGenreList = new HashMap<>();
        mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, false));


        mShowAdapter = new ShowBaseAdapter(mShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, false));
        ((BaseActivity) getActivity()).checkNetwork();

        // Use persistent filtering if it is enabled.
        if (preferences.getBoolean(PERSISTENT_FILTERING_PREFERENCE, false)) {
            filterShows();
        }
    }

    /**
     * Visualises the list of shows on the screen.
     *
     * @param fragmentView the view to attach the ListView to.
     */
    void showShowList(final View fragmentView) {
        super.showShowList(fragmentView);

        // Dynamically load new pages when user scrolls down.
        mShowView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) { // Check for scroll down.
                    visibleItemCount = mShowLinearLayoutManager.getChildCount();
                    totalItemCount = mShowLinearLayoutManager.getItemCount();
                    pastVisibleItems = mShowLinearLayoutManager.findFirstVisibleItemPosition();

                    if (loading) {
                        if (totalItemCount > previousTotal) {
                            loading = false;
                            previousTotal = totalItemCount;


                            if (mSearchView) {
                                currentSearchPage++;
                            } else {
                                currentPage++;
                            }
                        }
                    }

                    int threshold = visibleThreshold;
                    if (preferences.getBoolean(SHOWS_LIST_PREFERENCE, true)) {
                        // It is a grid view, so the threshold should be bigger.
                        threshold *= preferences.getInt(GRID_SIZE_PREFERENCE, 3);
                    }

                    // When no new pages are being loaded,
                    // but the user is at the end of the list, load the new page.
                    if (!loading && (visibleItemCount + pastVisibleItems + threshold) >= totalItemCount) {
                        // Load the next page of the content in the background.
                        if (mSearchView) {
                            mSearchTask = new SearchList().execute
                                    (mListType, Integer.toString(currentSearchPage + 1), mSearchQuery);
                        } else {
                            new ShowList().execute(mListType, Integer.toString(currentPage + 1));
                        }
                        loading = true;
                    }
                }
            }
        });
    }

    /**
     * Creates the ShowBaseAdapter with the (still empty) ArrayList.
     * Also starts an AsyncTask to load the items for the empty ArrayList.
     *
     * @param query the query to start the AsyncTask with (and that will be added to the
     *              API call as search query).
     */
    public void search(String query) {
        // Create a separate adapter for the search results.
        mSearchShowArrayList = new ArrayList<>();
        mSearchShowAdapter = new ShowBaseAdapter(mSearchShowArrayList, mShowGenreList,
                preferences.getBoolean(SHOWS_LIST_PREFERENCE, false));

        // Cancel old AsyncTask if it exists.
        currentSearchPage = 1;
        if (mSearchTask != null) {
            mSearchTask.cancel(true);
        }
        mSearchTask = new SearchList().execute(mListType, "1", query);

        mSearchQuery = query;
    }

    /**
     * Sets the search variable to false and sets original adapter in the RecyclerView.
     */
    public void cancelSearch() {
        mSearchView = false;
        mShowView.setAdapter(mShowAdapter);
    }

    /**
     * Uses AsyncTask to retrieve the list with popular shows.
     */
    private class ShowList extends AsyncTask<String, Void, String> {

        private final String API_KEY = ConfigHelper.getConfigValue(
                getActivity().getApplicationContext(), "api_key");
        private boolean missingOverview;
        private String listType;
        private int page;

        protected String doInBackground(String... params) {
            listType = params[0];
            page = Integer.parseInt(params[1]);
            if (params.length > 2) {
                missingOverview = params[2].equalsIgnoreCase("true");
            }

            String line;
            StringBuilder stringBuilder = new StringBuilder();

            // Load the webpage with the popular movies/series.
            try {
                URL url;
                if (missingOverview) {
                    url = new URL("https://api.themoviedb.org/3/discover/"
                            + listType + "?" + filterParameter + "&page="
                            + page + "&api_key=" + API_KEY);
                } else {
                    url = new URL("https://api.themoviedb.org/3/discover/"
                            + listType + "?" + filterParameter + "&page="
                            + page + "&api_key=" + API_KEY + BaseActivity.getLanguageParameter(getContext()));
                }

                URLConnection urlConnection = url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(
                                    urlConnection.getInputStream()));

                    // Create one long string of the webpage.
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }

                    // Close connection and return the data from the webpage.
                    bufferedReader.close();
                    return stringBuilder.toString();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (MalformedURLException mue) {
                mue.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            // Loading the dataset failed, return null.
            return null;
        }

        protected void onPostExecute(String response) {
            if (response != null && !response.isEmpty()) {
                // Keep the user at the same position in the list.
                int position;
                try {
                    position = mShowLinearLayoutManager.findFirstVisibleItemPosition();
                } catch (NullPointerException npe) {
                    position = 0;
                }

                // If the filter has changed, remove the old items
                if (filterChanged) {
                    mShowArrayList.clear();

                    // Set the previous total back to zero.
                    previousTotal = 0;

                    // Set filterChanged back to false.
                    filterChanged = false;
                }

                // Convert the JSON data from the webpage into JSONObjects
                ArrayList<JSONObject> tempMovieArrayList = new ArrayList<>();
                try {
                    JSONObject reader = new JSONObject(response);
                    JSONArray arrayData = reader.getJSONArray("results");
                    for (int i = 0; i < arrayData.length(); i++) {
                        JSONObject websiteData = arrayData.getJSONObject(i);
                        if (missingOverview) {
                            tempMovieArrayList.add(websiteData);
                        } else {
                            mShowArrayList.add(websiteData);
                        }
                    }

                    // Some translations might be lacking and need to be filled in.
                    // Therefore, load the same list but in English.
                    // After that, iterate through the translated list
                    // and fill in any missing parts.
                    if (!Locale.getDefault().getLanguage().equals("en") &&
                            !missingOverview) {
                        new ShowList().execute(listType, Integer.toString(page), "true");
                    }

                    // If the overview is missing, add the overview from the English version.
                    if (missingOverview) {
                        for (int i = mShowArrayList.size() - tempMovieArrayList.size(); i < mShowArrayList.size(); i++) {
                            JSONObject movieObject = mShowArrayList.get(i);
                            if (movieObject.getString("overview").equals("")) {
                                movieObject.put("overview", tempMovieArrayList.
                                        get(i - (mShowArrayList.size() - tempMovieArrayList.size())).getString("overview"));
                            }
                        }
                    }

                    // Reload the adapter (with the new page)
                    // and set the user to his old position.
                    if (mShowView != null) {
                        mShowView.setAdapter(mShowAdapter);
                        scrollView.setAdapter(mShowAdapter);
                        if (page != 1) {
                            mShowView.scrollToPosition(position);

                        }

                    }
                    mShowListLoaded = true;
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        }
    }

    /**
     * Uses AsyncTask to retrieve the list with shows that fulfill the search query
     * (and are of the requested type which means that nothing will turn up if you
     * search for a series in the movies tab (and there are no movies with the same name).
     */
    private class SearchList extends AsyncTask<String, Void, String> {

        private final String API_KEY = ConfigHelper.getConfigValue(
                getActivity().getApplicationContext(), "api_key");
        private boolean missingOverview;
        private int page;

        protected String doInBackground(String... params) {
            // Extra parameter for the search query.
            String listType = params[0];
            page = Integer.parseInt(params[1]);
            String query = params[2];
            if (params.length > 3) {
                missingOverview = params[3].equalsIgnoreCase("true");
            }

            String line;
            StringBuilder stringBuilder = new StringBuilder();

            // Load the webpage with the list of movies/series.
            try {
                URL url;
                if (missingOverview) {
                    url = new URL("https://api.themoviedb.org/3/search/" +
                            listType + "?query=" + query + "&page=" + page +
                            "&api_key=" + API_KEY);
                } else {
                    url = new URL("https://api.themoviedb.org/3/search/" +
                            listType + "?&query=" + query + "&page=" + page +
                            "&api_key=" + API_KEY + getLanguageParameter(getContext()));
                }

                URLConnection urlConnection = url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(
                                    urlConnection.getInputStream()));

                    // If the user changed the search query, stop loading.
                    if (isCancelled()) {
                        return null;
                    }

                    // Create one long string of the webpage.
                    while ((line = bufferedReader.readLine()) != null) {
                        // If the user changed the search query, stop loading.
                        if (isCancelled()) {
                            // There are two because I am not sure
                            // what the best place is for this clause.
                            break;
                        }
                        stringBuilder.append(line).append("\n");
                    }

                    // Close connection and return the data from the webpage.
                    bufferedReader.close();
                    return stringBuilder.toString();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } catch (MalformedURLException mue) {
                mue.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

            // Loading the dataset failed, return null.
            return null;
        }

        protected void onPostExecute(String response) {
            // Keep the user at the same position in the list.
            int position;
            try {
                position = mShowLinearLayoutManager.findFirstVisibleItemPosition();
            } catch (NullPointerException npe) {
                position = 0;
            }

            // Clear the array list before adding new movies to it.
            if (currentSearchPage <= 0) {
                // Only if the list doesn't already contain search results.
                mSearchShowArrayList.clear();
            }

            // Convert the JSON webpage to JSONObjects
            // Add the JSONObjects to the list with movies/series.
            if (!(response == null || response.equals(""))) {
                ArrayList<JSONObject> tempSearchMovieArrayList = new ArrayList<>();
                try {
                    JSONObject reader = new JSONObject(response);
                    JSONArray arrayData = reader.getJSONArray("results");
                    for (int i = 0; i < arrayData.length(); i++) {
                        JSONObject websiteData = arrayData.getJSONObject(i);
                        if (missingOverview) {
                            tempSearchMovieArrayList.add(websiteData);
                        } else {
                            mSearchShowArrayList.add(websiteData);
                        }
                    }

                    // Some translations might be lacking and need to be filled in.
                    // Therefore, load the same list but in English.
                    // After that, iterate through the translated list
                    // and fill in any missing parts.
                    if (!Locale.getDefault().getLanguage().equals("en") && !missingOverview) {
                        mSearchTask = new SearchList().execute(mListType,
                                Integer.toString(page), mSearchQuery, "true");
                    }

                    if (missingOverview) {
                        for (int i = 0; i < mSearchShowArrayList.size(); i++) {
                            JSONObject movieObject = mSearchShowArrayList.get(i);
                            if (movieObject.getString("overview").equals("")) {
                                movieObject.put("overview", tempSearchMovieArrayList.
                                        get(i).getString("overview"));
                            }
                        }
                    }

                    // Reload the adapter (with the new page)
                    // and set the user to his old position.
                    mSearchView = true;
                    if (mShowView != null) {
                        mShowView.setAdapter(mSearchShowAdapter);
                        mShowView.scrollToPosition(2);

                    }
                    scrollView.setVisibility(View.GONE);
                    mShowView.setVisibility(View.VISIBLE);
                    scrollView.scrollToPosition(2);
                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }
        }
    }
}
