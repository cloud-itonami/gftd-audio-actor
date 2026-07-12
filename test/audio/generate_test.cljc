(ns audio.generate-test
  (:require [clojure.test :refer [deftest testing is]]
            [audio.generate :as generate]))

(def persona {:tags ["network-isekai"]})

(deftest round-candidates-is-pure-and-reproducible
  (is (= (generate/round-candidates persona 3 3) (generate/round-candidates persona 3 3))))

(deftest round-candidates-count-matches-k
  (is (= 5 (count (generate/round-candidates persona 0 5)))))

(deftest round-candidates-ids-are-unique-within-a-round
  (let [cs (generate/round-candidates persona 2 4)]
    (is (= 4 (count (distinct (map :candidate/id cs)))))))

(deftest round-candidates-prompt-includes-persona-tags
  (doseq [c (generate/round-candidates persona 0 3)]
    (is (re-find #"network-isekai" (:prompt c)))))

(deftest different-rounds-vary-the-gene-selection
  (let [r0 (map :gene (generate/round-candidates persona 0 3))
        r1 (map :gene (generate/round-candidates persona 1 3))]
    (is (not= r0 r1))))

(deftest round-candidates-mix-music-and-sfx-modalities
  (testing "even index -> :music, odd index -> :sfx, within the same round"
    (let [cs (generate/round-candidates persona 0 6)]
      (is (= [:music :sfx :music :sfx :music :sfx] (mapv :modality cs)))
      (is (= #{:music :sfx} (set (map :modality cs))))))
  (testing "music candidates carry music-shaped genes, sfx candidates sfx-shaped genes"
    (doseq [c (generate/round-candidates persona 1 4)]
      (case (:modality c)
        :music (is (= #{:mood :tempo :instrumentation} (set (keys (:gene c)))))
        :sfx   (is (= #{:kind :character :length} (set (keys (:gene c)))))))))
