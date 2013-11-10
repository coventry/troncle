(ns troncle.core
  (:require [troncle.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]
            [troncle.util :as u]
            [clojure.walk2 :as w]
            [troncle.traces :as t]
            [troncle.wrap]))

(defn parse-tree [s]
  "Return the location-decorated parse tree of s"
  (let [reader (rt/indexing-push-back-reader s)
        ;; EOF sentinel for r/read.  Make sure it's not in the string
        ;; to be parsed.  
        eof (loop [] (let [eof (gensym "parser-eof__")]
                       (if (.contains s (str eof)) (recur) eof)))]
    (take-while #(not (= eof %)) (repeatedly #(r/read reader nil eof)))))

(defn line-starts [s]
  "Returns the offset in s of the start of each new line"
  (let [m (re-matcher #"\r?\n" s)]
    (loop [starts [0]]
      (if (.find m)
        (recur (conj starts (.end m)))
        starts))))

(defn offset-from-line-column
  "Get the offset into the string from the line/column position, using
  the positions computed in line-starts"
  [line column linestarts]
  (+ (linestarts line) column))

(defn in-region? [linestarts start end f]
  (let [m (or (meta f) (constantly 0)) ; If no metadata assume offset 0
        sl (m :line) sc (m :column) el (m :end-line) ec (m :end-column)]
    (if (not ((set [sl sc el ec]) nil)) ; Meta data is present
      (let [so (offset-from-line-column sl sc linestarts)
            eo (offset-from-line-column el ec linestarts)]
        (<= start so eo end)))))

(defn mark-contained-forms [linestarts start end fs]
  "Given forms fs, a list of the offsets for each new line in
  linestarts, and offsets start and end, decorate each form in fs
  which lay between start end end with ^{:wrap true}"
  (w/walk (partial mark-contained-forms linestarts start end)
          (t/trace-fn #(if (in-region? linestarts start end %)
                         (vary-meta % conj {:wrap true})
                         :no-hit))
          fs))

(let [f (parse-tree u/test-strings)
      l (line-starts u/test-strings)
      nf (mark-contained-forms l 5 50 f)]
  (u/pprint (u/metadata-walk nf)))

