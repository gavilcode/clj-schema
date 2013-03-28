(ns architect.validation-test
  (:use clojure.test
        architect.blueprint
        architect.validation
        architect.test-blueprints)
  (:import clojure.lang.Keyword))


(defn first-name-bob? [x]
  (-> x :name :first (= "Bob")))

(deftest test-validation-errors
  (are [blueprint m errors] (= errors (validation-errors (if (blueprint? blueprint) blueprint (map-blueprint :strict blueprint)) m))

;;;; Degenerate cases

       nil             {}          #{}
       []              {}          #{}
       nil             nil         #{}
       []              nil         #{}

       family-blueprint [[:a] 2 [:b] 4] #{"Constraint failed: '[:or nil? map?]'"}

       ;;

;;;; One simple-blueprint per path

       [[:a] number?]
       {:a 1}
       #{}

       [[:b] Integer]
       {}
       #{"Map did not contain expected path [:b]."}

       [[:bb] Integer]
       {:bb "bb"}
       #{"Expected value \"bb\", at path [:bb], to be an instance of class java.lang.Integer, but was java.lang.String"}

       [[:b :c] Integer]
       {:b "a"}
       #{"Path [:b] was not specified in the blueprint."
         "Map did not contain expected path [:b :c]."}

       [[:b :c] Integer]
       {:b {:c :a}}
       #{"Expected value :a, at path [:b :c], to be an instance of class java.lang.Integer, but was clojure.lang.Keyword"}

       ;;

;;;; Multiple predicates per path - lists all failures, not just first

       [[:a] [number? pos?]]
       {:a 1}
       #{}

       [[:b] [String Keyword]]
       {}
       #{"Map did not contain expected path [:b]."}

       [[:b] [String Keyword]]
       {:b 1.1}
       #{"Expected value 1.1, at path [:b], to be an instance of class java.lang.String, but was java.lang.Double"
         "Expected value 1.1, at path [:b], to be an instance of class clojure.lang.Keyword, but was java.lang.Double"}

       [[:b :c] [number? pos?]]
       {:b "a"}
       #{"Path [:b] was not specified in the blueprint."
         "Map did not contain expected path [:b :c]."}

       [[:b :c] [String Keyword]]
       {:b {:c 1.1}}
       #{"Expected value 1.1, at path [:b :c], to be an instance of class java.lang.String, but was java.lang.Double"
         "Expected value 1.1, at path [:b :c], to be an instance of class clojure.lang.Keyword, but was java.lang.Double"}

       ;;

;;;; Handles combo of missing paths and erroneous values
       [[:b :c] [number? pos?]
        [:b :d] identity
        [:x :y] [neg? number?]
        [:z :z :top] keyword?]
       {:b {:c "a" :d :foo}
        :x {:y 99}}
       #{"Value \"a\", at path [:b :c], did not match predicate 'number?'."
         "Value \"a\", at path [:b :c], did not match predicate 'pos?'."
         "Map did not contain expected path [:z :z :top]."
         "Value 99, at path [:x :y], did not match predicate 'neg?'." }

;;;; when using 'sequence-of' blueprint is applied against each element in the sequence at that key
       [[:a] (sequence-of person-blueprint)]
       {:a [{:name {:first "Roberto"} :height 11} {:name {:first "Roberto"} :height "11"}]}
       #{"Expected value \"11\", at path [:a 1 :height], to be an instance of class java.lang.Number, but was java.lang.String"}

;;;; if the blueprint path isn't present, and using a blueprint the seq, we get a "not present" error
       [[:a] (sequence-of person-blueprint)]
       {:not-a [{:name {:first "Roberto"} :height 34} {:name {:first "Roberto"} :height "34"}]}
       #{"Map did not contain expected path [:a]."
         "Path [:not-a] was not specified in the blueprint."}

;;;; if an optional blueprint path isn't present - no error messages
       [(optional-path [:a]) (sequence-of person-blueprint)]
       {:not-a [{:name {:first "Roberto"} :height 91} {:name {:first "Roberto"} :height "91"}]}
       #{"Path [:not-a] was not specified in the blueprint."}

;;;; `optional-path` has no effect if the blueprint path is present
       [(optional-path [:a]) (sequence-of person-blueprint)]
       {:a [{:name {:first "Roberto"} :height 70} {:name {:first "Roberto"} :height "70"}]}
       #{"Expected value \"70\", at path [:a 1 :height], to be an instance of class java.lang.Number, but was java.lang.String"}

;;;; you can mix types of simple-blueprints: preds with blueprints
       [[:a] (sequence-of [first-name-bob? person-blueprint])]
       {:a [{:name {:first "Roberto"} :height 44} {:name {:first "Chris"} :height "4a"}]}
       #{"Value {:name {:first \"Roberto\"}, :height 44}, at path [:a 0], did not match predicate 'architect.validation-test/first-name-bob?'."
         "Expected value \"4a\", at path [:a 1 :height], to be an instance of class java.lang.Number, but was java.lang.String"
         "Value {:name {:first \"Chris\"}, :height \"4a\"}, at path [:a 1], did not match predicate 'architect.validation-test/first-name-bob?'."}

;;;; ...  multiple strict blueprints together makes little sense - one blueprint will think extra keys were not specified by it, though they were by the other blueprint
       [[:a] (sequence-of [name-blueprint person-blueprint])]
       {:a [{:name {:first :Roberto} :height 69} {:name {:first "Roberto"} :height "69"}]}
       #{"Path [:a 0 :height] was not specified in the blueprint."
         "Path [:a 1 :height] was not specified in the blueprint."
         "Expected value \"69\", at path [:a 1 :height], to be an instance of class java.lang.Number, but was java.lang.String"
         "Expected value :Roberto, at path [:a 0 :name :first], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}

;;;; blueprint on right of blueprint, can be made an or, and can work with both preds and blueprints mixed
       [[:a] [:or nil? person-blueprint]]
       {:a nil}
       #{}

;;;; you can have just one thing in the ':or' - but please don't it is weird
       [[:a] (sequence-of [:or person-blueprint])]
       {:a [{:name {:first "Roberto"} :height "76"}]}
       #{"Expected value \"76\", at path [:a 0 :height], to be an instance of class java.lang.Number, but was java.lang.String"}

;;;; when both :or options fail - see errors for both 'nil?' and 'person-blueprint'
       [[:a] [:or nil? person-blueprint]]
       {:a {:name {:first "Roberto"} :height "66"}}
       #{"Expected value \"66\", at path [:a :height], to be an instance of class java.lang.Number, but was java.lang.String" "Value {:name {:first \"Roberto\"}, :height \"66\"}, at path [:a], did not match predicate 'nil?'."}

;;;; or collects all failures in the sequence being checked
       [[:a] (sequence-of [:or nil? person-blueprint])]
       {:a [{:name {:first "Roberto"} :height "88"} {:name {:first "Roberto"} :height 88}]}
       #{"Expected value \"88\", at path [:a 0 :height], to be an instance of class java.lang.Number, but was java.lang.String" "Value {:name {:first \"Roberto\"}, :height \"88\"}, at path [:a 0], did not match predicate 'nil?'."}

;;;; nested blueprints - no errors
       [[:a :family] family-blueprint]
       {:a {:family {:mom {:name {:first "Theresa"}
                           :height 42}
                     :dad {:name {:first "Stanley"}
                           :height 53}}}}
       #{}

;;;; nested blueprints - with errors
       [[:a :family] family-blueprint]
       {:a {:family {:mom {:name {:first :Theresa}
                           :height 42}
                     :dad {:name {:first "Stanley"}
                           :height :53}}}}
       #{"Expected value :Theresa, at path [:a :family :mom :name :first], to be an instance of class java.lang.String, but was clojure.lang.Keyword"
         "Expected value :53, at path [:a :family :dad :height], to be an instance of class java.lang.Number, but was clojure.lang.Keyword"}

;;;; strict blueprints fail if there are more keys than specified
       [[:a :family] family-blueprint]
       {:a {:family {:mom {:name {:first "Theresa"
                                  :last "Greepostalla"}
                           :height 42
                           :favorite-book "Twilight"}
                     :child "David"
                     :dad {:name {:first "Stanley"
                                  :middle "Roberto-Gustav"}
                           :height 53
                           :favorite-sport "Fishing"}}
            :house "Large"}
        :b {:car "Honda Accord"}}
       #{"Path [:b :car] was not specified in the blueprint."
         "Path [:a :family :mom :favorite-book] was not specified in the blueprint."
         "Path [:a :family :dad :name :middle] was not specified in the blueprint."
         "Path [:a :family :child] was not specified in the blueprint."
         "Path [:a :house] was not specified in the blueprint."
         "Path [:a :family :mom :name :last] was not specified in the blueprint."
         "Path [:a :family :dad :favorite-sport] was not specified in the blueprint."}

;;;; nested loose blueprints don't cause extra path errors for their paths, even if inside a surrounding strict blueprint
       [[:a :family] mom-strict-dad-loose-family-blueprint]
       {:a {:family {:mom {:name {:first "Theresa"
                                  :last "Greepostalla"}
                           :height 42
                           :favorite-book "Twilight"}
                     :child "David"
                     :dad {:name {:first "Stanley"
                                  :middle "Roberto-Gustav"}
                           :height 53
                           :favorite-sport "Fishing"}} ;; Dad's loose so this extra key causes no problems
            :house "Large"}
        :b {:car "Honda Accord"}}
       #{"Path [:b :car] was not specified in the blueprint."
         "Path [:a :family :mom :favorite-book] was not specified in the blueprint."
         "Path [:a :family :child] was not specified in the blueprint."
         "Path [:a :house] was not specified in the blueprint."
         "Path [:a :family :mom :name :last] was not specified in the blueprint."}

;;;; stops looking for extra paths at validated path ends
       [[:a :family] map?]
       {:a {:family {:mom {:name {:first "Theresa"
                                  :last "Greepostalla"}
                           :height 42
                           :favorite-book "Twilight"}
                     :child "David"
                     :dad {:name {:first "Stanley"
                                  :middle "Roberto-Gustav"}
                           :height 53
                           :favorite-sport "Fishing"}} ;; Dad's loose so this extra key causes no problems
            :house "Large"}
        :b {:car "Honda Accord"}}
       #{"Path [:b :car] was not specified in the blueprint."
         "Path [:a :house] was not specified in the blueprint."}

;;;; marked as 'sequence-of' but only one value - causes an error
       [[:a] (sequence-of person-blueprint)]
       {:a {:name {:first "Roberto"} :height "76"}}
       #{"At parent path [:a], constraint failed: '[:or nil? sequential?]'"}

;;;; using 'sequence-of' with an 'Number' class - means there is a seq of numbers
       [[:a] (sequence-of Number)]
       {:a 1}
       #{"At parent path [:a], constraint failed: '[:or nil? sequential?]'"}

;;;; nil is an acceptable value for a 'sequence-of' blueprint
       [[:a] (sequence-of integer?)]
       {:a nil}
       #{}

;;;; sequence-of can be used from within other nested simple-blueprints -- [:a] is single item
       [[:a] [:or Number (sequence-of Number)]]
       {:a 4}
       #{}

;;;; ... <continued from above> -- [:a] is sequential
       [[:a] [:or Number (sequence-of Number)]]
       {:a [4 5 6 7]}
       #{}

;;;; marked as 'set-of' but only one value - causes an error
       [[:a] (set-of person-blueprint)]
       {:a {:name {:first "Roberto"} :height "76"}}
       #{"At parent path [:a], constraint failed: '[:or nil? set?]'"}

;;;; using 'set-of' with an 'Number' predicate - means there is a set of numbers
       [[:a] (set-of Number)]
       {:a 1}
       #{"At parent path [:a], constraint failed: '[:or nil? set?]'"}

;;;; nil is an acceptable value for a 'set-of' blueprint
       [[:a] (set-of integer?)]
       {:a nil}
       #{}

;;;; set-of can be used from within other nested simple-blueprints -- #{:a} is single item
       [[:a] [:or Number (set-of Number)]]
       {:a 4}
       #{}

;;;; ... <continued from above> -- [:a] is sequential
       [[:a] [:or Number (set-of Number)]]
       {:a #{4 5 6 7}}
       #{}





;;;; nested loose blueprints don't count toward strict blueprint's keys
       [[:a] loose-height-blueprint]
       {:a {:height 72 :extra-key-doesnt-cuase-error "foo"}
        :b "oops"}
       #{"Path [:b] was not specified in the blueprint."}

;;;; can use Classes as a blueprint
       [[:a] String]
       {:a "Roberto"}
       #{}

       [[:a] String]
       {:a :Roberto}
       #{"Expected value :Roberto, at path [:a], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}

       ;; instance-of? satisfies the predicate -- Long is an instance of Number
       [[:a] Number]
       {:a (long 999)}
       #{}

;;;; Wildcard paths

       ;; no problems if all paths match the wildcards in the path
       [[(wild Keyword) (wild string?) 99] String]
       {:a      {"b"      {99 "letter b"}}
        :xavier {"yellow" {99 "zebra"}}}
       #{}

       ;; validates the value at the given path, like normal
       [[:a (wild keyword?) (wild string?)] String]
       {:a {:x {"b" :b "c" "letter c"}}}
       #{"Expected value :b, at path [:a :x \"b\"], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}

       ;; if a path exists that doesn't match the wildcard, it is considered an extraneous path
       [[:a] (map-blueprint :strict [[(wild Keyword)] String])]
       {:a {"b" "foo" "c" "bar"}}
       #{"Path [:a \"c\"] was not specified in the blueprint."
         "Path [:a \"b\"] was not specified in the blueprint."}

       ;; can use Class objects as wildcard part of wildcard path
       [[:a (wild String)] String]
       {:a {"b" :b "c" "letter c"}}
       #{"Expected value :b, at path [:a \"b\"], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}

       ;; can use 'and statements' in simple-blueprints
       [[:a (wild [String #{"baz" "qux"}])] String]
       {:a {"baz" :b "c" "letter c"}}
       #{"Path [:a \"c\"] was not specified in the blueprint."
         "Expected value :b, at path [:a \"baz\"], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}

       ;; if no keys of the leaf-map match the wildcard, that is OK
       [[:a (wild string?)] String]
       {:a {999 :boom}}
       #{"Path [:a 999] was not specified in the blueprint."}

       ;; Wildcard paths match empty maps
       [[:a (wild string?)] String]
       {:a {}}
       #{}

       ;; don't get missing path errors for nested blueprints within wildcard paths - regression test Jun 15, 2012
       [[:carted (wild String)] product-blueprint]
       {:carted {"Sneetch" {:quantity 5 :price 100}}}
       #{}

       ;; doesn't confuse paths with string keys as wildcard paths - regression test Jun 15, 2012
       [["Sneetch" :unit-price-cents] String]
       {"Sneetch" {:unit-price-cents "a"}}
       #{}

       ;; won't confuse them in nested paths either
       [["Sneetch" :unit-price-cents] (map-blueprint :strict [[:a] string?])]
       {"Sneetch" {:unit-price-cents {:a "a"}}}
       #{}

       ;; when top level keys in the map is wildcarded
       [[(wild (comp keyword #(re-matches #"key\d+" %) name))] String]
       {:key0 "val0" :key1 "val1" :key2 "val2"}
       #{}

       ;; when top level keys in the map is wildcarded, but there are top
       ;; level keys which dont match the wildcard
       [[(wild (comp keyword #(re-matches #"key\d+" %) name))] String]
       {:key0 "val0" :key1 "val1" :key2 "val2" :top-level "another"}
       #{"Path [:top-level] was not specified in the blueprint."}

       ;; present concrete paths at the same level as the wildcard path,
       ;; wont be considered as an extraneous path
       [[:top-level] String
        [(wild (comp keyword #(re-matches #"key\d+" %) name))] String]
       {:key0 "val0" :key1 "val1" :key2 "val2" :top-level "another"}
       #{}

       ;; paths that are longer than the map accepts are handled without throwing exceptions
       [[:a (wild string?)] String]
       {:a 1}
       #{"Path [:a] was not specified in the blueprint."}

       ;; can't have empty maps at wilcard paths, they don't count
       [[:a :b] (map-blueprint :strict [[(wild String)] Number])]
       {:a {}}
       #{"Map did not contain expected path [:a :b]."}

       ;; ... <continued>
       [[:a :b] (map-blueprint :strict [[:banana-count] Number
                                        [(wild String)] Number])]
       {:a {}}
       #{"Map did not contain expected path [:a :b]."}

;;;; optional paths interactions with wild card paths

       ;; no missing path error even when it finds none that match the wildcard
       [(optional-path [:a (wild string?)]) String]
       {}
       #{}

       ;; no missing path error, even when the value isn't map as was expected
       [(optional-path [:a (wild string?)]) String]
       {:a 1}
       #{"Path [:a] was not specified in the blueprint."}

       ;; notices extraneous paths that have separately included subpaths
       ;; in same blueprint - regression test July 20, 2012
       [[:name]    String
        [:data]    map? ;; this guy = 'separately included subpath'
        [:data :a] String]
       {:name "Roberto"
        :data {:a "cool"
               :b "dude"}}
       #{"Path [:data :b] was not specified in the blueprint."}


       ;; [Issue #1] - Can AND a sequential with a single item blueprint
       [[:a] [empty? (sequence-of String)]]
       {:a []}
       #{}

       ;; [Issue #1] - continued...
       [[:a] [empty? (sequence-of String)]]
       {:a ["Roberto"]}
       #{"Value [\"Roberto\"], at path [:a], did not match predicate 'empty?'."}

       ))

(deftest test-blueprints-can-check-constraints-against-entire-map
  (let [errors (validation-errors blueprint-with-constraints {:a "string"
                                                              :b 99
                                                              :extra 47})]
    (is (= #{"Constraint failed: '(fn [m] (even? (count (keys m))))'" "Constraint failed: '(comp even? count distinct vals)'"}
           errors)))

  (is (= #{} (validation-errors blueprint-with-constraints {:a "string"
                                                            :b 99}))))

(deftest test-loose-blueprint-validations
  (are [blueprint m errors] (= errors (validation-errors (map-blueprint :loose blueprint) m))

       ;; extra paths on wild card paths are ok if the blueprint is loose
       [[:a (wild string?)] String]
       {:a {999 :boom}}
       #{}
       ))

(deftest test-valid?
  (testing "valid iff there'd be no error messages"
    (are [blueprint m result] (= (valid? (map-blueprint :strict blueprint) m) result)

         [[:a] number?]
         {:a 1}
         true

         [[:b] number?]
         {}
         false)))

;; TODO ALex July 30, 2012 -- move into internal ns all about wildcard paths
(deftest test-wildcard-path->concrete-paths
  (are [m wildcard-path concrete-paths] (= (set concrete-paths)
                                           (set (#'architect.validation/wildcard-path->concrete-paths m
                                                                                                      wildcard-path)))
       ;; base cases
       {}
       []
       [[]]

       {:a 1}
       [:a]
       [[:a]]

       ;; expands concrete path into itself
       {:a {:any-keyword {:c {:any-keyword 'SOMETHING}}}}
       [:a :any-keyword :c :any-keyword]
       [[:a :any-keyword :c :any-keyword]]

       ;; shortest wildcard works -- important test don't remove
       {:a 1}
       [(wild keyword?)]
       [[:a]]

       ;; expands wildcard path into all possible paths based on the supplied map 'm'
       {:a {:b {:c "foo"}
            :x {:c "bar"}}}
       [:a (wild keyword?) :c]
       [[:a :b :c]
        [:a :x :c]]

       ;; if a map doesn't have enough nesting to satisfy a wildcard path, then
       ;; there are no concrete paths generated
       {:a 1}
       [:a (wild :b)]
       []

       ))

;; same here
(deftest test-covered-by-wildcard-path?
  (are [path wildcard-path covered?] (= covered? (#'architect.validation/covered-by-wildcard-path? path wildcard-path))

       ;; base case
       []
       []
       true

       ;; ..
       [:a]
       [(wild keyword?)]
       true

       ;; ..
       [:a :b]
       [(wild keyword?)]
       false

       ;; ..
       [:a :b]
       [:a :b]
       true

       [:a]
       [:a :b]
       false

       [:a :b]
       [:a]
       false

       [:a "S"]
       [(wild keyword?) (wild String)]
       true))

(def-map-blueprint foo-blueprint
  [[:a] String])

(deftest test-validate-and-handle
  (is (= "SUCCESS: {:a \"one\"}"
         (validate-and-handle {:a "one"}
                              foo-blueprint
                              (fn [m] (str "SUCCESS: " m))
                              (fn [m errors] (str "FAIL: " m errors)))))
  (is (= "FAIL: {:b 2}(\"Map did not contain expected path [:a].\" \"Path [:b] was not specified in the blueprint.\")"
         (validate-and-handle {:b 2}
                              foo-blueprint
                              (fn [m] (str "SUCCESS: " m))
                              (fn [m errors] (str "FAIL: " m errors))))))

(deftest test-seq-validation-errors
  (is (= #{}
         (validation-errors (seq-blueprint :all String) ["a" "b" "c"])))
  (is (= #{"Expected value :a, at path [0], to be an instance of class java.lang.String, but was clojure.lang.Keyword"
           "Expected value :b, at path [1], to be an instance of class java.lang.String, but was clojure.lang.Keyword"
           "Expected value :c, at path [2], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}
         (validation-errors (seq-blueprint :all String) [:a :b :c]))))

(deftest test-set-validation-errors
  (is (= #{}
         (validation-errors (set-blueprint String) #{"a" "b" "c"})))
  (is (= #{"Expected value :a, at path [:*], to be an instance of class java.lang.String, but was clojure.lang.Keyword"
           "Expected value :b, at path [:*], to be an instance of class java.lang.String, but was clojure.lang.Keyword"
           "Expected value :c, at path [:*], to be an instance of class java.lang.String, but was clojure.lang.Keyword"}
         (validation-errors (set-blueprint String) #{:a :b :c}))))

(deftest test-simple-blueprints
  (is (= #{}
         (validation-errors String "neat")))
  (is (= #{"Expected value 44 to be an instance of class java.lang.String, but was java.lang.Long"}
         (validation-errors String 44)))

  (is (= #{}
         (validation-errors #"neat" "neato")))
  (is (= #{"Value \"neat\" did not match predicate '(fn [s] (re-find #\"^neato$\" s))'."}
         (validation-errors #"^neato$" "neat")))

  (is (= #{}
         (validation-errors [:or String Number] "neat")
         (validation-errors [:or String Number] 55)))
  (is (= #{"Expected value :keyword to be an instance of class java.lang.Number, but was clojure.lang.Keyword" "Expected value :keyword to be an instance of class java.lang.String, but was clojure.lang.Keyword"}
         (validation-errors [:or String Number] :keyword)))
  (is (= #{"Expected value :keyword to be an instance of class java.lang.Number, but was clojure.lang.Keyword" "Expected value :keyword to be an instance of class java.lang.String, but was clojure.lang.Keyword"}
         (validation-errors [:or Number String] :keyword)))

  (is (= #{}
         (validation-errors [Number Long] (long 55))))
  (is (= #{"Expected value :keyword to be an instance of class java.lang.Number, but was clojure.lang.Keyword"
           "Expected value :keyword to be an instance of class java.lang.Long, but was clojure.lang.Keyword"}
         (validation-errors [Number Long] :keyword)))

  
  (is (= #{}
         (validation-errors string? "string")))
  (is (= #{"Value 99 did not match predicate 'string?'."}
         (validation-errors string? 99)))
  
  (is (= #{}
         (validation-errors 1 1)))
  (is (= #{"Value 99 did not match predicate '(fn [x] (= 1 x))'."}
         (validation-errors 1 99)))
  
  (is (= #{}
         (validation-errors :a :a)))
  (is (= #{"Value {:a true} did not match predicate '(fn [x] (= :a x))'."}
         (validation-errors :a {:a true}))))

(deftest test->string-blueprint
  (is (= #{} (validation-errors (->string-blueprint [Long neg?]) "-55")))
  (is (= #{} (validation-errors (->string-blueprint pos?) "55")))
  (is (= #{} (validation-errors (->string-blueprint (set-of Long)) "#{55, 44, -33}")))

  (is (= #{"After applying :pre-validation-transform of #'clojure.core/read-string to original of \"[:a, 44, -33]\", expected value :a, at path [0], to be an instance of class java.lang.Long, but was clojure.lang.Keyword"}
         (validation-errors (->string-blueprint (sequence-of Long)) "[:a, 44, -33]")))
  (is (= #{"After applying :pre-validation-transform of #'clojure.core/read-string to original of \"55\", value 55 did not match predicate 'neg?'."}
         (validation-errors (->string-blueprint neg?) "55")))
  (is (= #{"Value 55 could not be transformed before validation using '#'clojure.core/read-string'."}
         (validation-errors (->string-blueprint [Long pos?]) 55))))

(deftest test-seq-layouts
  (is (= #{}
         (validation-errors checkers-board-blueprint [[1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]])))

  (is (= #{"Constraint failed: '(fn [xs] (= (count seq-layout) (count xs)))'"}
         (validation-errors checkers-board-blueprint [[0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]])))

  (is (= #{"Value 77777, at path [3 5], did not match predicate '#{1}'."}
         (validation-errors checkers-board-blueprint [[1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 77777 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]
                                                      [1 0 1 0 1 0 1 0]
                                                      [0 1 0 1 0 1 0 1]]))))

(deftest edge-cases-in-blueprint-construction
  (is (= #{}
         (validation-errors non-empty-map {:a 1})))

  (is (= #{}
         (validation-errors unsorted-non-empty-map {:a 1})))
  (is (= #{"Constraint failed: '(complement empty?)'"}
         (validation-errors unsorted-non-empty-map {})))
  (is (= #{"Constraint failed: '(fn [m] (not (sorted? m)))'"}
         (validation-errors unsorted-non-empty-map (sorted-map :a 1))))

  (is (= #{}
         (validation-errors red-list (list :red :red))))
  (is (= #{"Constraint failed: '(fn [xs] (even? (count xs)))'"}
         (validation-errors red-list (list :red :red :red))))
  (is (= #{"Constraint failed: 'list?'"}
         (validation-errors red-list (vector :red :red))))

  (is (= #{}
         (validation-errors red-set (sorted-set :red :RED))))
  (is (= #{"Constraint failed: '(fn [xs] (even? (count xs)))'"}
         (validation-errors red-set (sorted-set :red :RED :Red))))
  (is (= #{"Constraint failed: 'sorted?'"}
         (validation-errors red-set (hash-set :red :RED)))))