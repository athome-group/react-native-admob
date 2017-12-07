package com.sbugert.rnadmob;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.formats.NativeAdOptions;
import com.google.android.gms.ads.formats.NativeCustomTemplateAd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RNDFPNativeAdsModule extends ReactContextBaseJavaModule {

    public static final String REACT_CLASS = "RNDFPNativeAdsModule";

    public static final int ERROR_CODE_INTERNAL_ERROR = 0;
    public static final int ERROR_CODE_INVALID_REQUEST = 1;
    public static final int ERROR_CODE_NETWORK_ERROR = 2;
    public static final int ERROR_CODE_NO_FILL = 3;

    public static final String ERROR_MSG_INTERNAL_ERROR = "Something happened internally; " +
            "for instance, an invalid response was received from the ad server.";
    public static final String ERROR_MSG_INVALID_REQUEST = "The ad request was invalid; " +
            "for instance, the ad unit ID was incorrect.";
    public static final String ERROR_MSG_NETWORK_ERROR = "The ad request was unsuccessful due " +
            "to network connectivity.";
    public static final String ERROR_MSG_NO_FILL = "The ad request was successful, but no ad was " +
            "returned due to lack of ad inventory.";


    public static final String KEVENTADLOADED = "nativeCustomTemplateAdLoaded";
    public static final String KEVENTADFAILEDTOLOAD = "nativeCustomTemplateAdFailedToLoad";
    public static final String KEVENTADSTARTLOADING = "nativeCustomTemplateAdStartLoading";
    public static final String KEVENTALLADSFINISHED = "nativeCustomTemplateAllAdsFinished";
    
    
    public static final String EVENT_ADS_ALREADY_LOADING = "E_ADS_ALREADY_LOADING";
    public static final String MSG_ADS_ALREADY_LOADING =
            "Ads with request key %s are already loading.";
    public static final String EVENT_ADS_INVALID_PARAMETERS = "E_ADS_INVALID_PARAMETERS";
    public static final String MSG_ADS_INVALID_PARAMETERS =
            "Please provide non empty requestKey and adUnitIDs";

    private String KADUNITID = "adUnitID";
    private String KREQUESTKEY = "requestKey";


    private ReactApplicationContext mReactContext = null;
    private List<String> mTestDevices = new ArrayList<>();
    private List<String> mTemplateIDs = new ArrayList<>();
    private HashMap<String, String> mCustomTargetings = new HashMap<>();
    private HashMap<String, Callback> mRequestAdsRejects = new HashMap<>();
    private HashMap<String, Callback> mRequestAdsResolves = new HashMap<>();
    private HashMap<String, NativeCustomTemplateAd> mConvertedAds = new HashMap<>();
    private HashMap<String, HashMap<String, AdLoader>> mAdLoaders = new HashMap<>();
    private HashMap<String, List<String>> mAdUnits = new HashMap<>();
    private HashMap<String, NativeCustomTemplateAd> mNativeCustomTemplateAds = new HashMap<>();

    public RNDFPNativeAdsModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }


    @ReactMethod
    public void setTemplateIDs(String[] templateIDs) {
        mTemplateIDs = Arrays.asList(templateIDs);
    }

    @ReactMethod
    public void setCustomTargeting(HashMap<String, String> customTargeting) {
        mCustomTargetings = customTargeting;

    }

    @ReactMethod
    public void setTestDevices(String[] testDevices) {
        mTestDevices = Arrays.asList(testDevices);

    }

    @ReactMethod
    public void requestAds(String requestKey, String[] dfpAdUnitIds, Callback resolve,
                           Callback reject) {

        if (TextUtils.isEmpty(requestKey) || ((dfpAdUnitIds == null || dfpAdUnitIds.length == 0))) {
            reject.invoke(EVENT_ADS_INVALID_PARAMETERS,
                    MSG_ADS_INVALID_PARAMETERS,
                    null);
            return;
        }
        if (!hasRequestLoading(requestKey)) {

            List<String> dfpUnitIds = Arrays.asList(dfpAdUnitIds);

            mNativeCustomTemplateAds.put(requestKey, null);
            mConvertedAds.put(requestKey, null);
            mAdLoaders.put(requestKey, new HashMap<String, AdLoader>());
            mAdUnits.put(requestKey, dfpUnitIds);
            mRequestAdsResolves.put(requestKey, resolve);
            mRequestAdsRejects.put(requestKey, reject);

            for (String unitId : dfpUnitIds) {
                for (String templateID : mTemplateIDs) {

                    final AdLoader adLoader = getAdLoader(unitId, templateID, requestKey);
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            PublisherAdRequest.Builder publisherAdRequestBuilder =
                                    new PublisherAdRequest.Builder();
                            if (mTestDevices != null) {

                                for (String testDevice : mTestDevices) {
                                    publisherAdRequestBuilder.addTestDevice(testDevice);
                                }
                            }
                            adLoader.loadAd(publisherAdRequestBuilder.build());
                        }
                    });

                }
            }
        } else {
            reject.invoke(EVENT_ADS_ALREADY_LOADING,
                    String.format(Locale.getDefault(),
                            MSG_ADS_ALREADY_LOADING,
                            requestKey), null);
        }
    }

    @ReactMethod
    public void performClickOnAsset(String asset, String requestKey, String unitId) {

    }

    private AdLoader getAdLoader(
            final String dfpAdUnitId, String customTemplateAd, final String requestKey) {

        final AdLoader adLoader = new AdLoader.Builder(getReactApplicationContext(), dfpAdUnitId)
                .forCustomTemplateAd(customTemplateAd, new NativeCustomTemplateAd
                        .OnCustomTemplateAdLoadedListener() {
                    @Override
                    public void onCustomTemplateAdLoaded(NativeCustomTemplateAd ad) {
                        mNativeCustomTemplateAds.put(requestKey, ad);
                        sendEvent(KEVENTADLOADED, getAdEventParams(ad, requestKey));
                    }

                }, new NativeCustomTemplateAd.OnCustomClickListener() {
                    @Override
                    public void onCustomClick(NativeCustomTemplateAd ad, String s) {

                    }
                }).withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(int errorCode) {
                        sendEvent(requestKey, getErrorEventParams(dfpAdUnitId, errorCode));
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
        HashMap<String, AdLoader> loadersMap = mAdLoaders.get(requestKey);
        for (AdLoader adLoader : loadersMap.values()) {
            if (adLoader.isLoading()) {
                return true;
            }
        }
        return false;
    }

    private void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule
                        .RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private WritableMap getAdEventParams(NativeCustomTemplateAd ad, String requestKey) {
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
        WritableMap params = Arguments.createMap();
        WritableMap adHashMap = Arguments.createMap();
        adHashMap.putMap(KADUNITID, adAssets);
        params.putMap(requestKey, adHashMap);
        return params;
    }

    private WritableMap getErrorEventParams(String adUnitId, int errorCode) {
        WritableMap errorMap = Arguments.createMap();
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
        errorMap.putString(KEVENTADFAILEDTOLOAD, errMsg);
        WritableMap errorEventMap = Arguments.createMap();
        errorEventMap.putString(KADUNITID, adUnitId);
        errorEventMap.putMap("error", errorMap);
        return errorEventMap;
    }

    @javax.annotation.Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("simulatorId", AdRequest.DEVICE_ID_EMULATOR);
        return constants;
    }
}
