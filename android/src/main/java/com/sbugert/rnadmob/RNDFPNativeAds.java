package com.sbugert.rnadmob;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.formats.NativeAdOptions;
import com.google.android.gms.ads.formats.NativeCustomTemplateAd;
import com.google.android.gms.common.api.BooleanResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RNDFPNativeAds extends ReactContextBaseJavaModule {

    private static final String REACT_CLASS = "RNDFPNativeAds";

    private static final int ERROR_CODE_INTERNAL_ERROR = 0;
    private static final int ERROR_CODE_INVALID_REQUEST = 1;
    private static final int ERROR_CODE_NETWORK_ERROR = 2;
    private static final int ERROR_CODE_NO_FILL = 3;

    private static final String ERROR_MSG_INTERNAL_ERROR = "Something happened internally; " +
        "for instance, an invalid response was received from the ad server.";
    private static final String ERROR_MSG_INVALID_REQUEST = "The ad request was invalid; " +
        "for instance, the ad unit ID was incorrect.";
    private static final String ERROR_MSG_NETWORK_ERROR = "The ad request was unsuccessful due " +
        "to network connectivity.";
    private static final String ERROR_MSG_NO_FILL = "The ad request was successful, but no ad was " +
        "returned due to lack of ad inventory.";


    private static final String ADS_KEY = "ads";
    private static final String ERROR_KEY = "error";
    private String KADUNITID = "adUnitID";
    private String KREQUESTKEY = "requestKey";

    private static final String KEVENTADLOADED = "nativeCustomTemplateAdLoaded";
    private static final String KEVENTADFAILEDTOLOAD = "nativeCustomTemplateAdFailedToLoad";
    private static final String KEVENTADSTARTLOADING = "nativeCustomTemplateAdStartLoading";
    private static final String KEVENTALLADSFINISHED = "nativeCustomTemplateAllAdsFinished";

    private static final String EVENT_ADS_ALREADY_LOADING = "E_ADS_ALREADY_LOADING";
    private static final String MSG_ADS_ALREADY_LOADING =
        "Ads with request key %s are already loading.";
    private static final String EVENT_ADS_INVALID_PARAMETERS = "E_ADS_INVALID_PARAMETERS";
    private static final String MSG_ADS_INVALID_PARAMETERS =
        "Please provide non empty requestKey and adUnitIDs";
    private static final String EVENT_ADS_NO_TEMPLATE_ID = "E_ADS_NO_TEMPLATE_ID";
    private static final String MSG_ADS_NO_TEMPLATE_ID =
        "Please provide non empty Template ID";
    public static final String EVENT_ADS_REQUEST_FAILED = "E_ADS_REQUEST_FAILED";

    private ReadableArray mTestDevices = Arguments.createArray();
    private ReadableArray mTemplateIDs = Arguments.createArray();
    private ReadableMap mCustomTargetings = Arguments.createMap();
    private HashMap<String, HashMap<String, NativeCustomTemplateAd>> mNativeCustomTemplateAds = new HashMap<>();
    private HashMap<String, WritableMap> mConvertedAds = new HashMap<>();
    private HashMap<String, Callback> mRequestAdsResolves = new HashMap<>();
    private HashMap<String, Callback> mRequestAdsRejects = new HashMap<>();
    private HashMap<String, HashMap<String, Boolean>> mAdLoaders = new HashMap<>();
    private HashMap<String, ReadableArray> mAdUnits = new HashMap<>();

    public RNDFPNativeAds(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }


    @ReactMethod
    public void setTemplateIDs(ReadableArray templateIDs) {
        Log.v(REACT_CLASS, "!templateIDs size: " + templateIDs.size());
        Log.v(REACT_CLASS, "!templateID: " + templateIDs.getString(0));
        mTemplateIDs = templateIDs;
    }

    @ReactMethod
    public void setCustomTargeting(ReadableMap customTargeting) {
        mCustomTargetings = customTargeting;

    }

    @ReactMethod
    public void setTestDevices(ReadableArray testDevices) {
        mTestDevices = testDevices;

    }

    @ReactMethod
    public void performClickOnAsset(String asset, String requestKey, String unitId) {
        try {
            int adUnitKey = Integer.parseInt(unitId);
            mNativeCustomTemplateAds.get(requestKey).get(adUnitKey).performClick(asset);
        } catch (Exception e) {
//          Could not perform click fot the add asset
        }
    }

    @ReactMethod
    public void isNativeAdLoading(String requestKey, String adUnitId, Callback response) {
        boolean loading = false;
        HashMap<String, Boolean> loaders = mAdLoaders.get(requestKey);
        if (loaders != null) {
            boolean isLoading = loaders.get(adUnitId);
                loading = isLoading;
        }
        response.invoke(loading);
    }

    @ReactMethod
    public void requestAds(final String requestKey, ReadableArray dfpAdUnitIds, Callback resolve,
                           Callback reject) {

        if (TextUtils.isEmpty(requestKey) || ((dfpAdUnitIds == null || dfpAdUnitIds.size() == 0))) {
            reject.invoke(EVENT_ADS_INVALID_PARAMETERS,
                MSG_ADS_INVALID_PARAMETERS,
                null);
            Log.v(REACT_CLASS, "MSG_ADS_INVALID_PARAMETERS");
            return;
        }
        if (mTemplateIDs.size() <= 0) {
            reject.invoke(EVENT_ADS_NO_TEMPLATE_ID,
                MSG_ADS_NO_TEMPLATE_ID,
                null);
            Log.v(REACT_CLASS, "MSG_ADS_NO_TEMPLATE_ID");
            return;
        }

        if (!hasRequestLoading(requestKey)) {

            mNativeCustomTemplateAds.put(requestKey, new HashMap<String, NativeCustomTemplateAd>());
            mConvertedAds.put(requestKey, Arguments.createMap());
            mAdLoaders.put(requestKey, new HashMap<String, Boolean>());
            mAdUnits.put(requestKey, dfpAdUnitIds);
            mRequestAdsResolves.put(requestKey, resolve);
            mRequestAdsRejects.put(requestKey, reject);

            Log.v(REACT_CLASS, "!hasRequestLoading: " + requestKey);

            for (int i = 0; i < dfpAdUnitIds.size(); i++) {
                final String unitId = dfpAdUnitIds.getString(i);
                Log.v(REACT_CLASS, "unitId: " + unitId);
                for (int j = 0; j < mTemplateIDs.size(); j++) {
                    String templateID = mTemplateIDs.getString(j);
                    Log.v(REACT_CLASS, "!templateID: " + templateID);

                    final AdLoader adLoader = getAdLoader(unitId, templateID, requestKey);
                    mAdLoaders.get(requestKey).put(unitId, false);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            PublisherAdRequest.Builder publisherAdRequestBuilder =
                                new PublisherAdRequest.Builder();
                            if (mTestDevices != null) {
                                for (int i = 0; i < mTestDevices.size(); i++) {
                                    String testDevice = mTestDevices.getString(i);
                                    publisherAdRequestBuilder.addTestDevice(testDevice);
                                }
                            }
                            if (mCustomTargetings != null) {
                                ReadableMapKeySetIterator it = mCustomTargetings.keySetIterator();
                                while (it.hasNextKey()) {
                                    String key = it.nextKey();
                                    Log.v(REACT_CLASS, "setting custom targeting: " + key);
                                    publisherAdRequestBuilder.addCustomTargeting(key, mCustomTargetings.getString(key));
                                }
                            }

                            adLoader.loadAd(publisherAdRequestBuilder.build());
                            mAdLoaders.get(requestKey).put(unitId, true);
                            Log.v(REACT_CLASS, "hasRequestLoading (should be true): "+ hasRequestLoading(requestKey));

                            sendOnAdsStartingLoading(requestKey, unitId);
                        }
                    });
                }
            }
        } else {
            reject.invoke(EVENT_ADS_ALREADY_LOADING,
                String.format(Locale.getDefault(),
                    MSG_ADS_ALREADY_LOADING,
                    requestKey), null);
            Log.v(REACT_CLASS, "MSG_ADS_ALREADY_LOADING");
        }
    }

    private AdLoader getAdLoader(
        final String dfpAdUnitId, String customTemplateAd, final String requestKey) {

        final AdLoader adLoader = new AdLoader.Builder(getReactApplicationContext(), dfpAdUnitId)
            .forCustomTemplateAd(customTemplateAd, new NativeCustomTemplateAd
                .OnCustomTemplateAdLoadedListener() {
                @Override
                public void onCustomTemplateAdLoaded(NativeCustomTemplateAd ad) {

                    Log.v(REACT_CLASS, "ad loaded for " + requestKey + " with unitId: " + dfpAdUnitId);

                    mNativeCustomTemplateAds.get(requestKey).put(dfpAdUnitId, ad);
                    mConvertedAds.put(requestKey, getAdAssetsMap(ad));
                    sendOnAdLoadedEvent(requestKey, dfpAdUnitId, getAdAssetsMap(ad));
                    ad.recordImpression();
                    if (didAllRequestsFinish(requestKey)) {
                        Callback resolves = mRequestAdsResolves.get(requestKey);
                        if (resolves != null) {
                            mRequestAdsResolves.get(requestKey).invoke(getAdAssetsMap(ad));
                        }
                        sendOnAllAdsLoadedEvent(requestKey, getAdAssetsMap(ad));
                        cleanUp(requestKey);
                    }
                    mAdLoaders.get(requestKey).put(dfpAdUnitId, false);
                }

            }, new NativeCustomTemplateAd.OnCustomClickListener() {
                @Override
                public void onCustomClick(NativeCustomTemplateAd ad, String s) {

                }
            }).withAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(int errorCode) {
                    sendOnAdFailedEvent(requestKey, dfpAdUnitId, errorCode);
                    if (didAllAdRequestsFailed(requestKey)) {
                        sendOnAllAdsFailedEvent(requestKey, errorCode);
                        mRequestAdsRejects
                            .get(requestKey)
                            .invoke(EVENT_ADS_REQUEST_FAILED, getAdLoaderStringError(errorCode));
                        cleanUp(requestKey);
                    }
                    mAdLoaders.get(requestKey).put(dfpAdUnitId, false);
                }

                @Override
                public void onAdLoaded() {
                }
            })
            .withNativeAdOptions(new NativeAdOptions.Builder()
                // Methods in the NativeAdOptions.Builder class can be
                // used here to specify individual options settings.
                .build())
            .build();

        return adLoader;
    }


    private boolean hasRequestLoading(String requestKey) {
        HashMap<String, Boolean> loadersMap = mAdLoaders.get(requestKey);
        if (loadersMap != null) {
            for (boolean isLoading : loadersMap.values()) {
                if (isLoading) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean didAllRequestsFinish(String requestKey) {
        ReadableArray adUnits = mAdUnits.get(requestKey);
        if (adUnits != null) {
            return mNativeCustomTemplateAds.get(requestKey).keySet().size() == adUnits.size();
        } else {
            return true;
        }
    }

    private boolean didAllAdRequestsFailed(String requestKey) {
        ReadableArray adUnits = mAdUnits.get(requestKey);
        if (adUnits != null) {
            return mNativeCustomTemplateAds.get(requestKey).values().size() == adUnits.size();
        } else {
            return true;
        }
    }

    private void cleanUp(String requestKey) {
        mConvertedAds.remove(requestKey);
        mAdLoaders.remove(requestKey);
        mAdUnits.remove(requestKey);
        mRequestAdsResolves.remove(requestKey);
        mRequestAdsRejects.remove(requestKey);
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        Log.v(REACT_CLASS, "##Sending Event: " + eventName + " with requestKey: "+params.getString(KREQUESTKEY));
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule
                .RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    private void sendOnAdsStartingLoading(String requestKey, String unitId) {
        sendEvent(KEVENTADSTARTLOADING, getLoadingEventParams(requestKey, unitId));
    }

    private void sendOnAdLoadedEvent(String requestKey, String adUnitId, WritableMap ad) {
        WritableMap params = Arguments.createMap();
        params.putString(KREQUESTKEY, requestKey);
        params.putString(KADUNITID, adUnitId);
        params.putMap(ADS_KEY, ad);
        sendEvent(KEVENTADLOADED, params);
    }

    private void sendOnAdFailedEvent(String requestKey, String dfpAdUnitId, int errorCode) {
        sendEvent(KEVENTADFAILEDTOLOAD, getErrorEventParams(dfpAdUnitId, errorCode,
            requestKey));
    }

    private void sendOnAllAdsLoadedEvent(String requestKey, WritableMap ads) {
        WritableMap params = Arguments.createMap();
        params.putString(KREQUESTKEY, requestKey);
        params.putMap(ADS_KEY, ads);
        sendEvent(KEVENTADLOADED, params);
    }

    private void sendOnAllAdsFailedEvent(String requestKey, int errorCode) {
        WritableMap allAdsFailedMap = Arguments.createMap();
        allAdsFailedMap.putString(KREQUESTKEY, requestKey);
        allAdsFailedMap.putString(ERROR_KEY, getAdLoaderStringError(errorCode));
        sendEvent(KEVENTALLADSFINISHED, allAdsFailedMap);

    }

    @NonNull
    private WritableMap getAdAssetsMap(NativeCustomTemplateAd ad) {
        WritableMap adAssets = Arguments.createMap();
        for (String name : ad.getAvailableAssetNames()) {
            Log.v(REACT_CLASS, "Asset name: " + name);
            CharSequence text = ad.getText(name);
            if (!TextUtils.isEmpty(text)) {
                adAssets.putString(name, text.toString());
                Log.v(REACT_CLASS, "Asset type text: " + text);
            } else if (ad.getImage(name) != null) {
                String imageUrl = ad.getImage(name).getUri().toString();
                if (!TextUtils.isEmpty(imageUrl)) {
                    adAssets.putString(name, imageUrl);
                    Log.v(REACT_CLASS, "Asset type image: " + imageUrl);
                }
            }
        }
        Log.v(REACT_CLASS, "adAssets image: " + adAssets.getString("MainImage"));
        return adAssets;
    }

    private WritableMap getLoadingEventParams(String requestKey, String adUnitId) {
        WritableMap loadingMap = Arguments.createMap();
        loadingMap.putString(KREQUESTKEY, requestKey);
        loadingMap.putString(KADUNITID, adUnitId);
        return loadingMap;
    }

    private WritableMap getErrorEventParams(String adUnitId, int errorCode, String requestKey) {
        WritableMap errorMap = Arguments.createMap();
        String errMsg = getAdLoaderStringError(errorCode);
        errorMap.putString(ERROR_KEY, errMsg);
        errorMap.putString(KADUNITID, adUnitId);
        errorMap.putString(KREQUESTKEY, requestKey);
        return errorMap;
    }

    @NonNull
    private String getAdLoaderStringError(int errorCode) {
        String errMsg;
        switch (errorCode) {
            case ERROR_CODE_INTERNAL_ERROR:
                errMsg = ERROR_MSG_INTERNAL_ERROR;
                break;
            case ERROR_CODE_INVALID_REQUEST:
                errMsg = ERROR_MSG_INVALID_REQUEST;
                break;
            case ERROR_CODE_NETWORK_ERROR:
                errMsg = ERROR_MSG_NETWORK_ERROR;
                break;
            case ERROR_CODE_NO_FILL:
                errMsg = ERROR_MSG_NO_FILL;
                break;
            default:
                errMsg = "Unknown error";
        }
        return errMsg;
    }

    @javax.annotation.Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("simulatorId", AdRequest.DEVICE_ID_EMULATOR);
        return constants;
    }
}
