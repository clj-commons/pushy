(defproject lifecheq/pushy "0.3.9-SNAPSHOT"
  :description "HTML5 pushState for Clojurescript"
  :url "https://github.com/lifecheq/pushy"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.9.494" :scope "provided"]]

  :aliases {"deploy" ["do" "clean," "deploy" "clojars"]
            "test"   ["do" "clean," "with-profile" "dev" "cljsbuild" "test"]}

  :lein-release {:deploy-via :shell
                 :shell      ["lein" "deploy"]}

  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]]

  :source-paths ["src"]

  :profiles {:dev {:dependencies [[secretary "1.2.1"]]
                   :plugins      [[lein-cljsbuild "1.1.5"]
                                  [com.cemerick/clojurescript.test "0.3.3"]]

                   :cljsbuild
                   {:test-commands
                    {"unit" ["phantomjs" :runner
                             "target/unit-test.js"]}
                    :builds
                    {:test {:source-paths ["src" "test"]
                            :compiler     {:output-to     "target/unit-test.js"
                                           :optimizations :whitespace
                                           :pretty-print  true}}}}}})
