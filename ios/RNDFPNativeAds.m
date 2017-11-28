#import "RNDFPNativeAds.h"

#if __has_include(<React/RCTUtils.h>)
#import <React/RCTUtils.h>
#else
#import "RCTUtils.h"
#endif

static NSString *const kEventAdLoaded = @"nativeCustomTemplateAdLoaded";
static NSString *const kEventAdFailedToLoad = @"nativeCustomTemplateAdFailedToLoad";
static NSString *const kEventAdStartLoading = @"nativeCustomTemplateAdStartLoading";

@implementation RNDFPNativeAds {
    GADNativeCustomTemplateAd  *_nativeCustomTemplateAd;
    GADAdLoader *_adLoader;
    NSString *_adUnitID;
    NSArray *_templateIDs;
    NSArray *_testDevices;
    NSDictionary *_customTargeting;
    RCTPromiseResolveBlock _requestAdResolve;
    RCTPromiseRejectBlock _requestAdReject;
    BOOL hasListeners;
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
             kEventAdStartLoading
             ];
}

#pragma mark exported methods

RCT_EXPORT_METHOD(setAdUnitID:(NSString *)adUnitID)
{
    _adUnitID = adUnitID;
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

RCT_EXPORT_METHOD(performClickOnAsset:(NSString *)assetKey)
{
    [_nativeCustomTemplateAd performClickOnAssetWithKey:assetKey];
}

RCT_EXPORT_METHOD(isNativeAdLoading:(RCTResponseSenderBlock)callback)
{
    callback(@[@(_adLoader.isLoading)]);
}

RCT_EXPORT_METHOD(requestAd:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    _requestAdResolve = resolve;
    _requestAdReject = reject;
    
    GADMultipleAdsAdLoaderOptions *multipleAdsOptions = [[GADMultipleAdsAdLoaderOptions alloc] init];
    multipleAdsOptions.numberOfAds = 1;
    
    GADNativeAdImageAdLoaderOptions *imageAdLoaderOptions = [[GADNativeAdImageAdLoaderOptions alloc] init];
    imageAdLoaderOptions.disableImageLoading = true;
    
    _adLoader = [[GADAdLoader alloc] initWithAdUnitID:_adUnitID
                                   rootViewController:[[UIViewController alloc] init]
                                              adTypes:@[kGADAdLoaderAdTypeNativeCustomTemplate]
                                              options:@[multipleAdsOptions, imageAdLoaderOptions]];
    _adLoader.delegate = self;
    
    DFPRequest *request = [DFPRequest request];
    request.testDevices = _testDevices;
    request.customTargeting = _customTargeting;
    [_adLoader loadRequest:request];
    
    if (hasListeners) {
        [self sendEventWithName:kEventAdStartLoading body:nil];
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
    _nativeCustomTemplateAd = nativeCustomTemplateAd;
    
    NSMutableDictionary *ad = @{}.mutableCopy;
    
    for (NSString *key in nativeCustomTemplateAd.availableAssetKeys) {
        if ([nativeCustomTemplateAd stringForKey:key]) {
            [ad setValue:[nativeCustomTemplateAd stringForKey:key] forKey:key];
        }
        else if ([nativeCustomTemplateAd imageForKey:key]) {
            [ad setValue:[[nativeCustomTemplateAd imageForKey:key] imageURL].absoluteString forKey:key];
        }
    }
    
    if (hasListeners) {
        [self sendEventWithName:kEventAdLoaded body:ad];
    }
    _requestAdResolve(ad);
    [_nativeCustomTemplateAd recordImpression];
}

- (NSArray *)nativeCustomTemplateIDsForAdLoader:(GADAdLoader *)adLoader {
    return _templateIDs;
}


#pragma mark GADAdLoaderDelegate implementation

- (void)adLoader:(GADAdLoader *)adLoader didFailToReceiveAdWithError:(GADRequestError *)error {
    if (hasListeners) {
        NSDictionary *jsError = RCTJSErrorFromCodeMessageAndNSError(@"E_AD_REQUEST_FAILED", error.localizedDescription, error);
        [self sendEventWithName:kEventAdFailedToLoad body:jsError];
    }
    _requestAdReject(@"E_AD_REQUEST_FAILED", error.localizedDescription, error);
}

@end
