(ns crdtdemo.set2p
  (:refer-clojure :exclude [empty remove merge])
  (:require [clojure.set :as set]))

(defn empty []
  {:a #{}
   :r #{}})

(defn add [{:keys [a r]} x]
  {:a (conj a x)
   :r r})

(defn remove [{:keys [a r]} x]
  {:a a
   :r (conj r x)})

(defn merge [set1 set2]
  {:a (set/union (:a set1) (:a set2))
   :r (set/union (:r set1) (:r set2))})

(defn get-value [{:keys [a r]}]
  (set/difference a r))

(defn gc [{:keys [a r]}]
  {:a (set/difference a r)
   :r #{}})
