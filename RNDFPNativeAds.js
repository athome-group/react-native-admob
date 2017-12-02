import { NativeEventEmitter, NativeModules } from "react-native";

import { createErrorFromErrorData } from "./utils";

const REQUEST_KEY = "requestKey";

const RNDFPNativeAds = NativeModules.RNDFPNativeAds;
const eventEmitter = new NativeEventEmitter(RNDFPNativeAds);

const eventMap = {
  adLoaded: "nativeCustomTemplateAdLoaded",
  adFailedToLoad: "nativeCustomTemplateAdFailedToLoad",
  adStartLoading: "nativeCustomTemplateAdStartLoading",
  allAdsFinished: "nativeCustomTemplateAllAdsFinished"
};

const _subscriptions = new Map();

const addEventListener = (requestKey, event, handler) => {
  if (!_subscriptions[requestKey]) {
    _subscriptions[requestKey] = new Map();
  }

  const mappedEvent = eventMap[event];
  if (mappedEvent) {
    let listener;
    if (event === "adFailedToLoad") {
      listener = eventEmitter.addListener(mappedEvent, body => {
        if (body[REQUEST_KEY] === requestKey) {
          handler({ ...body, error: createErrorFromErrorData(body["error"]) });
        }
      });
    } else {
      listener = eventEmitter.addListener(mappedEvent, body => {
        if (body[REQUEST_KEY] === requestKey) {
          handler(body);
        }
      });
    }
    _subscriptions[requestKey].set(handler, listener);
    return {
      remove: () => removeEventListener(requestKey, event, handler)
    };
  } else {
    console.warn(`Trying to subscribe to unknown event: "${event}"`);
    return {
      remove: () => {}
    };
  }
};

const removeEventListener = (requestKey, type, handler) => {
  const listener = _subscriptions[requestKey].get(handler);
  if (!listener) {
    return;
  }
  listener.remove();
  _subscriptions[requestKey].delete(handler);
};

const removeAllListeners = requestkey => {
  _subscriptions[requestkey].forEach((listener, key, map) => {
    listener.remove();
    map.delete(key);
  });
};

export default {
  ...RNDFPNativeAds,
  addEventListener,
  removeEventListener,
  removeAllListeners
};
