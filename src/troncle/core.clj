(ns troncle.core
  (:require [troncle.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]))

(defn escape-to-regex [s]
  (re-pattern (java.util.regex.Pattern/quote s)))

(defn parse-tree [s]
  "Return the location-decorated parse tree of s"
  (let [reader (rt/indexing-push-back-reader s)
        ;; EOF sentinel for r/read.  Make sure it's not in the string
        ;; to be parsed.
        eof (loop [] (let [eof (gensym "parser-eof__")]
                       (if (re-find (escape-to-regex (str eof)) s)
                         (recur)
                         eof)))]
    (loop [rv []]
      (let [form (r/read reader nil eof)]
        ;; (meta (first (conj rv form)))
        (if (not (= form eof)) (recur (conj rv form))
             rv)))))

(parse-tree (slurp (clojure.java.io/resource "clojure/core.clj")))
