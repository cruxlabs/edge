((nil
  (cider-refresh-before-fn . "dev-extras/suspend")
  (cider-refresh-after-fn  . "dev-extras/resume")
  (cider-repl-init-code . ("(dev)"))
  (cider-clojure-cli-global-options . "-A:dev{{#kick}}:build:dev/build{{/kick}}"){{#cljs}}
  (cider-default-cljs-repl . edge)
  (cider-cljs-repl-types . ((edge "(user/cljs-repl)"))){{/cljs}}))
