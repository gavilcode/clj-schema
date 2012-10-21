Schemas for Clojure maps
========================

Define validation schemas for validating maps.

Schemas are any number of paths through a nested map, paired with a
validator.

There are 5 types of validators: 

*   any predicate function 
*   any `Class` object, i.e. `String`, `clojure.lang.Keyword`, `java.util.Date`, etc 
*   any clj-schema schema 
*   `[validator1 validator2]` to indicate both validator1 AND validator2 
*   `[:or validator1 validator2]` to indicate both validator1 OR validator2

Any validator may be wrapped in sequence-of to indicate the value should
be sequential, or wrapped in set-of to indicate the value is a set. By
default, schemas assume the value at a path to be a singular value.

A path may be marked as an `optional-path`. This means that is doesn't
have to be present, but if it is, it must match the given validator.

Wildcard paths are paths where one or more peices are defined as anything
matching a given validator.

Example Schema:

```clj
(defschema bar-schema 
  [[:a :b :c] pred 
   [:x :y :z] [pred2 pred3 z-schema] ;; implicit 'and' - all three must pass 
   [:p :q :r] [:or nil? r-schema] ;; an 'or' statement - need just one to pass 
   (optional-path [:z]) (sequence-of string?) 
   [:a b :d] (loose-valdiation-schema [[:cat :name] String ;; can use Java Class objects directly 
   [:cat :colors] (set-of String)])]
```

Example schema w/ wildcard paths:

```clj
(defschema foo-schema 
  [[:a (wild String) (wild Number)] String])
```

  => matches maps such as: 
    `{:a {"car" {1.21 "jigawatts"}}}` 
    `{:a {"banana" {2 "green"
                      5 "yellow"}
          "apple" {1 "rotten"
                   4 "delicious"}}}`

`defschema` creates a strict schema, which expects only the paths it
describes to be present on the given map.

`def-loose-schema` creates a loose schema, which expects its paths to be
present but does not complain about extra paths


Map Validation Using Schemas
============================
 
`validation-errors` can be used to test a map vs a given schema. It can 
find a variety of issues:

*   thing we're validating wasn't a map
*   map contained a path not specified in the schema (only for strict schemas)
*   map was missing a specified path
*   a path's value was single, but was specified as sequential
*   a path's value was single, but was specified as a set
*   a path's value was sequential, but was specified as single
*   a path's value was a set, but was specified as single
*   a path's value didn't pass the given predicate
*   a path's value's Class wasn't an instance of the specified Class

```clj
(defschema person-schema
  [[:name :first] String
   [:name :last]  String
   [:height]      Double])
```

```clj
(validation-errors person-schema {})
```

```clj
;; => #{"Map did not contain expected path [:name :first]." 
;;      "Map did not contain expected path [:name :last]."
;;      "Map did not contain expected path [:hieght]."}
```

Supports alternate report formats.  You just have to implement the ErrorReporter 
protocol, and then pass it in like this:

```clj
(error-validation MySpecialErrorReporter person-schema {...})
```


Test Data and Test Data Factories
=================================

In the clj-schema.fixtures namespace.

```clj
(defschema person-schema
  [[:name :first] String
   [:name :last]  String
   [:height]      Double])

;; This will blow up at load time if the 'alex' map you want to use in your test
;; turns out to not be valid
(def-fixture alex person-schema
  {:name {:first "Alex"
          :last "Baranosky"}
   :height 71})

;; Let's define a factory that makes people. And then...
(def-fixture-factory person person-schema
  [& {:keys [first-name last-name height]
      :or {first-name "Alex"
           last-name "Baranosky"
           height 71}}]
  {:name {:first first-name
          :last last-name}
   :height height})

;; ... write a test that tests a fictional function called sort-people-by-height
(deftest test-sort-people-by-height
  (is (= [(person :height 67) (person :height 89) (person :height 98)]
         (sort-people-by-height [(person :height 98) (person :height 67) (person :height 89)]))))

```

Type Coercion using Schemas
===========================

Very new.  Will likely change a bunch.

Support for attempting to coerce a map to pass a given schema.  This can be used 
to assist JSON deserialization or XML parsing, for example.

Uses a multi-method to define coercers, so you can extend it.


Developer Tests
===============

Run them with `./run-tests.sh`.  This will run all unit tests in Clojure 1.2 - 1.5, ensuring this library is usable by the widest number of projects.