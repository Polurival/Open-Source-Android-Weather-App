package com.ardovic.weatherappprototype;

import android.annotation.SuppressLint;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.ardovic.weatherappprototype.model.IJ;
import com.ardovic.weatherappprototype.model.retrofit.Response;
import com.ardovic.weatherappprototype.util.ImageHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;

import static com.ardovic.weatherappprototype.network.WeatherApi.API_KEY;

public class MainActivity extends BaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    @BindView(R.id.actv_city_country_name)
    AutoCompleteTextView actvCityCountryName;
    @BindView(R.id.iv_condition_icon)
    ImageView ivConditionIcon;
    @BindView(R.id.tv_city_country_name)
    TextView tvCityCountryName;
    @BindView(R.id.tv_condition_description)
    TextView tvConditionDescription;
    @BindView(R.id.tv_temperature)
    TextView tvTemperature;
    @BindView(R.id.tv_pressure)
    TextView tvPressure;
    @BindView(R.id.tv_humidity)
    TextView tvHumidity;
    @BindView(R.id.tv_wind_speed_degrees)
    TextView tvWindSpeedDegrees;

    public final static String CITY_ID = "city_id";
    public final static String CITY_COUNTRY_NAME = "city_country_name";
    public final static String TABLE_1 = "my_table";
    public final static String ID = "_id";
    public final static String[] mProjection = {ID, CITY_COUNTRY_NAME};
    private static final String TAG = "MainActivity";
    private static final String CITY_ARGS = "city_weather_arg";
    //FetchThreadData<Integer> mFetchThreadData;
    //private Handler mHandler = new Handler();
    public String cityCountryName;

    public SimpleCursorAdapter mAdapter;

    @Override
    protected void onStart() {
        super.onStart();
        if (!cityCountryName.equals("")) {
            requestWeather();
            //mFetchThreadData.queueResponce(0, cityCountryName);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        cityCountryName = sharedPreferences.getString(CITY_COUNTRY_NAME, "");
        actvCityCountryName.setText(cityCountryName);
        //mFetchThreadData = new FetchThreadData<>(mHandler);
        //mFetchThreadData.start();
        //mFetchThreadData.getLooper();
        //initServerResponse();


        //JSONConverter.getInstance().makeNewShortJSON(this, null, null, null);

        if (database.isOpen()) {
            checkDatabaseState();
        } else {
            database = databaseHelper.getReadableDatabase();
            checkDatabaseState();
        }


        // Create a SimpleCursorAdapter for the State Name field.
        mAdapter = new SimpleCursorAdapter(this,
                R.layout.dropdown_text,
                null,
                new String[]{CITY_COUNTRY_NAME},
                new int[]{R.id.text}, 0);
        mAdapter.setFilterQueryProvider(new FilterQueryProvider() {
            @Override
            public Cursor runQuery(CharSequence constraint) {
                if (constraint != null) {
                    if (constraint.length() >= 3 && !TextUtils.isEmpty(constraint)) {
                        Bundle bundle = new Bundle();
                        String query = charArrayUpperCaser(constraint);
                        bundle.putString(CITY_ARGS, query);
                        getLoaderManager().restartLoader(0, bundle, MainActivity.this).forceLoad();
                    }
                }
                return null;
            }
        });

        // Set an OnItemClickListener, to update dependent fields when
        // a choice is made in the AutoCompleteTextView.
        actvCityCountryName.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> listView, View view,
                                    int position, long id) {
                // Get the cursor, positioned to the corresponding row in the
                // result set
                Cursor cursor = (Cursor) listView.getItemAtPosition(position);

                // Get the state's capital from this row in the database.
                cityCountryName = cursor.getString(cursor.getColumnIndexOrThrow(CITY_COUNTRY_NAME));

                // Update the parent class's TextView
                actvCityCountryName.setText(cityCountryName);

                requestWeather();
                //mFetchThreadData.queueResponce(position, cityCountryName);

//                JSONWeatherTask task = new JSONWeatherTask();
//                task.execute(new String[]{cityCountryName});

                hideKeyboard();
            }
        });

        actvCityCountryName.setAdapter(mAdapter);


    }

    @Override
    protected void onStop() {
        super.onStop();
        sharedPreferences.edit().putString(CITY_COUNTRY_NAME, cityCountryName).apply();

    }

    public void createLocalCityDB() {

        int i = 0;

        ArrayList<ContentValues> cvList = new ArrayList<>();
        ContentValues cv;
        Gson gson = new GsonBuilder().create();
        IJ ij;

        try (JsonReader reader = new JsonReader(new InputStreamReader(getAssets().open("ijCityList.json")))) {

            // Read file in stream mode
            reader.beginArray();

            while (reader.hasNext()) {
                // Read data into object model
                ij = gson.fromJson(reader, IJ.class);

                cv = new ContentValues();
                i++;
                cv.put(CITY_ID, ij.i);
                cv.put(CITY_COUNTRY_NAME, ij.j);
                cvList.add(cv);

                if (cvList.size() % 10000 == 0) {
                    System.out.println("Adding 10K to db, current item: " + i);
                    database.beginTransaction();
                    for (ContentValues value : cvList) {
                        database.insert(TABLE_1, null, value);
                    }
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    cvList = new ArrayList<>();
                }

            }

            System.out.println("Adding last part to db, current item: " + i);
            database.beginTransaction();
            for (ContentValues value : cvList) {
                database.insert(TABLE_1, null, value);
            }
            database.setTransactionSuccessful();
            database.endTransaction();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public android.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String s = args.getString(CITY_ARGS);
        WeatherCursorLoader loader = null;
        if (s != null && !TextUtils.isEmpty(s)) {
            loader = new WeatherCursorLoader(this, database, s);
        }
        return loader;
    }

    @Override
    public void onLoadFinished(android.content.Loader<Cursor> loader, Cursor cursor) {
        Log.d(TAG, "onLoadFinished: " + Arrays.toString(cursor.getColumnNames()));
        mAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(android.content.Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }
        database.close();
        //mFetchThreadData.clearQueue();
    }


    private static class WeatherCursorLoader extends CursorLoader {

        private SQLiteDatabase mSQLiteDatabase;
        private String mQuery;

        WeatherCursorLoader(Context context, SQLiteDatabase cDatabase, String s) {
            super(context);
            mSQLiteDatabase = cDatabase;
            mQuery = s + "%";
            Log.d(TAG, "WeatherCursorLoader: " + mQuery);
        }


        @Override
        public Cursor loadInBackground() {
            return mSQLiteDatabase.query(TABLE_1, mProjection,
                    CITY_COUNTRY_NAME + " like ?", new String[]{mQuery},
                    null, null, null, "50");
        }
    }

    private String charArrayUpperCaser(CharSequence sequence) {
        Character charAt = sequence.charAt(0);
        String s = sequence.toString().replace(sequence.charAt(0), charAt.toString().toUpperCase().charAt(0));
        Log.d(TAG, "charArrayUpperCaser: " + s);
        return s;
    }

    private void checkDatabaseState() {
        if (databaseHelper.isTableExists(database, TABLE_1)) {
            long count = DatabaseUtils.queryNumEntries(database, TABLE_1);
            System.out.println(count);
            Log.d(TAG, "checkDatabaseState: start checking database");
            if (count != 168820) {
                database.execSQL("DROP TABLE IF EXISTS " + TABLE_1);
                databaseHelper.createTable1(database);
                createLocalCityDB();
                Log.d(TAG, "checkDatabaseState: database is broken");
            }
        } else {
            databaseHelper.createTable1(database);
            createLocalCityDB();
            Log.d(TAG, "checkDatabaseState: first start database");
        }
    }

    private void requestWeather() {
        weatherApi.getWeather(cityCountryName, API_KEY).enqueue(new Callback<Response>() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onResponse(@NonNull Call<Response> call, @NonNull retrofit2.Response<Response> response) {
                Response model = response.body();

                if (model != null) {
                    Log.d(TAG, model.toString());

                    tvCityCountryName.setText(model.getName() + ", " + model.getSys().getCountry());
                    tvConditionDescription.setText(model.getWeather().get(0).getMain() + " (" +
                            (model.getWeather().get(0).getDescription() + ")"));
                    tvTemperature.setText(", " + Math.round((model.getMain().getTemp() - 273.15)) + (char) 0x00B0 + "C");
                    tvHumidity.setText(model.getMain().getHumidity() + "%");
                    tvPressure.setText(model.getMain().getPressure() + " hPa");
                    tvWindSpeedDegrees.setText(model.getWind().getSpeed() + " mps, " + model.getWind().getDeg() + (char) 0x00B0);

                    requestWeatherIcon(model);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Response> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Check internet connection or try again later", Toast.LENGTH_SHORT)
                        .show();
                Log.d(TAG, "Weather request error: " + t.getMessage());
            }
        });
    }

    /**
     * BitmapFactory.decodeStream method needs background thread
     */
    private void requestWeatherIcon(Response model) {
        weatherApi.getIcon(model.getWeather().get(0).getIcon()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, final @NonNull retrofit2.Response<ResponseBody> response) {
                if (response.body() != null) {

                    new Thread() {
                        @Override
                        public void run() {
                            final Bitmap bitmap = BitmapFactory.decodeStream(response.body().byteStream());
                            if (bitmap != null) {
                                final Bitmap resizedBitmap = ImageHelper.getResizedBitmap(bitmap, 100, 100);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ivConditionIcon.setImageBitmap(resizedBitmap);
                                    }
                                });
                            }
                        }
                    }.start();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Check internet connection or try again later", Toast.LENGTH_SHORT)
                        .show();
                Log.d(TAG, "Weather icon request error: " + t.getMessage());
            }
        });
    }
}



