(set-env!
  :source-paths #{"src", "test"}
  :resource-paths #{"resources"}
  :dependencies '[[boot/core "2.6.0" :scope "provided"]
                  ; [adzerk/bootlaces "0.1.13"]
                  [org.pegdown/pegdown "1.6.0"]
                  [circleci/clj-yaml "0.5.5"]
                  [time-to-read "0.1.0"]
                  [sitemap "0.2.5"]
                  [clj-rss "0.2.3"]
                  [gravatar "1.1.1"]
                  [clj-time "0.12.0"]
                  [mvxcvi/puget "1.0.0"]
                  [com.novemberain/pantomime "2.8.0"]
                  [pandeiro/boot-http "0.7.0"]
                  [org.asciidoctor/asciidoctorj "1.5.4"]
                  [org.asciidoctor/asciidoctorj-diagram "1.5.0"]
                  [proto-repl "0.3.1"]
                  [adzerk/boot-test "1.1.2"]])

(require '[io.perun :refer :all]
         '[io.perun.example.post :as post-view]
         '[io.perun.example.index :as index-view]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-test :refer :all])
; (require '[adzerk.bootlaces :refer :all])

(def +version+ "0.4.0-SNAPSHOT")

; (bootlaces! +version+)

(task-options!
  ; aot {:all true}
  ; push {:ensure-branch  "master"
  ;       :ensure-clean   false
  ;       :ensure-version +version+}
  pom {:project 'perun
       :version +version+
       :description "Static site generator build with Clojure and Boot"
       :url         "https://github.com/hashobject/perun"
       :scm         {:url "https://github.com/hashobject/perun"}
       :license     {"name" "Eclipse Public License"
                     "url"  "http://www.eclipse.org/legal/epl-v10.html"}})


; (deftask release-snapshot
;   "Release snapshot"
;   []
;   (comp (build-jar) (push-snapshot)))

(deftask build-dev
  "Build blog dev version"
  []
  (comp ;(base)
        (global-metadata)
        (markdown)
        (asciidoctor)
        (print-meta)
        ;(draft)
        (ttr)
        (slug)
        (permalink)
        (canonical-url)
        (render :renderer 'io.perun.example.post/render)
        (collection :renderer 'io.perun.example.index/render :page "index.html" :filterer identity)
        ))
;
; (deftask dev
;   "Dev process"
;   []
;   (comp
;     (watch)
;     (repl :server true)
;     (pom)
;     (jar)
;     (install)))

(deftask dev
  []
  (comp (watch)
        (build-dev)
        (serve :resource-root "public")))

; (deftask dev [] nil)
