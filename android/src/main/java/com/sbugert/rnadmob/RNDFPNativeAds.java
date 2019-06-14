package com.sbugert.rnadmob;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.formats.NativeAdOptions;
import com.google.android.gms.ads.formats.NativeCustomTemplateAd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name=RNDFPNativeAds.REACT_CLASS)
public class RNDFPNativeAds extends ReactContextBaseJavaModule {

    public static final String REACT_CLASS = "RNDFPNativeAds";

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


    private static final String AD_KEY = "ad";
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
    private HashMap<String, ReadableArray> mTemplateIDs = new HashMap<>();
    private HashMap<String, ReadableMap> mCustomTargetings = new HashMap<>();
    private HashMap<String, HashMap<String, Boolean>> mProcessingNativeCustomTemplateAds = new HashMap<>();
    private HashMap<String, HashMap<String, Boolean>> mFailedNativeCustomTemplateAds = new HashMap<>();
    private HashMap<String, HashMap<String, NativeCustomTemplateAd>> mConvertedNativeCustomTemplateAds = new HashMap<>();
    private HashMap<String, Promise> mRequestAdsPromises = new HashMap<>();
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
    public void setTemplateIDs(ReadableArray templateIDs, String requestKey) {
        mTemplateIDs.put(requestKey, templateIDs);
    }

    @ReactMethod
    public void setCustomTargeting(ReadableMap customTargeting, String requestKey) {
        mCustomTargetings.put(requestKey, customTargeting);
    }

    @ReactMethod
    public void setTestDevices(ReadableArray testDevices) {
        mTestDevices = testDevices;
    }

    @ReactMethod
    public void performClickOnAsset(final String asset, String requestKey, String unitId) {
        try {
            HashMap<String, NativeCustomTemplateAd> ads = mConvertedNativeCustomTemplateAds.get(requestKey);
            if (ads != null) {
                final NativeCustomTemplateAd adToClick = ads.get(unitId);
                if (adToClick != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                adToClick.performClick(asset);
                            } catch (Exception e) {
                                Log.v(REACT_CLASS, e.getMessage());
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
//          Could not perform click fot the add asset
            Log.w(REACT_CLASS, e);
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
    public void requestAds(final String requestKey, ReadableArray dfpAdUnitIds, Promise requestKeyPromise) {
        if (TextUtils.isEmpty(requestKey) || ((dfpAdUnitIds == null || dfpAdUnitIds.size() == 0))) {
            requestKeyPromise.reject(EVENT_ADS_INVALID_PARAMETERS);
            return;
        }
        if (mTemplateIDs.size() <= 0) {
            requestKeyPromise.reject(EVENT_ADS_NO_TEMPLATE_ID,
                MSG_ADS_NO_TEMPLATE_ID);
            return;
        }
        if (!hasRequestLoading(requestKey)) {
            mProcessingNativeCustomTemplateAds.put(requestKey, new HashMap<String, Boolean>());
            mFailedNativeCustomTemplateAds.put(requestKey, new HashMap<String, Boolean>());
            mConvertedNativeCustomTemplateAds.put(requestKey, new HashMap<String, NativeCustomTemplateAd>());
            mAdLoaders.put(requestKey, new HashMap<String, Boolean>());
            mAdUnits.put(requestKey, dfpAdUnitIds);
            mRequestAdsPromises.put(requestKey, requestKeyPromise);
            for (int i = 0; i < dfpAdUnitIds.size(); i++) {
                final String unitId = dfpAdUnitIds.getString(i);
                ReadableArray templateIds = mTemplateIDs.get(requestKey);
                String templateId = "";
                if (templateIds != null) {
                    String tempId = templateIds.getString(0);
                    if (tempId != null) {
                        templateId = tempId;
                    }
                }
                mProcessingNativeCustomTemplateAds.get(requestKey).put(unitId, true);
                final AdLoader adLoader = getAdLoader(unitId, templateId, requestKey);
                mAdLoaders.get(requestKey).put(unitId, false);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        PublisherAdRequest.Builder publisherAdRequestBuilder =
                            new PublisherAdRequest.Builder();
                        if (mTestDevices != null) {
                            for (int i = 0; i < mTestDevices.size(); i++) {
                                String testDevice = mTestDevices.getString(i);
                                if (testDevice == "SIMULATOR") {
                                    testDevice = AdRequest.DEVICE_ID_EMULATOR;
                                }
                                publisherAdRequestBuilder.addTestDevice(testDevice);
                            }
                        }
                        if (mCustomTargetings != null && mCustomTargetings.get(requestKey) != null) {
                            ReadableMap customTargetings = mCustomTargetings.get(requestKey);
                            ReadableMapKeySetIterator it = customTargetings.keySetIterator();
                            while (it.hasNextKey()) {
                                String key = it.nextKey();
                                if (customTargetings.getType(key) == ReadableType.Array) {
                                    try {
                                        ArrayList<Object> al = customTargetings.getArray(key).toArrayList();
                                        List<String> valList = new ArrayList<>();
                                        for (int l = 0; l < al.size(); l++) {
                                            valList.add(l, (String) al.get(l));
                                        }
                                        publisherAdRequestBuilder.addCustomTargeting(key, valList);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else if (customTargetings.getType(key) == ReadableType.String) {
                                    publisherAdRequestBuilder.addCustomTargeting(key, customTargetings.getString(key));
                                }
                            }
                        }
                        adLoader.loadAd(publisherAdRequestBuilder.build());

                        HashMap<String, Boolean> adLoaderMap = mAdLoaders.get(requestKey);
                        if (adLoaderMap != null) {
                            adLoaderMap.put(unitId, true);
                        }
                        sendOnAdsStartingLoading(requestKey, unitId);
                    }
                });
                // }
                //}
            }
        } else {
            requestKeyPromise.reject(EVENT_ADS_ALREADY_LOADING,
                String.format(Locale.getDefault(),
                    MSG_ADS_ALREADY_LOADING,
                    requestKey));
        }
    }

    @ReactMethod
    public void cleanUp(ReadableArray requestKeys) {
        for (int i = 0; i < requestKeys.size(); i++) {
            String requestKey = requestKeys.getString(i);
            mConvertedNativeCustomTemplateAds.remove(requestKey);
        }
    }

    private AdLoader getAdLoader(
        final String dfpAdUnitId, final String customTemplateID, final String requestKey) {
        final AdLoader adLoader = new AdLoader.Builder(getReactApplicationContext(), dfpAdUnitId)
            .forCustomTemplateAd(customTemplateID, new NativeCustomTemplateAd
                .OnCustomTemplateAdLoadedListener() {
                @Override
                public void onCustomTemplateAdLoaded(NativeCustomTemplateAd ad) {
                    sendOnAdLoadedEvent(requestKey, dfpAdUnitId, getAdAssetsMap(ad));
                    ad.recordImpression();
                    HashMap<String, NativeCustomTemplateAd> convertedMap = mConvertedNativeCustomTemplateAds.get(requestKey);
                    if (convertedMap != null) {
                        convertedMap.put(dfpAdUnitId, ad);
                    }
                    HashMap<String, Boolean> processingMap = mProcessingNativeCustomTemplateAds.get(requestKey);
                    if (processingMap != null) {
                        processingMap.put(dfpAdUnitId, false);
                    }
                    HashMap<String, Boolean> loadersMap = mAdLoaders.get(requestKey);
                    if (loadersMap != null) {
                        loadersMap.put(dfpAdUnitId, false);
                    }
                    if (didAllRequestsFinish(requestKey)) {
                        Promise requestKeyPromise = mRequestAdsPromises.get(requestKey);
                        if (requestKeyPromise != null) {
                            requestKeyPromise.resolve(getWritableMapFromNativeAdsMap(requestKey));
                        }
                        sendOnAllAdsLoadedEvent(requestKey);
                        cleanUp(requestKey);
                    }
                }

            }, null).withAdListener(new AdListener() {
                @Override
                public void onAdFailedToLoad(int errorCode) {
                    sendOnAdFailedEvent(requestKey, dfpAdUnitId, errorCode);
                    HashMap<String, Boolean> hashAds = mProcessingNativeCustomTemplateAds.get(requestKey);
                    if (hashAds != null) {
                        mProcessingNativeCustomTemplateAds.get(requestKey).put(dfpAdUnitId, false);
                    }
                    HashMap<String, Boolean> hashFailedAds = mFailedNativeCustomTemplateAds.get(requestKey);
                    if (hashFailedAds != null) {
                        hashFailedAds.put(dfpAdUnitId, true);
                    }
                    HashMap<String, Boolean> hashLoaders = mAdLoaders.get(requestKey);
                    if (hashLoaders != null) {
                        hashLoaders.put(dfpAdUnitId, false);
                    }
                    if (didAllAdRequestsFailed(requestKey)) {
                        sendOnAllAdsFailedEvent(requestKey, errorCode);
                        Promise requestKeyPromise = mRequestAdsPromises.get(requestKey);
                        if (requestKeyPromise != null) {
                            requestKeyPromise.reject(EVENT_ADS_REQUEST_FAILED, getAdLoaderStringError(errorCode));
                        }
                        cleanUp(requestKey);
                    }
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
            if (adUnits != null) {
                HashMap<String, Boolean> processingAds = mProcessingNativeCustomTemplateAds.get(requestKey);
                if (processingAds != null) {
                    Iterator it = processingAds.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry entry = (Map.Entry) it.next();
                        if ((boolean) entry.getValue()) {
                            return false;
                        }
                    }
                }
            }

        }
        return true;
    }

    private boolean didAllAdRequestsFailed(String requestKey) {
        ReadableArray adUnits = mAdUnits.get(requestKey);
        if (adUnits != null) {
            HashMap<String, Boolean> failedAds = mFailedNativeCustomTemplateAds.get(requestKey);
            if (failedAds != null) {
                return failedAds.size() == adUnits.size();
            }
        }
        return true;
    }

    private void cleanUp(String requestKey) {
        mProcessingNativeCustomTemplateAds.remove(requestKey);
        mFailedNativeCustomTemplateAds.remove(requestKey);
        mAdLoaders.remove(requestKey);
        mAdUnits.remove(requestKey);
        mRequestAdsPromises.remove(requestKey);
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
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
        params.putMap(AD_KEY, ad);
        sendEvent(KEVENTADLOADED, params);
    }

    private void sendOnAdFailedEvent(String requestKey, String dfpAdUnitId, int errorCode) {
        sendEvent(KEVENTADFAILEDTOLOAD, getErrorEventParams(dfpAdUnitId, errorCode,
            requestKey));
    }

    private void sendOnAllAdsLoadedEvent(String requestKey) {
        WritableMap params = Arguments.createMap();
        params.putString(KREQUESTKEY, requestKey);
        params.putMap(ADS_KEY, getWritableMapFromNativeAdsMap(requestKey));
        sendEvent(KEVENTALLADSFINISHED, params);
    }

    private void sendOnAllAdsFailedEvent(String requestKey, int errorCode) {
        WritableMap allAdsFailedMap = Arguments.createMap();
        allAdsFailedMap.putString(KREQUESTKEY, requestKey);
        allAdsFailedMap.putString(ERROR_KEY, getAdLoaderStringError(errorCode));
        sendEvent(KEVENTALLADSFINISHED, allAdsFailedMap);

    }

    private void performClickOnUIThread(final NativeCustomTemplateAd ad, final String assetName) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    ad.performClick(assetName);
                } catch (Exception e) {
                    Log.v(REACT_CLASS, e.getMessage());
                }
            }
        });

    }

    private WritableMap getWritableMapFromNativeAdsMap(String requestKey) {
        WritableMap nativeAds = Arguments.createMap();
        HashMap<String, NativeCustomTemplateAd> convertedAds =
            mConvertedNativeCustomTemplateAds.get(requestKey);
        if (convertedAds != null) {
            Iterator it = convertedAds.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                nativeAds
                    .putMap((String) entry.getKey(),
                        getAdAssetsMap((NativeCustomTemplateAd) entry.getValue()));
            }
        }
        return nativeAds;
    }

    @NonNull
    private WritableMap getAdAssetsMap(NativeCustomTemplateAd ad) {
        WritableMap adAssets = Arguments.createMap();
        for (String name : ad.getAvailableAssetNames()) {
            CharSequence text = ad.getText(name);
            if (!TextUtils.isEmpty(text)) {
                adAssets.putString(name, text.toString());
            } else if (ad.getImage(name) != null) {
                String imageUrl = ad.getImage(name).getUri().toString();
                if (!TextUtils.isEmpty(imageUrl)) {
                    adAssets.putString(name, imageUrl);
                }
            }
        }
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

    // @javax.annotation.Nullable
    // @Override
    // public Map<String, Object> getConstants() {
    //     final Map<String, Object> constants = new HashMap<>();
    //     constants.put("simulatorId", AdRequest.DEVICE_ID_EMULATOR);
    //     return constants;
    // }
}
