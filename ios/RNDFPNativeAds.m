#import "RNDFPNativeAds.h"

#if __has_include(<React/RCTUtils.h>)
#import <React/RCTUtils.h>
#else
#import "RCTUtils.h"
#endif

@interface GADAdLoader(requestKey)
@property(nonatomic, copy) NSString *requestKey;
@end

#import <objc/runtime.h>

static char RequestKey;

@implementation GADAdLoader(requestKey)
- (NSString *)requestKey
{
    return objc_getAssociatedObject(self, &RequestKey);
}

- (void)setRequestKey:(NSString *)requestKey
{
    objc_setAssociatedObject(self, &RequestKey, requestKey, OBJC_ASSOCIATION_COPY);
}

@end


static NSString *const kEventAdLoaded = @"nativeCustomTemplateAdLoaded";
static NSString *const kEventAdFailedToLoad = @"nativeCustomTemplateAdFailedToLoad";
static NSString *const kEventAdStartLoading = @"nativeCustomTemplateAdStartLoading";
static NSString *const kEventAllAdsFinished = @"nativeCustomTemplateAllAdsFinished";

static NSString *const kAdUnitID = @"adUnitID";
static NSString *const kRequestKey = @"requestKey";

@implementation RNDFPNativeAds {
    NSMutableDictionary<NSString *, NSMutableDictionary *> *_nativeCustomTemplateAds;
    NSMutableDictionary<NSString *, NSMutableDictionary *> *_convertedAds;
    NSMutableDictionary<NSString *, NSMutableDictionary<NSString *, GADAdLoader *> *> *_adLoaders;
    NSMutableDictionary<NSString *, NSArray<NSString *> *> *_adUnitIDs;
    NSMutableDictionary<NSString *, NSArray *> *_templateIDs;
    NSArray *_testDevices;
    NSMutableDictionary<NSString *, NSDictionary *> *_customTargetings;
    NSMutableDictionary<NSString *, RCTPromiseResolveBlock> *_requestAdsResolves;
    NSMutableDictionary<NSString *, RCTPromiseRejectBlock> *_requestAdsRejects;
    BOOL hasListeners;
}

- (instancetype)init
{
    if ((self = [super init])) {
        _nativeCustomTemplateAds = @{}.mutableCopy;
        _convertedAds = @{}.mutableCopy;
        _adLoaders = @{}.mutableCopy;
        _adUnitIDs = @{}.mutableCopy;
        _requestAdsResolves = @{}.mutableCopy;
        _requestAdsRejects = @{}.mutableCopy;
        _templateIDs = @{}.mutableCopy;
        _customTargetings = @{}.mutableCopy;
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

RCT_EXPORT_METHOD(setTestDevices:(NSArray *)testDevices)
{
    _testDevices = testDevices;
}

RCT_EXPORT_METHOD(setTemplateIDs:(NSArray *)templateIDs forRequestKey:(NSString *)requestKey)
{
    _templateIDs[requestKey] = templateIDs;
}

RCT_EXPORT_METHOD(setCustomTargeting:(NSDictionary *)customTargeting forRequestKey:(NSString *)requestKey)
{
    _customTargetings[requestKey] = customTargeting;
}

RCT_EXPORT_METHOD(performClickOnAsset:(NSString *)assetKey requestKey:(NSString *)key unitID:(NSString *)unitID)
{
    if (_nativeCustomTemplateAds[key]) {
        GADNativeCustomTemplateAd *ad = _nativeCustomTemplateAds[key][unitID];
        if (ad) {
            [ad performClickOnAssetWithKey:assetKey];
        }
    }
}

RCT_EXPORT_METHOD(cleanUp:(NSArray<NSString *> *)requestKeys)
{
    [_nativeCustomTemplateAds removeObjectsForKeys:requestKeys];
}

RCT_EXPORT_METHOD(isNativeAdLoading:(NSString *)key unitID:(NSString *)unitID callback:(RCTResponseSenderBlock)callback)
{
    callback(@[@(_adLoaders[key][unitID].isLoading)]);
}

// requestKey is a unique key used to track request in order to support multiple request groups
// each call of this method creates a new request group of GADAdLoaders
RCT_EXPORT_METHOD(requestAds:(NSString *)requestKey forAdUnitIDs:(NSArray *)adUnitIDs resolve:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    if (requestKey.length == 0 || adUnitIDs.count == 0) {
        reject(@"E_ADS_INVALID_PARAMETERS", @"Please provide non empty requestKey and adUnitIDs", nil);
        return;
    }
    
    if (![self requestLoading:requestKey]) {
        [_convertedAds setObject:@{}.mutableCopy forKey:requestKey];
        [_adLoaders setObject:@{}.mutableCopy forKey:requestKey];
        [_adUnitIDs setObject:adUnitIDs forKey:requestKey];
        
        [_requestAdsResolves setObject:resolve forKey:requestKey];
        [_requestAdsRejects setObject:reject forKey:requestKey];
        
        // TODO: expose options to be configurable
        GADMultipleAdsAdLoaderOptions *multipleAdsOptions = [[GADMultipleAdsAdLoaderOptions alloc] init];
        multipleAdsOptions.numberOfAds = 1;
        
        GADNativeAdImageAdLoaderOptions *imageAdLoaderOptions = [[GADNativeAdImageAdLoaderOptions alloc] init];
        imageAdLoaderOptions.disableImageLoading = true;
        
        for (NSString *adUnitID in adUnitIDs) {
            GADAdLoader *adLoader = [[GADAdLoader alloc] initWithAdUnitID:adUnitID
                                                       rootViewController:[[UIViewController alloc] init]
                                                                  adTypes:@[kGADAdLoaderAdTypeNativeCustomTemplate]
                                                                  options:@[multipleAdsOptions, imageAdLoaderOptions]];
            adLoader.delegate = self;
            adLoader.requestKey = requestKey;
            [_adLoaders[requestKey] setObject:adLoader forKey:adUnitID];
            
            DFPRequest *request = [DFPRequest request];
            request.testDevices = _testDevices;
            request.customTargeting = _customTargetings[requestKey];
            [adLoader loadRequest:request];
            
            if (hasListeners) {
                [self sendEventWithName:kEventAdStartLoading body:@{kRequestKey:requestKey, kAdUnitID: adUnitID}];
            }
        }
    } else {
        reject(@"E_ADS_ALREADY_LOADING", [NSString stringWithFormat: @"Ads with request key %@ are already loading.", requestKey], nil);
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
    NSString *requestKey = adLoader.requestKey;
    
    if (nativeCustomTemplateAd) {
        if (!_nativeCustomTemplateAds[requestKey]) {
            [_nativeCustomTemplateAds setObject:@{}.mutableCopy forKey:requestKey];
        }
        [_nativeCustomTemplateAds[requestKey] setObject:nativeCustomTemplateAd forKey:adLoader.adUnitID];
    }
    
    NSMutableDictionary *ad = @{}.mutableCopy;
    
    for (NSString *key in nativeCustomTemplateAd.availableAssetKeys) {
        if ([nativeCustomTemplateAd stringForKey:key]) {
            [ad setValue:[nativeCustomTemplateAd stringForKey:key] forKey:key];
        }
        else if ([nativeCustomTemplateAd imageForKey:key]) {
            [ad setValue:[[nativeCustomTemplateAd imageForKey:key] imageURL].absoluteString forKey:key];
        }
    }
    [_convertedAds[requestKey] setObject:ad forKey:adLoader.adUnitID];
    
    if (hasListeners) {
        [self sendEventWithName:kEventAdLoaded body:@{kRequestKey:requestKey, kAdUnitID: adLoader.adUnitID, @"ad":ad}];
    }
    
    [nativeCustomTemplateAd recordImpression];
    
    if ([self allAdsFinished:requestKey]) {
        if (hasListeners) {
            [self sendEventWithName:kEventAllAdsFinished body:@{kRequestKey:requestKey, @"ads":_convertedAds[requestKey]}];
        }
        
        _requestAdsResolves[requestKey](_convertedAds[requestKey]);
        [self internalCleanUp:requestKey];
    }
}

- (NSArray *)nativeCustomTemplateIDsForAdLoader:(GADAdLoader *)adLoader {
    return _templateIDs[adLoader.requestKey];
}


#pragma mark GADAdLoaderDelegate implementation

- (void)adLoader:(GADAdLoader *)adLoader didFailToReceiveAdWithError:(GADRequestError *)error {
    NSString *requestKey = adLoader.requestKey;
    
    NSDictionary *jsError = RCTJSErrorFromCodeMessageAndNSError(@"E_AD_REQUEST_FAILED", error.localizedDescription, error);
    if (hasListeners) {
        [self sendEventWithName:kEventAdFailedToLoad body:@{kRequestKey:requestKey, kAdUnitID: adLoader.adUnitID, @"error":jsError}];
    }
    // TODO: can set in dictionary the error object
    [_convertedAds[requestKey] setObject:@{} forKey:adLoader.adUnitID];
    
    if ([self allAdsFailed:requestKey]) {
        if (hasListeners) {
            [self sendEventWithName:kEventAllAdsFinished body:@{kRequestKey:requestKey, @"error":jsError}];
        }
        
        _requestAdsRejects[requestKey](@"E_ADS_REQUEST_FAILED", error.localizedDescription, error);
        [self internalCleanUp:requestKey];
    }
}

#pragma mark Helper

- (void)internalCleanUp:(NSString *)requestKey {
    // __nativeCustomTemplateAds not cleaned since further actions may be performed e.g. performClickOnAssetWithKey
    [_convertedAds removeObjectForKey:requestKey];
    [_adLoaders removeObjectForKey:requestKey];
    [_adUnitIDs removeObjectForKey:requestKey];
    [_requestAdsResolves removeObjectForKey:requestKey];
    [_requestAdsRejects removeObjectForKey:requestKey];
    [_customTargetings removeObjectForKey:requestKey];
    [_templateIDs removeObjectForKey:requestKey];
}

- (BOOL)requestLoading:(NSString *)requestKey {
    for (GADAdLoader *loader in _adLoaders[requestKey].allValues) {
        if (loader.isLoading) {
            return true;
        }
    }
    return false;
}

- (BOOL)allAdsFinished:(NSString *)requestKey {
    return _convertedAds[requestKey].allKeys.count == _adUnitIDs[requestKey].count;
}

- (BOOL)allAdsFailed:(NSString *)requestKey {
    if (_convertedAds[requestKey].allValues.count != _adUnitIDs[requestKey].count) {
        return false;
    }
    
    for (NSDictionary *ad in _convertedAds[requestKey].allValues) {
        if (ad.count != 0) {
            return false;
        }
    }
    return true;
}

@end
