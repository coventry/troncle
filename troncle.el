(require 'clojure-mode)
(require 'nrepl)

(defun map->assoc (m)
  "Given a (key val ...) list as returned by nrepl, return an
  association list."
  (apply 'format-spec-make m))

;; Initialize clojure machinery.  This needs to be synchronous so we
;; know it's finished before anyone tries to use it.
(let ((resp (nrepl-send-string-sync "(require 'troncle.emacs)" "user")))
  (if (assoc-default ':stderr (map->assoc resp))
      (error "Could not load troncle on clojure server side.")))

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

(defun troncle-trace-region (rstart rend)
  "See docstring for nrepl-trace-region"
  (interactive "r")
  (save-excursion
    (let* ((defun-region (nrepl-region-for-expression-at-point))
	   (dstart (car defun-region)) (dend (car (cdr defun-region)))
	   (fn (buffer-file-name)))
      (nrepl-send-op "trace-region"
		     ;; nrepl-send-op can only handle strings
		     (list "source" (buffer-substring-no-properties
				     dstart dend)
			   "source-region" (str (cons fn defun-region))
			   "trace-region" (str (list fn rstart rend)))
		     (troncle-op-handler (current-buffer))))))

(define-key clojure-mode-map (kbd "C-c t R") 'troncle-trace-region)

(provide 'troncle)
