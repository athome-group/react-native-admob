// import AdMobBanner from "./RNAdMobBanner";
// import AdMobInterstitial from "./RNAdMobInterstitial";
// import AdMobRewarded from "./RNAdMobRewarded";
// import DFPNativeAds from "./RNDFPNativeAds";
// import PublisherBanner from "./RNPublisherBanner";

// export {
//   AdMobBanner,
//   AdMobInterstitial,
//   PublisherBanner,
//   AdMobRewarded,
//   DFPNativeAds
// };

/* eslint-disable global-require */
module.exports = {
  get AdMobBanner() {
    return require('./RNAdMobBanner').default;
  },
  get AdMobInterstitial() {
    return require('./RNAdMobInterstitial').default;
  },
  get PublisherBanner() {
    return require('./RNPublisherBanner').default;
  },
  get AdMobRewarded() {
    return require('./RNAdMobRewarded').default;
  },
  get DFPNativeAds() {
    return require('./RNDFPNativeAds').default;
  }
};
