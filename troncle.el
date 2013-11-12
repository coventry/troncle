(require 'clojure-mode)
(require 'nrepl-discover)

(nrepl-discover)

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
		     (nrepl-discover-op-handler (current-buffer))))))

(provide 'troncle)

