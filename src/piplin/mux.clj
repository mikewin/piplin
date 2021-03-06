(ns piplin.mux
  (:require [clojure.core :as clj])
  (:refer-clojure :exclude [cond condp cast])
  (:use [piplin types protocols])
  (:use [slingshot.slingshot]))

(defn mux2-impl
  [sel v1 v2]
  (when-not (clj/= (typeof v1) (typeof v2))
    (throw+ (error v1 "and" v2 "are different types" (typeof v1) (typeof v2))))
  (let [sel (cast (anontype :boolean) sel)]
    (if (pipinst? sel)
      (if sel v1 v2)
      (->
        (mkast (typeof v1) :mux2 [sel v1 v2] mux2-impl)
        (assoc-dist-fn
          #(mux2-impl sel (cast % v1) (cast % v2)))))))

(defn mux2-helper
  [sel v1-thunk v2-thunk]
  (let [v1 (v1-thunk)
        v2 (v2-thunk)]
    (mux2-impl sel v1 v2)))

(defmacro mux2
  [sel v1 v2]
  `(mux2-helper ~sel (fn [] ~v1) (fn [] ~v2)))

(defn cond-helper [predicates thunks]
  (if (some #(instance? piplin.types.ASTNode %) predicates)
    (let [last-pred (last predicates)
          predicates (butlast predicates)
          mux-tree (->> thunks
                     (interleave predicates)
                     (partition 2)
                     reverse
                     (reduce (fn [prev [p t]]
                               (fn [] (mux2-helper p t prev)))
                             (last thunks)))]
      (if (clj/= last-pred :else)
        (mux-tree)
        (throw+ (error "Must include :else in simulated cond"))))
    (let [thunk (->> thunks
                  (interleave predicates)
                  (partition 2)
                  (keep (fn [[p t]] (if p t nil)))
                  first)]
      (if (nil? thunk)
        nil
        (thunk)))))

(defmacro cond [& more]
  (when-not (even? (count more))
    (throw (RuntimeException. "cond takes an even number of clauses")))
  (let [bodies (->> more rest (take-nth 2))
        thunks (map (fn [body]
                      `(fn [] ~body))
                    bodies)
        predicates (take-nth 2 more)]
    `(cond-helper [~@predicates] [~@thunks])))

(defmacro condp [pred expr & clauses]
  (let [pairs (partition 2 clauses)
        else (if (even? (count clauses)) nil (last clauses))
        body (mapcat (fn [[test body]]
                       `((~pred ~test ~expr) ~body))
                     pairs)
        body (if (nil? else) body (concat body [:else else]))]
    `(cond ~@body)))


