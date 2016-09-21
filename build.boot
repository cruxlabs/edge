;; Copyright © 2016, JUXT LTD.

;; A complete development environment for websites in Clojure and
;; ClojureScript.

;; Most users will use 'boot dev' from the command-line or via an IDE
;; (e.g. CIDER).

;; See README.md for more details.

(require '[clojure.java.shell :as sh])

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?]
        (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                          (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags" "--match" "[0-9].*"))))]
    (cond
      dirty? (str (next-version version) "-" hash "-dirty")
      (pos? (Long/parseLong commits)) (str (next-version version) "-" hash)
      :otherwise version)))

(def project "edge")
(def version (deduce-version-from-git))

(set-env!
 :source-paths #{"sass" "src"}
 :resource-paths #{"resources"}
 :asset-paths #{"assets"}
 :dependencies
 '[[adzerk/boot-cljs "1.7.228-1" :scope "test"]
   [adzerk/boot-cljs-repl "0.3.2" :scope "test"]
   [adzerk/boot-reload "0.4.11" :scope "test"]
   [weasel "0.7.0" :scope "test"] ;; Websocket Server
   [deraen/boot-sass "0.2.1" :scope "test"]
   [reloaded.repl "0.2.1" :scope "test"]

   [org.clojure/clojure "1.9.0-alpha12"]
   [org.clojure/clojurescript "1.9.229"]

   [org.clojure/tools.nrepl "0.2.12"]

   ;; Needed for start-repl in cljs repl
   [com.cemerick/piggieback "0.2.1" :scope "test"]

   ;; Server deps
   [aero "1.0.0"]
   [bidi "2.0.10"]
   [com.stuartsierra/component "0.3.1"]
   [hiccup "1.0.5"]
   [org.clojure/tools.namespace "0.2.11"]
   [prismatic/schema "1.0.4"]
   [selmer "1.0.4"]
   [yada "1.1.35" :exclusions [aleph manifold ring-swagger prismatic/schema]]

   [aleph "0.4.2-alpha8"]
   [manifold "0.1.6-alpha1"]
   [metosin/ring-swagger "0.22.10"]
   [prismatic/schema "1.1.3"]

   ;; App deps
   [reagent "0.6.0-rc"]
   [com.cognitect/transit-clj "0.8.285"]
   ;;[com.cognitect/transit-cljs "0.8.239"]

   ;; Logging
   [org.clojure/tools.logging "0.3.1"]
   [org.slf4j/jcl-over-slf4j "1.7.21"]
   [org.slf4j/jul-to-slf4j "1.7.21"]
   [org.slf4j/log4j-over-slf4j "1.7.21"]
   [ch.qos.logback/logback-classic "1.1.5"
    :exclusions [org.slf4j/slf4j-api]]])

(require '[adzerk.boot-cljs :refer [cljs]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
         '[adzerk.boot-reload :refer [reload]]
         '[deraen.boot-sass :refer [sass]]
         '[com.stuartsierra.component :as component]
         'clojure.tools.namespace.repl
         '[edge.system :refer [new-system]])

(def repl-port 5600)

(task-options!
 repl {:client true
       :port repl-port}
 pom {:project (symbol project)
      :version version
      :description "A complete Clojure project you can leap from"
      :license {"The MIT License (MIT)" "http://opensource.org/licenses/mit-license.php"}}
 aot {:namespace #{'edge.main}}
 jar {:main 'edge.main
      :file (str project "-" version "-standalone.jar")})

(deftask dev-system
  "Develop the server backend. The system is automatically started in
  the dev profile."
  []
  (require 'reloaded.repl)
  (let [go (resolve 'reloaded.repl/go)]
    (try
      (require 'user)
      (go)
      (catch Exception e
        (boot.util/fail "Exception while starting the system\n")
        (boot.util/print-ex e))))
  identity)

(deftask dev
  "This is the main development entry point."
  []
  (set-env! :dependencies #(vec (concat % '[[reloaded.repl "0.2.1"]])))
  (set-env! :source-paths #(conj % "dev"))

  ;; Needed by tools.namespace to know where the source files are
  (apply clojure.tools.namespace.repl/set-refresh-dirs (get-env :directories))

  (comp
   (watch)
   (speak)
   (sass :output-style :expanded)
   (reload :on-jsload 'edge.main/init)
   (cljs-repl :nrepl-opts {:client false
                           :port repl-port
                           :init-ns 'user}) ; this is also the server repl!
   (cljs :ids #{"edge"} :optimizations :none)
   (dev-system)
   (target)))

(deftask static
  "This is used for creating optimized static resources under static"
  []
  (comp
   (sass :output-style :compressed)
   (cljs :ids #{"edge"} :optimizations :advanced)))

(deftask build
  []
  (comp
   (static)
   (target :dir #{"static"})))

(defn- run-system [profile]
  (println "Running system with profile" profile)
  (let [system (new-system profile)]
    (component/start system)
    (intern 'user 'system system)
    (with-pre-wrap fileset
      (assoc fileset :system system))))

(deftask run [p profile VAL kw "Profile"]
  (comp
   (repl :server true
         :port (case profile :prod 5601 :beta 5602 5600)
         :init-ns 'user)
   (run-system (or profile :prod))
   (wait)))

(deftask uberjar
  "Build an uberjar"
  []
  (comp
   (static)
   (aot)
   (pom)
   (uber)
   (jar)
   (target)))

(deftask aws
  "Call out to AWS"
  []
  (dosh "aws" "help"))

(def environment-name (str project "-prod"))
(def aws-region "eu-west-1")
(def aws-account-id "247806367507")
(def zipfile (format "edge-aws-ebs-upload-%s.zip" version))

(deftask create-application
  "Create AWS Beanstalk application and environment, only call this once."
  []
  (println "Creating application:" project)
  (dosh "aws" "elasticbeanstalk" "create-application"
        "--application-name" project)
  (println "Creating environment:" project environment-name)
  (dosh "aws" "elasticbeanstalk" "create-environment"
        "--application-name" project
        "--environment-name" environment-name
        "--cname-prefix" environment-name
        "--solution-stack-name" "64bit Amazon Linux 2016.03 v2.1.6 running Docker 1.11.2"))

(deftask docker "Create a zip" []
  (with-pre-wrap fileset
    (dosh "zip"
          (str "target/" zipfile)
          "Dockerfile"
          (str "target/" project "-" version "-standalone.jar"))
    fileset))

(deftask deploy "Deploy application to beanstalk environment" []
  (comp
   (println "Building zip file:" zipfile)
   (dosh "zip"
          (str "target/" zipfile)
          "Dockerfile"
          (str "target/" project "-" version "-standalone.jar"))
   (println "Uploading zip file to S3:" zipfile)
   (dosh "aws" "s3" "cp" (str "target/" zipfile)
         (format "s3://elasticbeanstalk-%s-%s/%s" aws-region aws-account-id zipfile))
   (println "Creating application version:" version)
   (dosh "aws" "elasticbeanstalk" "create-application-version"
         "--application-name" project
         "--version-label" version
         "--source-bundle" (format "S3Bucket=elasticbeanstalk-%s-%s,S3Key=%s" aws-region aws-account-id zipfile))
   (println "Updating environment:" environment-name "->" version)
   (dosh "aws" "elasticbeanstalk" "update-environment"
         "--application-name" project
         "--environment-name" environment-name
         "--version-label" version)
   (println "Done")
   identity))

(deftask show-version "Show version" [] (println version))
