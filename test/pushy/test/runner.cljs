(ns pushy.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [pushy.test.core]))


(doo-tests 'pushy.test.core)
