;;; nrepl-discover.el --- Client to load commands from nrepl server

;; Copyright © 2013 Phil Hagelberg
;;
;; Author: Phil Hagelberg <technomancy@gmail.com>
;; URL: http://github.com/technomancy/nrepl-discover
;; Version: 0.0.1
;; Keywords: languages, lisp

;; This file is not part of GNU Emacs.

;;; Commentary:

;; This library is a client for Clojure nREPL servers which offer up
;; certain operations as commands which can be invoked client-side.

;; Upon running M-x nrepl-discover, it will query the connected server
;; for operations and use the return value to construct new
;; interactive defuns corresponding to each server-side operation
;; which prompt appropriately for the given types desired. They also
;; understand how to respond to certain predetermined editor-side
;; response types.

;;; License:

;; This program is free software; you can redistribute it and/or
;; modify it under the terms of the GNU General Public License
;; as published by the Free Software Foundation; either version 3
;; of the License, or (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with GNU Emacs; see the file COPYING.  If not, write to the
;; Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
;; Boston, MA 02110-1301, USA.

;;; Code:

(require 'nrepl)

;; copied from nrepl-make-response-handler because that's a monolithic ball
(defun nrepl-discover-status (status)
  (when (member "interrupted" status)
    (message "Evaluation interrupted."))
  (when (member "eval-error" status)
    (funcall nrepl-err-handler buffer ex root-ex session))
  (when (member "namespace-not-found" status)
    (message "Namespace not found."))
  (when (member "need-input" status)
    (nrepl-need-input buffer))
  (when (member "done" status)
    (remhash id nrepl-requests)))

(defun nrepl-discover-face (color)
  (let ((face-name (intern (concat "nrepl-discover-" color "-face"))))
    (when (not (symbol-file face-name 'defface))
      (custom-declare-face face-name `((default . (:background ,color)))
                           (concat "Face for nrepl " color " overlays")))
    face-name))

(defun nrepl-discover-overlay (overlay)
  (save-excursion
    ;; TODO: support optional file arg here
    (destructuring-bind (color line) overlay
      (goto-char (point-min))
      (forward-line (1- line))
      (let ((beg (point)))
        (end-of-line)
        (let ((overlay (make-overlay beg (point))))
          (overlay-put overlay 'face (nrepl-discover-face color))
          (when message
            (overlay-put overlay 'message message)))))))

(defun nrepl-discover-op-handler (buffer)
  (lexical-let ((buffer buffer))
    (lambda (response)
      (nrepl-dbind-response response (message ns out err status id ex root-ex
                                              session overlay clear-overlays
                                              text url reload)
        (when message
          (message message))
        (when text ; TODO: test
          (with-current-buffer (format "*nrepl-text*")
            (let ((inhibit-read-only t))
              (delete-region (point-min) (point-max))
              (insert text))
            (setq buffer-read-only t)))
        (when out
          (nrepl-emit-output buffer out t))
        (when err
          (nrepl-emit-output buffer err t))
        (when url
          (browse-url url))
        (when reload
          (let ((b (find-buffer-visiting reload)))
            (message "Found %s to revert." b)
            (when b
              (with-current-buffer b
                (revert-buffer)))))
        ;; TODO: support position
        ;; (with-current-buffer buffer
        ;;   (ring-insert find-tag-marker-ring (point-marker)))
        (when clear-overlays
          ;; TODO: support optional buffer arg
          (with-current-buffer buffer
            (remove-overlays)))
        (when overlay
          (with-current-buffer buffer
            (nrepl-discover-overlay overlay)))
        (when status
          (nrepl-discover-status status))))))

(defvar nrepl-discover-var nil)

(defun nrepl-discover-choose-var (ns)
  (let ((nrepl-discover-var nil)) ; poor man's promises
    (nrepl-ido-read-var (or ns "user")
                        (lambda (var) (setq nrepl-discover-var var)))
    ;; async? more like ehsync.
    (while (not nrepl-discover-var)
      (sit-for 0.01))
    (concat nrepl-ido-ns "/" nrepl-discover-var)))

(defun nrepl-discover-argument (arg)
  (list (car arg) (case (intern (cadr arg))
                    ;; we already have this implicit in nrepl msgs; needed here?
                    ('ns '(if current-prefix-arg
                              (read-from-minibuffer "Namespace: ")
                            (clojure-find-ns)))
                    ('region '(list buffer-file-name (point) (mark))) ; untested
                    ;; TODO: default to current defn
                    ('var '(nrepl-discover-choose-var (clojure-find-ns)))
                    ('file '(progn
                              (save-some-buffers)
                              (if current-prefix-arg ; untested
                                  (ido-read-file-name)
                                buffer-file-name)))
                    ('position '(format "%s:%s" buffer-file-name (point))) ; untested
                    ('list `(completing-read ,(or (nth 2 arg) ; untested
                                                  (concat (nth 0 arg) ": "))
                                             ,(nth 3 arg)))
		    ('region-contents
		     '(buffer-substring-no-properties (point) (mark)))
		    ('region-for-expression-contents
		     (apply 'buffer-substring-no-properties
			    (nrepl-region-for-expression-at-point)))
                    ;; TODO: eval type
                    (t `(read-from-minibuffer
                         ,(or (nth 2 arg)
                              (concat (nth 0 arg) ": ")))))))

(defun nrepl-discover-command-for (op)
  "Construct a defun command form for `OP'.

Argument should be an alist with \"name\", \"doc\", and \"args\" keys as per
the nrepl-discover docs."
  `(defun ,(intern (concat "nrepl-" (assoc-default "name" op))) ()
     ,(assoc-default "doc" op)
     (interactive)
     (nrepl-send-op ,(assoc-default "name" op)
                    (list ,@(mapcan 'nrepl-discover-argument
                                    (assoc-default "args" op)))
                    (nrepl-discover-op-handler (current-buffer)))))

(defvar nrepl-discovered-ops nil
  "List of ops discovered by the last `nrepl-discover' run.")

(defun nrepl-discover ()
  "Query nREPL server for operations and define Emacs commands for them."
  (interactive)
  (setq nrepl-discovered-ops nil)
  (nrepl-send-op "discover" ()
                 (nrepl-make-response-handler
                  (current-buffer)
                  (lambda (_ value)
		    (message (pp-to-string value))
                    (dolist (op value)
                      (when (not (string= "discover"
                                          (assoc-default "name" (cdr op))))
                        ;; for some reason the 'dict car needs to be stripped
                        (eval (nrepl-discover-command-for (cdr op)))))
                    (add-to-list 'nrepl-discovered-ops
                                 (assoc-default "name" (cdr op)))
                    (message "Loaded nrepl-discover commands: %s."
                             (mapconcat (lambda (op)
                                          (assoc-default "name" (cdr op)))
                                        ops ", ")))
                  nil nil nil nil)))

(provide 'nrepl-discover)
;;; nrepl-discover.el ends here
