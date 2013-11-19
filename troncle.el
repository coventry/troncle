;;; troncle.el --- Emacs convenience functions for tracing clojure code
;; Version: 0.1.1
;; Author: Alex Coventry
;; URL: https://github.com/coventry/troncle

;; A library of functions for quickly wrapping and executing clojure
;; code with tracing instrumentation.  See
;; https://github.com/coventry/troncle for usage and installation
;; instructions.

(require 'clojure-mode)
(require 'nrepl)

(defun troncle-op-handler (buffer)
  "Return a handler for nrepl responses.  Copied from
  nrepl-discover's nrepl-discover-op-handler."
  (lexical-let ((buffer buffer))
    (lambda (response)
      (nrepl-dbind-response response (message)
	(when message (message message))
	;; There is a bunch more I'm leaving out from
	;; nrepl-discover-op-handler.  Definitely want to go back and
	;; look at how it handles overlays, when I get to that part.
	))))

(defun str (&rest vals) (mapconcat (lambda (v) (pp-to-string v)) vals " "))

(defun troncle-toplevel-region-for-region ()
  "Get all top-level forms contained in or neighbouring region
between point and mark."
  (let ((start (save-excursion (goto-char (min (point) (mark)))
			       (beginning-of-defun) (point)))
	(end   (save-excursion (goto-char (max (point) (mark)))
			       (end-of-defun)  (point))))
    (list start end)))

;;;###autoload
(defun troncle-trace-region (rstart rend)
  "Send top-level form point is in to troncle.emacs/trace-region
via nrepl protocol.  The form is re-compiled, with all evaluable
forms in the current emacs region (between (point) and mark) are
instrumented wiht tracing.  Then
troncle.traces/trace-execution-function is executed.  (See
troncle-set-exec-var for a way to set this.)
"
  (interactive "r")
  (save-excursion
    (let* ((defun-region (troncle-toplevel-region-for-region))
	   (dstart (car defun-region)) (dend (car (cdr defun-region)))
	   (fn (buffer-file-name)))
      (nrepl-send-op "trace-region"
		     ;; nrepl-send-op can only handle strings
		     (list "source" (buffer-substring-no-properties
				     dstart dend)
			   "source-region" (str (cons fn defun-region))
			   "trace-region" (str (list fn rstart rend)))
		     (troncle-op-handler (current-buffer))))))

(defun troncle-discover-choose-var (ns)
  "Choose a var to be executed when forms are sent for tracing
instrumentation with troncle-trace-region.  The var must be a fn
which takes no arguments."
  (let ((troncle-discover-var nil)) ; poor man's promises
    (nrepl-ido-read-var (or ns "user")
                        (lambda (var) (setq troncle-discover-var var)))
    ;; async? more like ehsync.
    (while (not troncle-discover-var)
      (sit-for 0.01))
    (concat nrepl-ido-ns "/" troncle-discover-var)))

;;;###autoload
(defun troncle-set-exec-var ()
  (interactive)
  (nrepl-send-op "set-exec-var"
		 (list "var" (troncle-discover-choose-var
			      (clojure-find-ns)))
		 (troncle-op-handler (current-buffer))))


(eval-after-load 'clojure-mode
  '(progn
     (define-key clojure-mode-map (kbd "C-c t R") 'troncle-trace-region)
     (define-key clojure-mode-map (kbd "C-c t E") 'troncle-set-exec-var)))

(provide 'troncle)

;;; troncle.el ends here
