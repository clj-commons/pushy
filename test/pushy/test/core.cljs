(ns pushy.test.core
  (:require
   [clojure.test :refer [deftest is testing async] :as test]
   [pushy.core :as pushy]
   [secretary.core :as secretary :refer-macros [defroute]]
   [goog.events :as events])
  (:import goog.history.Html5History))

(secretary/set-config! :prefix "/")
(def test-val (atom false))

(def history
  (pushy/pushy secretary/dispatch!
               (fn [x] (when (secretary/locate-route x) x))
               identity))

(defroute foo-route "/foo" []
  (reset! test-val true))

(defroute bar-route "/bar" []
  (reset! test-val true))

(deftest constructing-history
  (is (instance? Html5History (pushy/new-history))))

(deftest constructing-pushy
  (is (satisfies? pushy/IHistory (pushy/pushy (constantly nil) (constantly nil)))))

(deftest supported-browser
  (is (pushy/supported?)))

;; event listeners started = dispatch
(deftest push-state-foo-route
  (async done
         (reset! test-val false)
         (pushy/start! history)
         (pushy/replace-token! history "/foo")
         (js/setTimeout
          (fn []
            (is @test-val)
            (is (nil? (pushy/stop! history)))
            (is (= "/foo" (pushy/get-token history)))
            (done))
          5000)))

;; no event listeners started = no dispatch
(deftest push-state-bar-route
  (async done
         (reset! test-val false)
         (pushy/replace-token! history "/bar")
         (js/setTimeout
          (fn []
            (is (false? @test-val))
            (is (= "/bar" (pushy/get-token history)))
            (done))
          5000)))

;; Helpers for the link click tests below

(defn- click-link!
  "Adds a link to `href` to the page, clicks it, then removes it. `attrs` is a
  map of extra attributes for the link."
  ([href] (click-link! href {}))
  ([href attrs]
   (let [el (.createElement js/document "a")]
     (.setAttribute el "href" href)
     (doseq [[k v] attrs]
       (.setAttribute el k v))
     ;; Never let the browser follow the link and unload the test page. Pushy
     ;; listens on the document and does not look at `defaultPrevented`, so
     ;; this does not change what it does.
     (.addEventListener el "click" #(.preventDefault %))
     (.appendChild (.-body js/document) el)
     (.click el)
     (.removeChild (.-body js/document) el))))

(defn- with-click-history
  "Starts a pushy instance sitting on `token` that records every dispatch and
  every same-token click. `opts` are extra options for `pushy/pushy`; pass
  `:same-token-click? true` to install the `:on-same-token-click` callback.
  Calls `f` with the instance and atoms of the dispatched and same-token values."
  [token {:keys [same-token-click?] :as opts} f]
  (let [dispatched (atom [])
        same-token (atom [])
        opts (cond-> (dissoc opts :same-token-click?)
               same-token-click? (assoc :on-same-token-click #(swap! same-token conj %)))
        h (apply pushy/pushy #(swap! dispatched conj %) identity (mapcat identity opts))]
    (pushy/start! h)
    (pushy/replace-token! h token)
    (reset! dispatched [])
    (try
      (f h dispatched same-token)
      (finally
        (pushy/stop! h)))))

(deftest click-to-other-token-dispatches
  (with-click-history "/click-a" {}
    (fn [h dispatched _]
      (click-link! "/click-b")
      (is (= ["/click-b"] @dispatched))
      (is (= "/click-b" (pushy/get-token h))))))

;; set-token! skips an unchanged token; replace-token! always dispatches
(deftest replace-token-dispatches-for-current-token
  (with-click-history "/click-same" {}
    (fn [h dispatched _]
      (pushy/set-token! h "/click-same")
      (is (= [] @dispatched))
      (pushy/replace-token! h "/click-same")
      (is (= ["/click-same"] @dispatched)))))

;; default: clicking a link to the current URL does nothing
(deftest click-to-same-token-does-nothing-by-default
  (with-click-history "/click-same" {}
    (fn [h dispatched _]
      (let [entries (.-length js/history)]
        (click-link! "/click-same")
        (is (= [] @dispatched))
        (is (= "/click-same" (pushy/get-token h)))
        (is (= entries (.-length js/history)))))))

;; with a callback: it is called, dispatch-fn is not, history is untouched
(deftest click-to-same-token-calls-callback
  (with-click-history "/click-same" {:same-token-click? true}
    (fn [h dispatched same-token]
      (let [entries (.-length js/history)]
        (click-link! "/click-same")
        (is (= ["/click-same"] @same-token))
        (is (= [] @dispatched))
        (is (= "/click-same" (pushy/get-token h)))
        (is (= entries (.-length js/history)))
        (testing "a different token goes through history and dispatch-fn, not the callback"
          (click-link! "/click-other")
          (is (= ["/click-other"] @dispatched))
          (is (= ["/click-same"] @same-token))
          (is (= (inc entries) (.-length js/history))))))))

(deftest same-token-callback-gets-identity-fn-result
  (with-click-history "/click-same" {:same-token-click? true
                                     :identity-fn (fn [token] {:route token})}
    (fn [_ _ same-token]
      (click-link! "/click-same")
      (is (= [{:route "/click-same"}] @same-token)))))

(deftest same-token-callback-compares-query-and-fragment
  (with-click-history "/click-same?q=1#top" {:same-token-click? true}
    (fn [_ dispatched same-token]
      (click-link! "/click-same?q=1#top")
      (is (= ["/click-same?q=1#top"] @same-token))
      (is (= [] @dispatched))
      (testing "a different fragment is a different token"
        (click-link! "/click-same?q=1#other")
        (is (= ["/click-same?q=1#top"] @same-token))
        (is (= ["/click-same?q=1#other"] @dispatched))))))

;; clicks pushy leaves to the browser never reach the callback
(deftest same-token-callback-skipped-for-bypassed-clicks
  (with-click-history "/click-same" {:same-token-click? true}
    (fn [_ dispatched same-token]
      (click-link! "/click-same" {"data-pushy-ignore" "true"})
      (click-link! "/click-same" {"target" "_blank"})
      (is (= [] @same-token))
      (is (= [] @dispatched)))))

;; the callback only fires for routes match-fn recognises
(deftest same-token-callback-skipped-when-no-match
  (let [same-token (atom [])
        h (pushy/pushy (fn [_]) (fn [token] (when (= token "/known") token))
                       :on-same-token-click #(swap! same-token conj %))]
    (pushy/start! h)
    (try
      (pushy/replace-token! h "/unknown")
      (click-link! "/unknown")
      (is (= [] @same-token))
      (pushy/replace-token! h "/known")
      (click-link! "/known")
      (is (= ["/known"] @same-token))
      (finally
        (pushy/stop! h)))))
