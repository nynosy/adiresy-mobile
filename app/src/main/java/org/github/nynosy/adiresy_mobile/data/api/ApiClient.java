package org.github.nynosy.adiresy_mobile.data.api;

import android.content.Context;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.github.nynosy.adiresy_mobile.BuildConfig;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static final String BASE_URL      = "https://adiresy.mg/";
    private static final int    CACHE_MB      = 5;
    private static final int    CONNECT_TO_S  = 10;
    private static final int    READ_TO_S     = 15;

    private static volatile AdiresyApi instance;

    public static AdiresyApi get(Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = build(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private static AdiresyApi build(Context context) {
        File cacheDir = new File(context.getCacheDir(), "okhttp");
        Cache cache = new Cache(cacheDir, (long) CACHE_MB * 1024 * 1024);

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(CONNECT_TO_S, TimeUnit.SECONDS)
                .readTimeout(READ_TO_S, TimeUnit.SECONDS)
                .addInterceptor(new ApiKeyInterceptor(BuildConfig.API_KEY));

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            clientBuilder.addInterceptor(logging);
        }

        OkHttpClient client = clientBuilder.build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AdiresyApi.class);
    }
}
