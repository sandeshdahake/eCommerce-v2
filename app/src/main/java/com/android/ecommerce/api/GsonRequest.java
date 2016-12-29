package com.android.ecommerce.api;

import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.util.Base64;

import com.android.ecommerce.entities.Banner;
import com.android.ecommerce.entities.BannerProducts;
import com.android.ecommerce.entities.BannersResponse;
import com.android.ecommerce.entities.drawerMenu.DrawerItemCategory;
import com.android.ecommerce.entities.drawerMenu.DrawerResponse;
import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.JsonSyntaxException;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.ecommerce.BuildConfig;
import com.android.ecommerce.CONST;
import com.android.ecommerce.MyApplication;
import com.android.ecommerce.utils.Utils;
//import com.android.ecommerce.ux.dialogs.LoginDialogFragment;
//import com.android.ecommerce.ux.dialogs.LoginExpiredDialogFragment;
import timber.log.Timber;

/**
 * Basic request using Gson's reflection.
 * Usable for authorized and unauthorized requests.
 * Allowing for an optional {@link String} to be passed in as part of the request body.
 *
 * @param <T> object which is serializable from request.
 */
public class GsonRequest<T> extends Request<T> {
    /**
     * Default charset for JSON request.
     */
    private static final String PROTOCOL_CHARSET = "utf-8";

    /**
     * Class of object which is serializable from request.
     */
    private final Class<T> clazz;

    /**
     * Array of Class which is serializable from request.
     */
    private final String requestType;
    /**
     * User token for authorized requests.
     */
    private final String accessToken;
    private final String requestBody;
    private final Response.Listener<T> successListener;
    private String requestUrl;
    private int requestStatusCode;
    private FragmentManager fragmentManager;

    /**
     * Create a new authorized request.
     *
     * @param method          The HTTP method to use
     * @param requestUrl      URL to fetch the JSON from
     * @param requestBody     A {@link String} to post with the request. Null is allowed and
     *                        indicates no parameters will be posted along with request.
     * @param clazz           Relevant class object, for Gson's reflection
     * @param successListener Listener to retrieve successful response
     * @param errorListener   Error listener, or null to ignore errors
     * @param fragmentManager Manager to create re-login dialog on HTTP status 403. Null is allowed.
     * @param accessToken     Token identifying user used for user specific requests.
     */
    public GsonRequest(int method, String requestUrl, String requestBody, Class<T> clazz, Response.Listener<T> successListener,
                       Response.ErrorListener errorListener, FragmentManager fragmentManager, String accessToken,String requestType) {
        super(method, requestUrl, errorListener);
        this.clazz = clazz;
        this.requestUrl = requestUrl;
        this.requestBody = requestBody;
        this.successListener = successListener;
        this.fragmentManager = fragmentManager;
        this.accessToken = accessToken;
        this.requestType = requestType;
    }

    /**
     * Create a new unauthorized request.
     *
     * @param method          The HTTP method to use
     * @param requestUrl      URL to fetch the JSON from
     * @param requestBody     A {@link String} to post with the request. Null is allowed and
     *                        indicates no parameters will be posted along with request.
     * @param clazz           Relevant class object, for Gson's reflection
     * @param successListener Listener to retrieve successful response
     * @param errorListener   Error listener, or null to ignore errors
     */
    public GsonRequest(int method, String requestUrl, String requestBody, Class<T> clazz, Response.Listener<T> successListener,
                       Response.ErrorListener errorListener, String requestType) {
        this(method, requestUrl, requestBody, clazz, successListener, errorListener, null, null,requestType);
    }

    public GsonRequest(int method, String requestUrl, String requestBody, Class<T> clazz, Response.Listener<T> successListener,
                       Response.ErrorListener errorListener) {
        this(method, requestUrl, requestBody, clazz, successListener, errorListener, null, null,null);
    }


    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        Map<String, String> headers = new HashMap<>();
        headers.put("Client-Version", MyApplication.APP_VERSION);
        headers.put("Device-Token", MyApplication.ANDROID_ID);

        // Determine if request should be authorized.
        if (accessToken != null && !accessToken.isEmpty()) {
            String credentials = accessToken + ":";
            String encodedCredentials = Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
            headers.put("Authorization", "Basic " + encodedCredentials);
        }
        return headers;
    }

    @Override
    public byte[] getBody() {
        try {
            return requestBody == null ? null : requestBody.getBytes(PROTOCOL_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", requestBody, PROTOCOL_CHARSET);
            return null;
        }
    }

    @Override
    protected void deliverResponse(@NonNull T response) {
        successListener.onResponse(response);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            requestStatusCode = response.statusCode;
            if (BuildConfig.DEBUG)
                Timber.d("%s URL: %s. ResponseCode: %d", this.getClass().getSimpleName(), requestUrl, response.statusCode);

            // Parse response and return obtained object
            String json = new String(response.data, PROTOCOL_CHARSET);
            T result = null;
            if(requestType == null){
                result = Utils.getGsonParser().fromJson(json, clazz);
            }else if (CONST.DRAWER_REQUESTS_TAG.equals(requestType)){
                DrawerItemCategory[] listOfCategory = Utils.getGsonParser().fromJson(json, DrawerItemCategory[].class);
                DrawerResponse drawerResponse = new DrawerResponse();
                drawerResponse.setNavigation(Arrays.asList(listOfCategory));
                result =(T)drawerResponse;
            }else if (CONST.BANNER_REQUESTS_TAG.equals(requestType)){
                Banner[] listOfBanners = Utils.getGsonParser().fromJson(json, Banner[].class);
                BannersResponse bannersResponse = new BannersResponse();
                bannersResponse.setRecords(Arrays.asList(listOfBanners));
                result =(T)bannersResponse;
            }else if (CONST.BANNER_REQUESTS_FEATURED_TAG.equals(requestType)){
                BannerProducts[] listOfBannerProducts = Utils.getGsonParser().fromJson(json, BannerProducts[].class);
                BannersResponse bannersResponse = new BannersResponse();
                bannersResponse.setFeaturedRecords(Arrays.asList(listOfBannerProducts));
                result =(T)bannersResponse;
            }else if (CONST.BANNER_REQUESTS_POPULAR_TAG.equals(requestType)){
                BannerProducts[] listOfBannerProducts = Utils.getGsonParser().fromJson(json, BannerProducts[].class);
                BannersResponse bannersResponse = new BannersResponse();
                bannersResponse.setPopularRecords(Arrays.asList(listOfBannerProducts));
                result =(T)bannersResponse;
            }

            if (result == null) return Response.error(new ParseError(new NullPointerException()));
            else return Response.success(result, HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (JsonSyntaxException e) {
            return Response.error(new ParseError(e));
        } catch (Exception e) {
            return Response.error(new ParseError(e));
        }
    }

    @Override
    protected VolleyError parseNetworkError(VolleyError volleyError) {
        if (volleyError.networkResponse != null) {
            // Save request status code
            requestStatusCode = volleyError.networkResponse.statusCode;
            if (BuildConfig.DEBUG)
                Timber.e("%s URL: %s. ERROR: %s", this.getClass().getSimpleName(), requestUrl, new String(volleyError.networkResponse.data));

            // If AccessToken expired. Logout user and redirect to home page.
/*
            if (getStatusCode() == HttpURLConnection.HTTP_FORBIDDEN && fragmentManager != null) {
                LoginDialogFragment.logoutUser();
                DialogFragment loginExpiredDialogFragment = new LoginExpiredDialogFragment();
                loginExpiredDialogFragment.show(fragmentManager, LoginExpiredDialogFragment.class.getSimpleName());
            }
*/
        } else {
            requestStatusCode = CONST.MissingStatusCode;
        }
        return super.parseNetworkError(volleyError);
    }

    /**
     * Method returns result statusCode of invoked request.
     *
     * @return HTTP standard code.
     */
    public int getStatusCode() {
        return requestStatusCode;
    }
}