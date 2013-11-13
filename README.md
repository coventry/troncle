# troncle

Troncle is a proof-of-concept integration of clojure's tracing tools
with emacs.

`clojure.tools.trace` is super-handy, but wrapping various forms in your
code with `(ctt/trace ...)` as you explore how it's working gets
tedious.  The main idea with troncle is to take most of that tedium
away.

## Usage

Currently, troncle has one function: You mark a region of your code,
hit`M-x troncle-trace-region` (`C-c t R`), and troncle sends the
top-level defun containing point to clojure.  Any forms contained in the
marked region, as well as any forms generated by their macroexpansion,
are instrumented with `clojure.tools.trace/trace` so that the results of
their evaluation during execution are reported in the repl buffer.

In order to do this, you have to let troncle know how to execute the
code.  You do this using the atom
`tronce.traces/trace-execution-function`.

For instance, suppose that you have the following code in `tst.clj`:

```clojure
(ns troncle.tst)

(defn ^String capitalize
  [^CharSequence s]
  (let [s (.toString s)]
    (if (< (count s) 2)
      (.toUpperCase s)
      (str (.toUpperCase (subs s 0 1))
           (.toLowerCase (subs s 1))))))
```

After installing troncle as described in the
[installation](#Installation) section

## Installation

Foo

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
