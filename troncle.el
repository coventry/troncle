;;; troncle.el --- Emacs convenience functions for tracing clojure code

;; Version: 0.1.2
;; Author: Alex Coventry
;; URL: https://github.com/coventry/troncle
;; Package-Requires: ((nrepl "0.2.0"))

;;; Commentary:

;; A library of functions for quickly wrapping and executing clojure
;; code with tracing instrumentation.  See
;; https://github.com/coventry/troncle for usage and installation
;; instructions.
;; 
;; This is a stub: To ensure that troncle's elisp and clojure logic
;; are compatible, the elisp for interaction with troncle is loaded
;; from the troncle jar file on the clojure side.

;;; Code:

(provide 'troncle)

(defun map->assoc (m)
  "Given a (key val ...) list as returned by nrepl, return an
  association list."
  (apply 'format-spec-make m))

(defun str (&rest vals) (mapconcat (lambda (v) (pp-to-string v)) vals " "))

(defun troncle-get-elisp ()
  "Pull the elisp interaction logic out of clojure as a string"
  (let* ((get-troncle-cmd "(troncle.emacs/get-troncle-source)")
	 (resp (map->assoc (nrepl-send-string-sync get-troncle-cmd)))
	 (troncle-src (assoc-default ':value resp)))
    (unless (string-match "(provide 'troncle)" troncle-src)
      (error (concat "Could not get troncle interaction elisp from repl server.\n"
		     "Make sure your clojure project has troncle loaded.\n"
		     "See https://github.com/coventry/troncle#installation")))
    (mapconcat 'identity (split-string-and-unquote troncle-src) "\n")))

(defun troncle-load-elisp ()
    ;; Load the forms provided from clojure
    (with-temp-buffer (insert (troncle-get-elisp)) (eval-buffer)))

(troncle-load-elisp)

;;; troncle.el ends here
