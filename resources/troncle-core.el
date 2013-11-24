;; Clojure interaction logic

(require 'clojure-mode)

;; cider.el/nrepl.el compatibility.  Grr
(let ((prefix (if (featurep 'cider) "cider-" "nrepl-")))
 (dolist (var '("send-op"
		"current-ns"
		"ido-read-var"
		"ido-ns"))
   (let ((aliased-var (intern (concat prefix var)))
	 (alias-sym (intern (concat "troncle--" var))))
     (cond ((functionp aliased-var)
	    (defalias alias-sym aliased-var))
	   ((boundp aliased-var)
	    (defvaralias alias-sym aliased-var))
	   ('t (error "%S is not defined." aliased-var))))))

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

(defun troncle-toplevel-region-for-region ()
  "Get all top-level forms contained in or neighbouring region
between point and mark."
  (let ((start (save-excursion (goto-char (min (point) (mark)))
			       (beginning-of-defun) (point)))
	(end   (save-excursion (goto-char (max (point) (mark)))
			       (end-of-defun)  (point))))
    (list start end)))

(defun troncle-trace-region* (rstart rend)
  "Send top-level form point is in to troncle.emacs/trace-region
via nrepl protocol.  The form is re-compiled, with all evaluable
forms in the current emacs region (between (point) and mark) are
instrumented wiht tracing.  Then
troncle.traces/trace-execution-function is executed.  (See
troncle-set-exec-var for a way to set this.)"
  (save-excursion
    (let* ((defun-region (troncle-toplevel-region-for-region))
	   (dstart (car defun-region)) (dend (car (cdr defun-region)))
	   (fn (buffer-file-name)))
      (troncle--send-op "trace-region"
		     ;; *-send-op can only handle strings
		     (list "source" (buffer-substring-no-properties
				     dstart dend)
			   "source-region" (str (cons fn defun-region))
			   "trace-region" (str (list fn rstart rend)))
		     (troncle-op-handler (current-buffer))))))

;; As these functions proliferate, I will have to come up with a
;; better way of doing this wrapping.
(defun troncle-trace-region (rstart rend)
  (interactive "r")
  (troncle-load-elisp)
  (troncle-trace-region* rstart rend))

(defun troncle-discover-choose-var (ns exec-fn)
  "Choose a var to be executed when forms are sent for tracing
instrumentation with troncle-trace-region.  The var must be a fn
which takes no arguments. Invokes exec-fn with the fully
qualified var-name as string."
  (lexical-let ((exec-fn exec-fn))
    (troncle--ido-read-var (or ns "user")
                        (lambda (var)
                          (funcall exec-fn
                                   (concat troncle--ido-ns "/"
                                   var))))))

(defun troncle-send-var (opname)
  "Get user to choose a var to send to OPNAME"
  (lexical-let ((handler (troncle-op-handler (current-buffer)))
		(opname opname))
    (troncle-discover-choose-var
     (clojure-find-ns)
     (lambda (var)
       (troncle--send-op opname (list "var" var) handler)))))

;;;###autoload
(defun troncle-set-exec-var ()
  (interactive) (troncle-load-elisp) (troncle-send-var "set-exec-var"))

;;;###autoload
(defun troncle-toggle-trace-var ()
  (interactive) (troncle-load-elisp) (troncle-send-var "toggle-trace-var"))


(eval-after-load 'clojure-mode
  '(progn
     (define-key clojure-mode-map (kbd "C-c t R") 'troncle-trace-region)
     (define-key clojure-mode-map (kbd "C-c t E") 'troncle-set-exec-var)
     (define-key clojure-mode-map (kbd "C-c t V") 'troncle-toggle-trace-var)))

(provide 'troncle)
