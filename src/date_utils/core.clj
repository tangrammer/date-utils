(ns date-utils.core
  (:require [clj-time.core   :as tc]
            [clj-time.format :as tf]
            [clj-time.coerce :as te]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import org.joda.time.format.DateTimeFormatter
           org.joda.time.DateTime
           org.joda.time.DateTimeZone
           java.util.List
           java.util.regex.Pattern))

;; The following times all refer to the same moment: "18:30Z", "22:30+04", "1130−0700", and "15:00−03:30".
;; Nautical time zone letters are not used with the exception of Z.

(defn date-example [has-hyphens?]
  (if has-hyphens?
    "YYYY-MM-DD 2014-12-30"
    "YYYYMMDD 20141230"))

(defn time-example [has-colons?]
  (if has-colons?
    "HH:MM:SS 12:59:25"
    "HHMMSS 125925"))

(def conditions
  {:numbers {:regex #"\d+"
             :ex-fn-message #(str "You need to use only numbers")}
   :numbers+hyphens {:regex #"[\d-]+"
                     :ex-fn-message #(format "In date format only permited numbers and hiphens. You provided: %s" %)}
   :numbers+colons {:regex #"[\d:]+"
                    :ex-fn-message #(format "In time format only permited numbers and colons.You provided: %s" %)}
   :numbers+colons+Z+plus+minus {:regex #"[Z\d:\+-]+"
                      :ex-fn-message #(format  "In time-zone format only permited letter 'Z', numbers and colons. You provided: " %)}
   :numbers+YMDH {:regex #"[\d[YMDH]]+"
                  :ex-fn-message #(format "Duration pattern only can contain unsigned numbers and these letters 'YMDH' as this pattern nYnMnDnH, you provided %s" %)}})

(defn- substring? [^String sub ^String st]
  (not= (.indexOf st sub) -1))

(defn- satisfy-pattern? [^Pattern p]
  (fn [^String s] (= s (re-find p s))))

(defn- check-condition [value condition-fn exception-message-fn]
  (when-not (condition-fn value)
    (throw (Exception. (exception-message-fn value)))))

(defn check-pattern-condition
  ([key-condition value]
     (check-pattern-condition key-condition value (:ex-fn-message (get conditions key-condition)))
     )
  ([key-condition value ex-fn-message]
     (if-let [c (get conditions key-condition)]
      (let [{:keys [regex]} (get conditions key-condition)]
        (check-condition value (satisfy-pattern? regex) ex-fn-message))
      (throw (Exception. (format "You used a unexistent key pattern condition" key-condition))))))

(defn- parse-int [^String s]
  (. Integer parseInt  s)
  )

(defn valid-year [^String s]
  (let [y (parse-int s)]
    (and (> y 2000) (<= y (inc (tc/year (tc/now)))))))
(defn valid-month [^String s]
  (let [m (parse-int s)]
    (and (> m 0 ) (<= m 12))))
(defn valid-day [^String s]
  (let [d (parse-int s)]
    (and (> d 0 ) (<= d 31))))
(defn valid-hour [^String s]
  (let [m (parse-int s)]
    (and (>= m 0 ) (< m 24))))
(defn valid-minute [^String s]
  (let [m (parse-int s)]
    (and (>= m 0 ) (< m 60))))

(defn- take-val [start n ^String s]
  (apply str (take n (drop start (seq s)))))

(defn- format-date [^String raw-s]
   (let [d-e (date-example (substring?  "-" raw-s))
        s (str/replace raw-s #"-" "")]

    (check-pattern-condition :numbers s
                             (fn [s] (format "You need to use only numeric values, example %s. You provided: %s" d-e s)))

    (check-condition s #(contains? #{4 6 8} (count %))
                     (fn [s] (format "you have invalid format date, example %s. You provided: %s" d-e s)))

    (condp = (count s)
      4 (do
          ;;"YYYY"
          (check-condition s valid-year  #(str "invalid year value" %))
          (format "%s0101" s))
      6 (do
          ;;"YYYYMM"
          (check-condition s (fn [s] (and (valid-year (take-val 0 4 s)) (valid-month (take-val 4 2 s))))
                           #(format "invalid year or month values, you provided : %s" %))
          (format "%s%s01" (take-val 0 4 s) (take-val 4 2 s)))
      8 (do
          ;;"YYYYMMDD"
          (check-condition s (fn [s] (and (valid-year (take-val 0 4 s)) (valid-month (take-val 4 2 s)) (valid-day (take-val 6 2 s))))
                           #(format "invalid year or month or day values, you provided : %s" %))
          s))))

;;;; TIME RELATED CODE

;; An offset of zero, in addition to having the special representation "Z", can also be stated numerically as "+00:00", "+0000", or "+00". However, it is not permitted to state it numerically with a negative sign, as "−00:00", "−0000", or "−00".
(defn- take-off-timezone [^String ftime]
  (cond
   (substring? "Z" ftime) (do
                            ;;"zulu"
                            (apply str (butlast ftime))
                            )
   (substring? "+" ftime) (do
                            ;;"utc+"
                            (first (str/split ftime #"\+"))
                            )
   (substring? "-" ftime) (do
                            ;;"utc-"
                            (first (str/split ftime #"\-"))
                            )
   :else (do
           "no timezone"
           ftime)))

(defn- get-timezone [^String ftime]
  (cond
   (substring? "Z" ftime) (do
                            ;;"zulu"
                            "Z"
                            )
   (substring? "+" ftime) (do
                            ;;"utc+"
                            (str "+"(last (str/split ftime #"\+")))
                            )
   (substring? "-" ftime) (do
                            ;;"utc-"
                            (str "-" (last (str/split ftime #"\-")))
                            )
   :else (do
           ;;"no timezone"
           "Z")))

(defn- format-time [^String raw-s]
  (let [t-e (time-example (substring?  ":" raw-s))
        s (str/replace raw-s #":" "")]

    (check-pattern-condition :numbers s
                             (fn [s] (format "You need to use only numeric values, example %s. You provided: %s" t-e s)))

    (check-condition s #(contains? #{2 4 6} (count %))
                     (fn [s] (format "you have invalid format date, example %s. You provided: %s" t-e s)))

    (condp = (count s)
      2(do
         ;;"HH"
         (check-condition s valid-hour
                          #(format "invalid hour value, you provided : %s" %))
         (format "%s0000" (take-val 0 2 s) (take-val 2 2 s)))
      4 (do
          ;;"HH-MM"
          (check-condition s #(and (valid-hour (take-val 0 2 %)) (valid-minute (take-val 2 2 %)))
                           #(format "invalid hour or minute values, you provided : %s" %))
          (format "%s%s00" (take-val 0 2 s) (take-val 2 2 s)))
      6 (do
          ;;"HH-MM-SS"
          (check-condition s #(and (valid-hour (take-val 0 2 %)) (valid-minute (take-val 2 2 %)) (valid-minute (take-val 4 2 %)))
                           #(format "invalid hour or minute or second values, you provided : %s" %))
          s))))

(defn format-time-zone* [^String time-zone]
  (check-pattern-condition :numbers+colons+Z+plus+minus time-zone)
  (if (=  time-zone "Z")
    "Z" ;; else we dont care about these signs in   formatting time zone value +/-
    (str (first time-zone) (format-time (apply str (next time-zone))))))

(defn format-time*
  "only-time, without timezone or date. HH:MM:SS
  or HHMMSS. Example 12:23:23 or 122323"
  [^String only-time]
  (check-pattern-condition :numbers+colons only-time)
  (format-time  only-time))

(defn format-date* [^String s]
  (check-pattern-condition :numbers+hyphens s)
  (format-date s))

(defn format-date-time [^String s]
  (let [[date time] (str/split s #"T")
        date-f (format-date* date)
        time-f (format-time* (take-off-timezone time))
        time-zone-f (format-time-zone* (get-timezone time))]
    (format "%sT%s%s" date-f time-f time-zone-f)))

(defn parse-date*
  "all values are parsed to basic format, so YYYY-MM-DD => YYYYMMDD and HH:MM:SS => HHMMSS"
  [^String -s ]
  (let [s (str/upper-case -s)]
    (if (substring? "T" s)
      (tf/parse (:basic-date-time-no-ms tf/formatters)
                (format-date-time s))
      (tf/parse (:basic-date tf/formatters) (format-date* s)))))

(parse-date* "2014-11-01T05:35:00+05")
(parse-date* "20141101T053000+10")

(defn parse-dur
  "Adapting String dur to this standar
  http://en.wikipedia.org/wiki/ISO_8601#Durations There is a few of
  String pattern validations that ensure ISO_8601

  Result is a map with 'date-fn key' and 'integer value' as follows
  (parse-dur '1Y5M4D3H')
  => {#<core$hours clj_time.core$hours@6802bf86> 3,
      #<core$days clj_time.core$days@11ad1594> 4,
      #<core$monthsclj_time.core$months@26e0108a> 5,
      #<core$years clj_time.core$years@5527e87d> 1}"
  [^String dur]
   (do
     (check-pattern-condition :numbers+YMDH dur)

     (check-condition dur #(even? (count %))
                      #(format "Duration pattern must be even following this pattern nYnMnDnH, you provided %s" %))

     (check-condition dur #(substring? (apply str (mapv (comp str second) (partition 2 %))) "YMDH")
                      #(format "Duration pattern is an orderer pattern nYnMnDnH, you provided %s" %))

     (let [parsed-dur (map (fn [[n t]] [(parse-int (str n)) (get {:Y tc/years :M tc/months :D tc/days :H tc/hours}
                                                                (keyword (str/upper-case (str t))))])
                           (partition 2 dur))
           format-dur (reduce (fn [i [n t]] (assoc i t n)) {} parsed-dur)]

       (check-condition dur (fn [_] (= (count format-dur) (count parsed-dur)))
                        #(format "Duration value can't contain duplicated keys following this pattern nYnMnDnH, you provided %s" %))
       format-dur)))

(defn apply-dur-to-date [^String dur ^DateTime date]
  (let [format-dur (parse-dur dur)]
    (apply tc/plus (conj (map (fn [[f n]] (f n)) format-dur) date))))

(parse-dur "1Y5M4D3H")
(apply-dur-to-date "1Y5M4D3H" (tc/now))
(apply-dur-to-date "0H" (tc/now))
