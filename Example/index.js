import {
  AdMobBanner,
  AdMobInterstitial,
  AdMobRewarded,
  DFPNativeAds,
  PublisherBanner
} from "react-native-admob";
import {
  AppRegistry,
  Button,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableHighlight,
  View
} from "react-native";
import React, { Component } from "react";

const BannerExample = ({ style, title, children, ...props }) => (
  <View {...props} style={[styles.example, style]}>
    <Text style={styles.title}>{title}</Text>
    <View>{children}</View>
  </View>
);

const bannerWidths = [200, 250, 320];

export default class Example extends Component {
  constructor() {
    super();
    this.state = {
      fluidSizeIndex: 0
    };
  }

  componentDidMount() {
    AdMobRewarded.setTestDevices([AdMobRewarded.simulatorId]);
    AdMobRewarded.setAdUnitID("ca-app-pub-3940256099942544/5224354917");

    AdMobRewarded.addEventListener("rewarded", reward =>
      console.log("AdMobRewarded => rewarded", reward)
    );
    AdMobRewarded.addEventListener("adLoaded", () =>
      console.log("AdMobRewarded => adLoaded")
    );
    AdMobRewarded.addEventListener("adFailedToLoad", error =>
      console.warn(error)
    );
    AdMobRewarded.addEventListener("adOpened", () =>
      console.log("AdMobRewarded => adOpened")
    );
    AdMobRewarded.addEventListener("videoStarted", () =>
      console.log("AdMobRewarded => videoStarted")
    );
    AdMobRewarded.addEventListener("adClosed", () => {
      console.log("AdMobRewarded => adClosed");
      AdMobRewarded.requestAd().catch(error => console.warn(error));
    });
    AdMobRewarded.addEventListener("adLeftApplication", () =>
      console.log("AdMobRewarded => adLeftApplication")
    );

    AdMobRewarded.requestAd().catch(error => console.warn(error));

    AdMobInterstitial.setTestDevices([AdMobInterstitial.simulatorId]);
    AdMobInterstitial.setAdUnitID("ca-app-pub-3940256099942544/1033173712");

    AdMobInterstitial.addEventListener("adLoaded", () =>
      console.log("AdMobInterstitial adLoaded")
    );
    AdMobInterstitial.addEventListener("adFailedToLoad", error =>
      console.warn(error)
    );
    AdMobInterstitial.addEventListener("adOpened", () =>
      console.log("AdMobInterstitial => adOpened")
    );
    AdMobInterstitial.addEventListener("adClosed", () => {
      console.log("AdMobInterstitial => adClosed");
      AdMobInterstitial.requestAd().catch(error => console.warn(error));
    });
    AdMobInterstitial.addEventListener("adLeftApplication", () =>
      console.log("AdMobInterstitial => adLeftApplication")
    );

    AdMobInterstitial.requestAd().catch(error => console.warn(error));

    // DFPNativeAds.setTestDevices([DFPNativeAds.simulatorId]);
    DFPNativeAds.setTemplateIDs(["11746836"]); //(["10104090"]);
    DFPNativeAds.setCustomTargeting({ Langue: "FR" });
  }

  componentWillUnmount() {
    AdMobRewarded.removeAllListeners();
    AdMobInterstitial.removeAllListeners();
  }

  showRewarded() {
    AdMobRewarded.showAd().catch(error => console.warn(error));
  }

  showInterstitial() {
    AdMobInterstitial.showAd().catch(error => console.warn(error));
  }

  requestNativeAd() {
    // PDP
    const PDPRequestKey = "PDP";
    const result = DFPNativeAds.addEventListener(
      PDPRequestKey,
      "adLoaded",
      body => console.log("PDP adLoaded: ", body)
    );
    result["remove"]();
    DFPNativeAds.addEventListener(PDPRequestKey, "adFailedToLoad", body =>
      console.log("PDP failed to load: ", body["adUnitID"], body["error"])
    );
    DFPNativeAds.addEventListener(PDPRequestKey, "adStartLoading", id =>
      console.log("PDP staring loading with id: ", id)
    );
    DFPNativeAds.addEventListener(PDPRequestKey, "allAdsFinished", body =>
      console.log("PDP all finished: ", body)
    );

    DFPNativeAds.requestAds(PDPRequestKey, [
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_1",
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_2",
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_3"
    ])
      .then(ads => {
        let text = "";
        for (const key in ads) {
          if (ads.hasOwnProperty(key)) {
            const element = ads[key];
            text = text.concat("\n", key, ": ", element["Titre"]);
          }
        }
        alert(`PDP:\n ${text}`);
        DFPNativeAds.removeAllListeners(PDPRequestKey);
      })
      .catch(error => {
        alert(`PDP:\n ${error}`);
        DFPNativeAds.removeAllListeners(PDPRequestKey);
      });
    DFPNativeAds.isNativeAdLoading(
      PDPRequestKey,
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_3",
      loading => console.log("isNativeLoading = PDP 3: ", loading)
    );

    // Home
    const HomeRequestKey = "Home";
    DFPNativeAds.addEventListener(HomeRequestKey, "adLoaded", body =>
      console.log("Home adLoaded: ", body)
    );
    DFPNativeAds.addEventListener(HomeRequestKey, "adFailedToLoad", body =>
      console.log("Home failed to load: ", body)
    );
    DFPNativeAds.addEventListener(HomeRequestKey, "adStartLoading", id =>
      console.log("Home staring loading with id: ", id)
    );
    DFPNativeAds.addEventListener(HomeRequestKey, "allAdsFinished", body =>
      console.log("Home all finished: ", body)
    );

    DFPNativeAds.requestAds(HomeRequestKey, [
      "/30879737/test4.lu_servicessuggeres/test4.lu_homepage_smartphone_acheter_servicessuggeres_4",
      "/30879737/test4.lu_servicessuggeres/test4.lu_homepage_smartphone_acheter_servicessuggeres_2",
      "/30879737/test4.lu_servicessuggeres/test4.lu_homepage_smartphone_acheter_servicessuggeres_3"
    ])
      .then(ads => {
        let text = "";
        for (const key in ads) {
          if (ads.hasOwnProperty(key)) {
            const element = ads[key];
            text = text.concat("\n", key, ": ", element["Titre"]);
          }
        }
        alert(`Home:\n ${text}`);

        DFPNativeAds.removeAllListeners(HomeRequestKey);
      })
      .catch(error => {
        alert(`Home ${error}`);

        DFPNativeAds.removeAllListeners(HomeRequestKey);
      });
    DFPNativeAds.isNativeAdLoading(
      HomeRequestKey,
      "/30879737/test4.lu_servicessuggeres/test4.lu_homepage_smartphone_acheter_servicessuggeres_2",
      loading => console.log("isNativeLoading = Home: ", loading)
    );

    // PDP 2
    // DFPNativeAds.addEventListener(PDPRequestKey, "adLoaded", body =>
    //   console.log("PDP 2 adLoaded: ", body)
    // );
    // DFPNativeAds.addEventListener(PDPRequestKey, "adFailedToLoad", body =>
    //   console.log("PDP 2 failed to load: ", body["adUnitID"], body["error"])
    // );
    // DFPNativeAds.addEventListener(PDPRequestKey, "adStartLoading", id =>
    //   console.log("PDP 2 staring loading with id: ", id)
    // );
    // DFPNativeAds.addEventListener(PDPRequestKey, "allAdsFinished", body =>
    //   console.log("PDP 2 all finished: ", body)
    // );
    DFPNativeAds.requestAds(PDPRequestKey, [
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_3",
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_4"
    ])
      .then(ads => {
        let text = "";
        for (const key in ads) {
          if (ads.hasOwnProperty(key)) {
            const element = ads[key];
            text = text.concat("\n", key, ": ", element["Titre"]);
          }
        }
        alert(`PDP 2:\n ${text}`);
      })
      .catch(error => alert(`PDP 2:\n ${error}`));
    DFPNativeAds.isNativeAdLoading(
      PDPRequestKey,
      "/30879737/test4.lu_servicessuggeres/test4.lu_fichedetail_smartphone_louer_servicessuggeres_3",
      loading => console.log("isNativeLoading = PDP 2 - id 3: ", loading)
    );
  }

  render() {
    return (
      <View style={styles.container}>
        <ScrollView>
          <BannerExample title="AdMob - Basic">
            <AdMobBanner
              adSize="banner"
              adUnitID="ca-app-pub-3940256099942544/6300978111"
              ref={el => (this._basicExample = el)}
            />
            <Button
              title="Reload"
              onPress={() => this._basicExample.loadBanner()}
            />
          </BannerExample>
          <BannerExample title="Smart Banner">
            <AdMobBanner
              adSize="smartBannerPortrait"
              adUnitID="ca-app-pub-3940256099942544/6300978111"
              ref={el => (this._smartBannerExample = el)}
            />
            <Button
              title="Reload"
              onPress={() => this._smartBannerExample.loadBanner()}
            />
          </BannerExample>
          <BannerExample title="Rewarded">
            <Button
              title="Show Rewarded Video and preload next"
              onPress={this.showRewarded}
            />
          </BannerExample>
          <BannerExample title="Interstitial">
            <Button
              title="Show Interstitial and preload next"
              onPress={this.showInterstitial}
            />
          </BannerExample>
          <BannerExample title="DFP - Multiple Ad Sizes">
            <PublisherBanner
              adSize="banner"
              validAdSizes={["banner", "largeBanner", "mediumRectangle"]}
              adUnitID="/6499/example/APIDemo/AdSizes"
              ref={el => (this._adSizesExample = el)}
            />
            <Button
              title="Reload"
              onPress={() => this._adSizesExample.loadBanner()}
            />
          </BannerExample>
          <BannerExample
            title="DFP - App Events"
            style={this.state.appEventsExampleStyle}
          >
            <PublisherBanner
              style={{ height: 50 }}
              adUnitID="/6499/example/APIDemo/AppEvents"
              onAdFailedToLoad={error => console.warn(error)}
              onAppEvent={event => {
                if (event.name === "color") {
                  this.setState({
                    appEventsExampleStyle: { backgroundColor: event.info }
                  });
                }
              }}
              ref={el => (this._appEventsExample = el)}
            />
            <Button
              title="Reload"
              onPress={() => this._appEventsExample.loadBanner()}
              style={styles.button}
            />
          </BannerExample>
          <BannerExample title="DFP - Fluid Ad Size">
            <View
              style={[
                { backgroundColor: "#f3f", paddingVertical: 10 },
                this.state.fluidAdSizeExampleStyle
              ]}
            >
              <PublisherBanner
                adSize="fluid"
                adUnitID="/6499/example/APIDemo/Fluid"
                ref={el => (this._appFluidAdSizeExample = el)}
                style={{ flex: 1 }}
              />
            </View>
            <Button
              title="Change Banner Width"
              onPress={() =>
                this.setState(prevState => ({
                  fluidSizeIndex: prevState.fluidSizeIndex + 1,
                  fluidAdSizeExampleStyle: {
                    width:
                      bannerWidths[
                        prevState.fluidSizeIndex % bannerWidths.length
                      ]
                  }
                }))
              }
              style={styles.button}
            />
            <Button
              title="Reload"
              onPress={() => this._appFluidAdSizeExample.loadBanner()}
              style={styles.button}
            />
          </BannerExample>
          <BannerExample title="DFP - native ads">
            <Button
              title="fetch custom rendering"
              onPress={this.requestNativeAd}
            />
          </BannerExample>
        </ScrollView>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    marginTop: Platform.OS === "ios" ? 30 : 10
  },
  example: {
    paddingVertical: 10
  },
  title: {
    margin: 10,
    fontSize: 20
  }
});

AppRegistry.registerComponent("Example", () => Example);
