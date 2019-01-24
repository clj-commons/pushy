(defproject clj-commons/pushy "0.3.10"
  :description "HTML5 pushState for Clojurescript"
  :url "https://github.com/clj-commons/pushy"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.494" :scope "provided"]]

  :aliases {"test"   ["do" "clean," "doo" "phantom" "test" "once"]}


  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :source-paths ["src"]

  :profiles {:dev {:dependencies [[secretary "1.2.3"]]
                   :plugins      [[lein-cljsbuild "1.1.7"]
                                  [lein-doo "0.1.10"]]

                   :cljsbuild
                   {:test-commands
                    {"unit" ["phantomjs" :runner
                             "target/unit-test.js"]}
                    :builds
                    {:test {:source-paths ["src" "test"]
                            :compiler     {:output-to     "target/unit-test.js"
                                           :main          pushy.test.runner
                                           :optimizations :whitespace
                                           :pretty-print  true}}}}}})
