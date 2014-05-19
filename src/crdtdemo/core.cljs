(ns crdtdemo.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [cljs.core.match.macros :refer [match]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [cljs.core.match]
            [clojure.set :as set]
            [crdtdemo.set2p :as set2p]))

(enable-console-print!)

;; Query generation and execution

(defn rand-value []
  (let [values ["Anna" "Darja" "Sofija" "Diana" "Marija"
                "Viktorija" "Tatjana" "Kristina" "Elizaveta"]]
    (nth values (rand-int (count values)))))

(defn rand-query []
  (if (> 0.3 (rand))
    [:remove (rand-value)]
    [:add (rand-value)]))

(defn rand-srv []
  (nth [:srv1 :srv2 :srv3] (rand-int 3)))

(defn make-query [[query-type var] srv]
  (case query-type
    :add (update-in srv [:state] set2p/add var)
    :remove (update-in srv [:state] set2p/remove var)))

(defn consistent? [{:keys [srv1 srv2 srv3]}]
  (and (= (set2p/get-value (:state srv1))
          (set2p/get-value (:state srv2)))
       (= (set2p/get-value (:state srv2))
          (set2p/get-value (:state srv3)))))

;; DOM helpers

(defn form-list [elems]
  (if (empty? elems)
    "[empty]"
    (apply dom/ul nil (for [val elems] (dom/li nil (str val))))))

(defn form-button
  ([command-c command text]
     (form-button command-c command text :default))
  ([command-c command text style]
     (dom/button #js{:className (str "btn btn-" (name style))
                     :onClick (fn [e] (put! command-c command))}
                 text)))

(defn form-history-entry [history-entry]
  (case (first history-entry)
    :query (let [[_ [query-type var]] history-entry]
             (str (name query-type) " \"" var "\""))
    :gc "perform GC"
    :sync (let [[_ a b] history-entry]
            (str "sync " (name a) " and " (name b)))
    (pr-str history-entry)))

(defn form-toolbar [command-c consistent]
  (dom/div #js{:className "btn-toolbar"
               :role "toolbar"}
    (dom/div #js{:className "btn-group"}
      (if consistent
        (dom/div #js{:className "btn btn-default disabled"}
                 (dom/i #js{:className "glyphicon glyphicon-ok"}))
        (dom/div #js{:className "btn btn-default disabled"}
                 (dom/i #js{:className "glyphicon glyphicon-remove"}))))
    (dom/div #js{:className "btn-group"}
      (form-button command-c [:sync :srv1 :srv2] "Sync 1 and 2")
      (form-button command-c [:sync :srv1 :srv3] "Sync 1 and 3")
      (form-button command-c [:sync :srv2 :srv3] "Sync 2 and 3"))
    (dom/div #js{:className "btn-group"}
      (form-button command-c :gc "Global sync&GC"))
    (dom/div #js{:className "btn-group"}
      (form-button command-c :query "Perform random query" :primary))))

;; Om app starts here

(def app-state
  (atom {:history []
         :srv1   {:state (set2p/empty)}
         :srv2   {:state (set2p/empty)}
         :srv3   {:state (set2p/empty)}}))

(defn server-view [[n server] owner]
  (reify
    om/IDisplayName
    (display-name [this] "client's timeline")
    om/IRenderState
    (render-state [this state]
      (dom/div #js {:className "timeline"}
        (dom/div #js {:className "well"}
          (dom/h3 nil (str "Server " n))
          (dom/div nil
             "Returned set: "
             (form-list (sort (set2p/get-value (:state server)))))
          (dom/div nil
             "Actual data:"
             (dom/ul nil
               (dom/li nil "A: " (form-list (sort (:a (:state server)))))
               (dom/li nil "R: " (form-list (sort (:r (:state server))))))))))))

(defn history-view [history owner]
  (reify
    om/IDisplayName
    (display-name [this] "Query history")
    om/IRenderState
    (render-state [this state]
      (dom/div #js{:className "timeline"}
        (dom/div #js{:className "well"}
          (dom/h3 nil "Query history: ")
          (form-list (reverse (map form-history-entry history))))))))

(defn timelines-view [app owner]
  (reify
    om/IDisplayName
    (display-name [this] "timelines")
    om/IInitState
    (init-state [_]
      {:command-c (chan)})
    om/IWillMount
    (will-mount [_]
      (let [command-c (om/get-state owner :command-c)]
        (go (loop []
              (let [msg (<! command-c)]
                (match msg
                 :query (let [query (rand-query)]
                          (om/transact! app :history
                                        #(conj % [:query query]))
                          (om/transact! app (rand-srv)
                                        (partial make-query query)))
                 :gc (let [new-state (-> (set2p/merge (-> @app :srv1 :state)
                                                      (-> @app :srv2 :state))
                                         (set2p/merge (-> @app :srv3 :state))
                                         set2p/gc)]
                       (om/transact! app :history #(conj % [:gc]))
                       (doseq [srv [:srv1 :srv2 :srv3]]
                         (om/update! app srv {:state new-state})))
                 [:sync a b] (let [new-state (set2p/merge (-> @app a :state)
                                                          (-> @app b :state))]
                               (om/transact! app :history
                                             #(conj % [:sync a b]))
                               (om/update! app [a] {:state new-state})
                               (om/update! app [b] {:state new-state}))))
              (recur)))))
    om/IRenderState
    (render-state [this {:keys [command-c]}]
      (dom/div #js{:className "container"}
        (dom/div #js{:className "row"}
          (dom/div #js{:className "col-xs-12"}
            (dom/div #js{:className "page-header"}
                     (dom/h1 nil "2P-Set demo")
                     (form-toolbar command-c (consistent? app))
                     (dom/div #js{:style #js{:clear "both"}}))))
        (dom/div #js{:className "row"}
          (dom/div #js{:className "col-xs-3"}
            (om/build server-view [1 (:srv1 app)]))
          (dom/div #js{:className "col-xs-3"}
            (om/build server-view [2 (:srv2 app)]))
          (dom/div #js{:className "col-xs-3"}
            (om/build server-view [3 (:srv3 app)]))
          (dom/div #js{:className "col-xs-3"}
            (om/build history-view (:history app))))))))

(om/root timelines-view app-state
         {:target (. js/document (getElementById "app"))})
