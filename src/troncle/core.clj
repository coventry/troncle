(ns troncle.core
  (:require [clojure.tools.reader :as r]
            [troncle.traces :as t]
            [troncle.macroshka :as wm]
            [troncle.util :as u]
            [clojure.tools.reader.reader-types :as rt]
            [clojure.walk2 :as w]))

(defn parse-tree 
  "Return the location-decorated parse tree of s"
  [s]
  (let [reader (rt/indexing-push-back-reader s)
        ;; EOF sentinel for r/read.  Make sure it's not in the string
        ;; to be parsed.
        eof (->> #(gensym "parser-eof__") repeatedly
                 (remove #(.contains s (str %))) first)]
    (take-while #(not (= eof %)) (repeatedly #(r/read reader nil eof)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Identification of forms to trace

(defn line-starts 
  "Returns the offset in s of the start of each new line"
  [s]
  (let [m (re-matcher #"\r?\n" s)
        nl #(if (.find m) (.end m))]
    (into [0] (take-while identity (repeatedly nl)))))

(defn line-column-from-offset
  "Get the line/column position from offset into the string."
  ([offset linestarts]
     (line-column-from-offset offset linestarts 0))
  ([offset linestarts startidx]
     (let [[linenum soffset]
           (last (take-while #(<= (% 1) offset)
                             (map-indexed vector
                                          (drop startidx linestarts))))]
       [(+ startidx linenum 1) (- offset soffset -1)])))

(defn offset-from-line-column
  "Get the offset into the string from the line/column position, using
  the positions computed in line-starts"
  [line column linestarts]
  (+ (linestarts (- line 1)) (- column 1)))

(defn in-region? 
  "Given linestarts a list of offsets for each new line in the source
  string, a region in the string marked by start and end, and f a
  subform taken from the read of the source string, test whether f's
  metadata lay in the specified region."
  [linestarts start end f]
  (let [m (or (meta f) (constantly 1)) ; If no metadata assume offset 0
        sl (m :line) sc (m :column) el (m :end-line) ec (m :end-column)]
    (if (not ((set [sl sc el ec]) nil)) ; Meta data is present
      (let [so (offset-from-line-column sl sc linestarts)
            eo (offset-from-line-column el ec linestarts)]
        (<= start so eo end)))))

(defn maybe-mark-form [linestarts start end f]
  (if (in-region? linestarts start end f)
    (vary-meta f conj {::wrap true})
    f))

(defn mark-contained-forms 
  "Given forms fs, a list of the offsets for each new line in
  linestarts, and offsets start and end, decorate each form in fs
  which lie between start end end with ^{:wrap true}"
  [linestarts start end fs]
  (u/postwalk (partial maybe-mark-form linestarts start end) fs))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing/wrapping logic

;; Becase it's a recursing macro, wm/wrap-form needs to reference fns
;; as var symbols.
(defonce dummy-ns (create-ns (gensym))) ;; Keep the vars here
(defn assign-var 
  "Return a fully-qualified symbol of a var assigned to value v"
  [name v]
  (let [s (gensym name)]
    (intern dummy-ns s v)
    (symbol (-> dummy-ns ns-name str) (str s))))

(def never (constantly false))

(defn maybe-wrap [trace-wrap]
  (fn [f ft]
    (if (-> f meta ::wrap) (trace-wrap f ft) ft)))

(defn trace-marked-form*
  "Evaluate f in the given ns, with any subforms marked with ^{::wrap
  true} wrapped by the fn pointed to by the trace-wrap var."
   [trace-wrap f ns]
   (binding [*ns* ns]
     (eval `(wm/wrap-form never identity ~trace-wrap ~f))))

(defn trace-marked-forms
  "Read and evaluate each top-level form in source in the given ns.
  Instrument any subforms between offsets start and end with tracing
  using trace-wrap."
  [source start end ns trace-wrap]
  (let [ls (line-starts source)
        tw (assign-var 'trace-wrap (maybe-wrap trace-wrap))]
    ;; Must (read) and (eval) top-level forms in lockstep, because
    ;; evaluation of earlier forms can establish context for reading
    ;; later forms.  (E.g., tagged literals)
    (doseq [form (parse-tree source)
            :let [mf  (mark-contained-forms ls start end form)]]
      (trace-marked-form* tw mf ns))))

