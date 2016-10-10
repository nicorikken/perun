;   Copyright (c) 2016 Nico Rikken nico@nicorikken.eu
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns io.perun.contrib.asciidoctor
  "AsciidoctorJ based converter from Asciidoc to HTML."
  (:require [io.perun.core     :as perun]
            [clojure.java.io   :as io]
            [clojure.string    :as str]
            [clj-yaml.core     :as yaml]
            [io.perun.markdown :as md]
            [boot.core         :as boot])
  (:import [org.asciidoctor Asciidoctor Asciidoctor$Factory]))

(defn keywords->names
  "Converts a map with keywords to a map with named keys. Only handles the top
   level of any nested structure."
  [m]
  (reduce-kv #(assoc %1 (name %2) %3) {} m))

(defn names->keywords
  "Converts a map with named keys to a map with keywords. Only handles the top
   level of any nested structure."
   [m]
   (reduce-kv #(assoc %1 (keyword %2) %3) {} m))

(defn normalize-options
  "Takes the options for the Asciidoctor parser and puts the in the format
   appropriate for handling by the downstream functions. Mostly to better suit
   the parsing by the AsciidoctorJ library."
  [clj-opts]
  (let [atr  (-> (:attributes clj-opts)
                 (keywords->names)
                 (java.util.HashMap.))
        opts (assoc clj-opts :attributes atr)]
    (keywords->names opts)))

(defn base-dir
  "Derive the `base_dir` from the meta-data, as a basis for links and inclusions
   but also for image generation. The regex will filter out the last part of the
   file path, after the last slash (`/`) to get back the base_dir."
  [full-path]
  (get (re-matches #"(.*\/)[^\/]+" full-path) 1))

(defn extract-meta
  "Extract the above YAML metadata (front-matter) from the head of the file.
   It returns a map with the `:meta` and the `:asciidoc` content. The `:meta`
   key contains a map of the metadata, or a `nil` if the extraction or parsing
   failed. The `:asciidoc` key contains a string of the remaining Asciidoc
   content.

   This function prevents the need to rely on the `skip-front-matter` option in
   the AsciidoctorJ conversion process."
  [content]
  (let [first-line   (first (drop-while str/blank? (str/split-lines content)))
        start?       (= "---" first-line)
        splitted     (str/split content #"---\n" 3)
        finish?      (> (count splitted) 2)]
    (if (and start? finish?)
      ;; metadata was found, try to parse it
      (let [;metadata-str (nth splitted 1)
            ;adoc-content (nth splitted 2)]
            metadata-str (get splitted 1)
            adoc-content (get splitted 2)]
        (if-let [parsed-yaml (md/normal-colls (yaml/parse-string metadata-str))]
          ;; yaml parsing succeeded, return the map
          {:meta (assoc parsed-yaml :original true)
           :asciidoc adoc-content}
          ;; yaml parsing failed, return only the adoc-content
          {:meta nil
           :asciidoc adoc-content}))
      ;; no metadata found, return the original content
      {:meta nil
       :asciidoc content})))

(defn new-adoc-container
  "Creates a new AsciidoctorJ (JRuby) container, based on the normalized options
   provided."
  [options]
  (let [n-opts (normalize-options options)
        acont  (Asciidoctor$Factory/create (str (get n-opts "gempath")))]
    (perun/report-info "asciidoctor" "new-adoc-container options %s" (str n-opts))
    (doto acont (.requireLibraries (into '() (get n-opts "libraries"))))))

(defn perunize-meta
  [meta]
  "Add duplicate entries for the metadata keys gathered from the AsciidoctorJ
   parsing using keys that adhere to the Perun specification of keys. The native
   AsciidoctorJ keys are still available for reference and debugging."
  (merge meta {:author-email  (:email     meta)
               :name          (:doctitle  meta)
               :date-build    (:localdate meta)
               :date-modified (:docdate   meta)}))

(defn parse-file-metadata
  "Read the asciidoc content and derive relevant metadata for use in other Perun
   tasks. The document is read in its entirety (.readDocumentStructure instead
   of .readDocumentHeader) to have the results of the options reflected into the
   resulting metadata. As the document is rendered again, the time-based
   attributes will vary from the asciidoc-to-html convertion (doctime,
   docdatetime, localdate, localdatetime, localtime)."
  [container adoc-content frontmatter n-opts]
  (let [attributes  (->> (.readDocumentStructure container adoc-content n-opts)
                         (.getHeader)
                         (.getAttributes)
                         (into {})
                         (names->keywords))]
    (merge frontmatter (perunize-meta attributes))))

(defn asciidoc-to-html
  "Converts a given string of asciidoc into HTML. The normalized options that
   can be provided, influence the behavior of the conversion."
  [container adoc-content n-opts]
  (perun/report-info "asciidoctor" "asciidoc-to-html options %s" (str n-opts))
  (perun/report-info "asciidoctor" "asciidoc-to-html attributes type %s" (type (get (str n-opts) "attributes")))
  (.convert container adoc-content n-opts))

(defn process-file
  "Parses the content of a single file and associates the available metadata to
   the resulting html string. The HTML conversion is dispatched."
  [container file options]
  (perun/report-debug "asciidoctor" "processing asciidoc" (:filename file))
  (let [basedir      {:base_dir (base-dir (:full-path file))}
        opts         (merge-with merge options {:attributes {:base_dir (base-dir (:full-path file))}})
        n-opts       (normalize-options opts)
        file-content (-> file :full-path io/file slurp)
        extraction   (extract-meta file-content)
        adoc-content (:asciidoc extraction)
        frontmatter  (:meta extraction)
        ad-metadata  (parse-file-metadata container adoc-content frontmatter n-opts)
        html         (asciidoc-to-html container adoc-content n-opts)]
    ;; (println "(tmp-file file)" (boot/tmp-file file))
    (perun/report-info "asciidoctor" "process-file options %s" (str options))
    (perun/report-info "asciidoctor" "process-file opts %s" (str opts))
    (spit (str (:base_dir basedir) "test-basedir.adoc") "= test\nNico\nHi there\n")
    ;(boot/add-resource (str basedir "test-basedir.adoc")) ;TODO, only commit in perun.clj
    (merge ad-metadata {:content html} file)))
;; TODO get 'skip-front-matter' attribute working to avoid the extract-meta call

(defn parse-asciidoc
  "The main function of `io.perun.contrib.asciidoctor`. Responsible for parsing
   all provided asciidoc files. The actual parsing is dispatched. It accepts a
   boot fileset and a map of options.

   The map of options typically includes an array of libraries and an array of
   attributes: {:libraries [] :attributes {}}. Libraries are loaded from the
   AsciidoctorJ project, and can be loaded specifically
   (\"asciidoctor-diagram/ditaa\") or more broadly (\"asciidoctor-diagram\").
   Attributes can be set freely, although a large set has been predefined in the
   Asciidoctor project to configure rendering options or set meta-data.

   This will create a new AsciidoctorJ (JRuby) container for parsing the given
   set of files. All the downstream operations on the files will use this
   container, preventing concurrent parsing. But the container creation and
   computing overhead is such that having a couple of AsciidoctorJ containers
   only makes sense for large or complex jobs, taking minutes rather than
   seconds."
  [asciidoc-files options]
  (let [container     (new-adoc-container options)
        updated-files (doall (map #(process-file container % options ) asciidoc-files))]
    (perun/report-info "asciidoctor" "parsed %s asciidoc files" (count asciidoc-files))
    (perun/report-info "asciidoctor" "options %s" (str options))
    (perun/report-info "asciidoctor" "imagesoutdir %s" (str (:imagesoutdir (:attributes options))))
    updated-files))

;; TODO
;; [x] Convert options in lowest functions (normalize-options)
;; [x] Get it working for build-dev, not only for tests
;; [ ] Make a tmpdir for the images and provide to the `parse-adciidoc`
;; [ ] Add image directories in the in- and output for tmpdir
;; [ ] Asume images defined from website root, and available from `resources`.
;;      Therefore set image root to be the tmpdir, than call `add-resource` on the dir.
;;      Or place iamges relative to each file, assuming the files will end up corectly.
;;      `imagesdir` would typically be `./images`, so relative to the file.

