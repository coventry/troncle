# troncle

Troncle is a proof-of-concept integration of clojure's tracing tools
with emacs, built around technomancy's innovative
[nrepl-discover](https://github.com/technomancy/nrepl-discover) and a
robust (very simple) [code-walking/wrapping
macro](https://github.com/coventry/troncle/blob/master/src/troncle/macroshka.clj)
I've developed.


`clojure.tools.trace` is super-handy for exploring how code is
executing, but wrapping and unwrapping subforms with `(ctt/trace ...)`
gets tedious.  The main idea with troncle is to take most of that tedium
away by letting you use emacs to point at the forms you want to wrap and
then doing the wrapping for you automatically during compilation.

This release is a very rough first cut, which I've published mostly
because I'm hoping to talk to people at the Clojure Conj about what
directions to take it in.  If this seems like an interesting project to
you, please take a look at the [roadmap](#roadmap) and let me know what
you think.  (Whether you're at the Conj or not, of course.)  If it seems
useless or otherwise misguided, please also let me know what you
think. :-) All feedback is welcome.

## Usage

Troncle's goal is to speed up a typical repl debugging workflow.
When you hit a bug, you write a test for it.  For instance, suppose we
have the following simple clojure file.

<img src="resources/images/tst.clj.png" width="50%">

If we compile this and run the test, it fails:

```clojure
troncle.tst> (capitalization)

FAIL in (capitalization) (tst.clj:14)
expected: (= "Foo" (capitalize "foo"))
  actual: (not (= "Foo" "foo"))
```

You can use troncle to quickly select regions of the code and check what
values they're returning as you're running the test.  In this case,
you'd do `C-c t E` (`troncle-set-exec-var`), choose `capitalization`,
then choose a region of the `capitalize` form to instrument for tracing
and send it off with `C-c t R` (`troncle-trace-region`.)  By the way,
the var you choose with `troncle-set-exec-var` needn't be in the same
namespace as the code you're interested in tracing, so it is compatible
with the usual practice of separating code and tests.  You can also set
the function to be executed at the repl using `troncle.traces/st`.
Whichever method you use, the function will be called with no arguments
on the clojure side when `troncle-trace-region` is called on the emacs
side.

Suppose we run `C-c t R` with the following region of `tst.clj`
selected:

<img src="resources/images/tst.clj.select-1.png" width="50%">

This results in the following output in the repl:

```clojure
L:5 C:11 (.toString s)
=> "foo"
L:6 C:12 (count s)
=> 3
L:6 C:9 (< (count s) 2)
=> false
```

Troncle wraps all evaluable forms in the full macroexpansion of any
forms in the region with tracing instrumentation.  In this case, for
instance, `L:5 C:11 (.toString s) => "foo"` means that the form
`(.toString s)` starting at line 5, column 11 returned the value "foo".

It all looks like sensible output, so let's try selecting another
region:

<img src="resources/images/tst.clj.select-2.png" width="50%">

Part of the output from this is 

```clojure
L:9 C:8 (str (.toUpperCase (subs s 0 1)) (.toLowerCase (subs s 1)))
=> "Foo"
L:8 C:7 (.toLowerCase (str (.toUpperCase (subs s 0 1)) (.toLowerCase (subs s 1))))
=> "foo"
```

So we've found the bug, a spurious `(.toLowerCase)`.

## Installation

1. In emacs, `M-x package-install troncle`.

2. In the `project.clj` file for the lein project where you want to use
   troncle, add

   ```clojure
   :repl-options {:nrepl-middleware [nrepl.discover/wrap-discover]}
   ```

   Also add `[troncle "0.1.0-SNAPSHOT"]` to your project.clj's
   `:dependencies` vector.
   
  You can also add these modifications to your `:user` map in your
  `~/.lein/profiles.clj`.

3. `M-x nrepl-jack-in` in your target project.  (Restart the jvm if
   necessary, to get the nrepl-discover middleware operating.)

4. Compile the code you want to execute with `C-c C-k`.

5. Set the function to be run by troncle using `M-x
   troncle-set-exec-var` from emacs or `tronce.traces/st` in the `nrepl`
   buffer.  (see [Usage](usage) for an example.)

6. Mark the forms you want traced, and hit `C-c t R` and watch the
   output in the repl!

## Roadmap


### Extended functionality

This is a very simple application at the moment, but I think it has a
lot of potential.  The core functionality is in `troncle.macroshka`,
which is a very robust code-walking scheme.  (I've run it over the
entire clojure source code.)

These are the directions I'd like to move it in:

1. Emacs convenience functions for passing "load this file" and "run
   this test" functions to `tronce.traces/st`.

2. Tracing instrumentation for multiple regions within a top-level
   form. 

3. Filtered tracing: Only report a trace when a given predicate returns
   true.  Predicate can be specified in terms of return values of the
   forms under consideration.

4. Save and restore current tracing configuration.

5. Tracing of bindings to local variables.

6. Send trace reports to a clojure list, rather than/as well as the
   repl, so that they can be queried programmatically.

7. Replay a series of trace reports using emacs overlays
   (nrepl-discover's overlay facility should make this easy.)

8. Replace the tracing with source-level step debugging.

## License

Copyright © 2013 Alex Coventry

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
