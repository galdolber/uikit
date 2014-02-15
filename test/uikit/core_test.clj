(ns uikit.core-test
  (:require [clojure.test :refer :all]
            [uikit.core :refer :all]))

(deftest test-resolve-constraint
  (doseq [[k v] layout-constraints]
    (are [s] (= v (resolve-constraint s))
         k
         (name k)
         v)))

(deftest test-constraint-not-found
  (are [s] (thrown? Exception (resolve-constraint s))
       :bla
       -33))

(deftest test-parse-constraint
  (are [s e] (= e (parse-constraint s))
       "C:button.top=main.centery"
       ["button-top" "button" 3 0 "main" 10 1.0 0.0]
       
       "C:button.top=main.centery 0.5"
       ["button-top" "button" 3 0 "main" 10 0.5 0.0]

       "C:button.top=main.centery 0.5 10"
       ["button-top" "button" 3 0 "main" 10 0.5 10]

       "C:button.top=nil.nil 1 30"
       ["button-top" "button" 3 0 "nil" 0 1 30]))

(deftest test-native-constraints
  (are [a] (= a (parse-constraint a))
       "V:|[button]-[panel]"
       "H:|-[main]-|"))

(deftest test-invalid-constraint
  (is (thrown? Exception (parse-constraint "C:button.top=main"))))

(deftest test-create-ui
  ;; Fake constrols
  (let [uiview 1
        uibutton 2
        red 3
        uitextfield 4
        scope (create-scope)
        ui (create-ui
            scope
            [uiview :main
             {:constraints
              ["V:|[accept(30)]"
               "H:|[accept]|"
               "H:|[cancel]|"
               "C:cancel.height=accept.height"]}
             [uitextfield :input
              {:events {:UITextFieldTextDidChangeNotification (fn [scope])}}]
             [uibutton :accept
              {:setTitle:forState ["Accept" 0]
               ;; Use a vector to use the same event type multiple times
               :events [:UITextFieldTextDidChangeNotification (fn [scope])
                        :UITextFieldTextDidChangeNotification (fn [scope])]
               :gestures {:UITapGestureRecognizer (fn [scope])
                          :UIGestureWithMap {:setSomething 1
                                             :handler (fn [scope])}}
               :setBackgroundColor red}]
             [uibutton :cancel
              {:setTitle:forState ["Cancel" 0]
               ;; Use a vector to use the same gesture type multiple times
               :gestures [:UITapGestureRecognizer (fn [scope])
                          :UITapGestureRecognizer (fn [scope])]}]])]
    (is (= 8 (count @(:retains @scope))))
    (is (= 3 (count @(:observers @scope))))
    (is (= uiview (:main @scope)))
    (is (= uitextfield (:input @scope)))
    (is (= uibutton (:accept @scope)))
    (is (= uibutton (:cancel @scope)))))
