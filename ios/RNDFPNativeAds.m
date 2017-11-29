#import "RNDFPNativeAds.h"

#if __has_include(<React/RCTUtils.h>)
#import <React/RCTUtils.h>
#else
#import "RCTUtils.h"
#endif

static NSString *const kEventAdLoaded = @"nativeCustomTemplateAdLoaded";
static NSString *const kEventAdFailedToLoad = @"nativeCustomTemplateAdFailedToLoad";
static NSString *const kEventAdStartLoading = @"nativeCustomTemplateAdStartLoading";
static NSString *const kEventAllAdsFinished = @"nativeCustomTemplateAllAdsFinished";

static NSString *const kAdUnitID = @"adUnitID";

@implementation RNDFPNativeAds {
    NSMutableDictionary *_nativeCustomTemplateAds;
    NSMutableDictionary *_convertedAds;
    NSMutableDictionary<NSString *, GADAdLoader *> *_adLoaders;
    NSArray<NSString *> *_adUnitIDs;
    NSArray *_templateIDs;
    NSArray *_testDevices;
    NSDictionary *_customTargeting;
    RCTPromiseResolveBlock _requestAdsResolve;
    RCTPromiseRejectBlock _requestAdsReject;
    BOOL hasListeners;
}

- (instancetype)init
{
    if ((self = [super init])) {
        _nativeCustomTemplateAds = @{}.mutableCopy;
        _convertedAds = @{}.mutableCopy;
        _adLoaders = @{}.mutableCopy;
    }
    return self;
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

RCT_EXPORT_MODULE();

- (NSArray<NSString *> *)supportedEvents
{
    return @[
             kEventAdLoaded,
             kEventAdFailedToLoad,
             kEventAdStartLoading,
             kEventAllAdsFinished
             ];
}

#pragma mark exported methods

RCT_EXPORT_METHOD(setAdUnitIDs:(NSArray *)adUnitIDs)
{
    _adUnitIDs = adUnitIDs;
}

RCT_EXPORT_METHOD(setTestDevices:(NSArray *)testDevices)
{
    _testDevices = testDevices;
}

RCT_EXPORT_METHOD(setTemplateIDs:(NSArray *)templateIDs)
{
    _templateIDs = templateIDs;
}

RCT_EXPORT_METHOD(setCustomTargeting:(NSDictionary *)customTargeting)
{
    _customTargeting = customTargeting;
}

RCT_EXPORT_METHOD(performClickOnAsset:(NSString *)assetKey unitID:(NSString *)unitID)
{
    [_nativeCustomTemplateAds[unitID] performClickOnAssetWithKey:assetKey];
}

RCT_EXPORT_METHOD(isNativeAdLoading:(NSString *)unitID with:(RCTResponseSenderBlock)callback)
{
    callback(@[@(_adLoaders[unitID].isLoading)]);
}

RCT_EXPORT_METHOD(requestAds:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if ([self allRequestsFinished]) {
        [_nativeCustomTemplateAds removeAllObjects];
        [_convertedAds removeAllObjects];
        [_adLoaders removeAllObjects];
        
        _requestAdsResolve = resolve;
        _requestAdsReject = reject;
        
        GADMultipleAdsAdLoaderOptions *multipleAdsOptions = [[GADMultipleAdsAdLoaderOptions alloc] init];
        multipleAdsOptions.numberOfAds = 1;
        
        GADNativeAdImageAdLoaderOptions *imageAdLoaderOptions = [[GADNativeAdImageAdLoaderOptions alloc] init];
        imageAdLoaderOptions.disableImageLoading = true;
        
        for (NSString *adUnitID in _adUnitIDs) {
            GADAdLoader *adLoader = [[GADAdLoader alloc] initWithAdUnitID:adUnitID
                                                       rootViewController:[[UIViewController alloc] init]
                                                                  adTypes:@[kGADAdLoaderAdTypeNativeCustomTemplate]
                                                                  options:@[multipleAdsOptions, imageAdLoaderOptions]];
            [_adLoaders setObject:adLoader forKey:adUnitID];
            adLoader.delegate = self;
            
            DFPRequest *request = [DFPRequest request];
            request.testDevices = _testDevices;
            request.customTargeting = _customTargeting;
            [adLoader loadRequest:request];
            
            if (hasListeners) {
                [self sendEventWithName:kEventAdStartLoading body:@{kAdUnitID : adUnitID}];
            }
            
        }
    } else {
        reject(@"E_ADS_ALREADY_LOADING", @"Ads are already loading.", nil);
    }
    
}

- (NSDictionary<NSString *,id> *)constantsToExport
{
    return @{
             @"simulatorId": kGADSimulatorID
             };
}

- (void)startObserving
{
    hasListeners = YES;
}

- (void)stopObserving
{
    hasListeners = NO;
}

#pragma mark GADNativeCustomTemplateAdLoaderDelegate implementation

- (void)adLoader:(GADAdLoader *)adLoader
didReceiveNativeCustomTemplateAd:(GADNativeCustomTemplateAd *)nativeCustomTemplateAd {
    [_nativeCustomTemplateAds setObject:nativeCustomTemplateAd?:[NSNull null] forKey:adLoader.adUnitID];
    
    NSMutableDictionary *ad = @{}.mutableCopy;
    
    for (NSString *key in nativeCustomTemplateAd.availableAssetKeys) {
        if ([nativeCustomTemplateAd stringForKey:key]) {
            [ad setValue:[nativeCustomTemplateAd stringForKey:key] forKey:key];
        }
        else if ([nativeCustomTemplateAd imageForKey:key]) {
            [ad setValue:[[nativeCustomTemplateAd imageForKey:key] imageURL].absoluteString forKey:key];
        }
    }
    [_convertedAds setObject:ad forKey:adLoader.adUnitID];
    
    if (hasListeners) {
        [self sendEventWithName:kEventAdLoaded body:@{kAdUnitID: adLoader.adUnitID, @"ad":ad}];
    }
    
    [nativeCustomTemplateAd recordImpression];
    
    if (_nativeCustomTemplateAds.allKeys.count == _adUnitIDs.count) {
        _requestAdsResolve(_convertedAds);
        if (hasListeners) {
            [self sendEventWithName:kEventAllAdsFinished body:@{@"ads":_convertedAds}];
        }
    }
}

- (NSArray *)nativeCustomTemplateIDsForAdLoader:(GADAdLoader *)adLoader {
    return _templateIDs;
}


#pragma mark GADAdLoaderDelegate implementation

- (void)adLoader:(GADAdLoader *)adLoader didFailToReceiveAdWithError:(GADRequestError *)error {
    
    NSDictionary *jsError = RCTJSErrorFromCodeMessageAndNSError(@"E_AD_REQUEST_FAILED", error.localizedDescription, error);
    if (hasListeners) {
        [self sendEventWithName:kEventAdFailedToLoad body:@{kAdUnitID: adLoader.adUnitID, @"error":jsError}];
    }
    // TODO: can set in both dictionaries the error object
    [_nativeCustomTemplateAds setObject:[NSNull null] forKey:adLoader.adUnitID];
    [_convertedAds setObject:@{} forKey:adLoader.adUnitID];
    
    if ([self allAdsFailed]) {
        _requestAdsReject(@"E_ADS_REQUEST_FAILED", error.localizedDescription, error);
        if (hasListeners) {
            [self sendEventWithName:kEventAllAdsFinished body:jsError];
        }
    }
}

#pragma mark Helper

- (BOOL)allRequestsFinished {
    for (GADAdLoader *loader in _adLoaders.allValues) {
        if (loader.isLoading) {
            return false;
        }
    }
    return true;
}

- (BOOL)allAdsFailed {
    if (_nativeCustomTemplateAds.allValues.count != _adUnitIDs.count) {
        return false;
    }
    
    for (GADNativeCustomTemplateAd *ad in _nativeCustomTemplateAds.allValues) {
        if (![ad isEqual:[NSNull null]]) {
            return false;
        }
    }
    return true;
}

@end
