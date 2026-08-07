(ns audio.datalad-test
  "isekai manifest が下流に渡す provenance の検査。

  この actor が publish する資産は、あとで cloud-itonami-isco-2652 の受注 gate や
  sakkyokuka の作品台帳に流れる。そこが必要とするのは『AI が作ったのか人が
  書いたのか』を**機械で読める**形で、従来の `:asset/gen :provenance`
  （英語の散文 1 行）では答えられなかった。"
  (:require [clojure.test :refer [deftest is testing]]
            [audio.datalad :as datalad]))

(def ^:private base
  {:id "aud-001" :kind :asset :format "wav" :title "遠雷"
   :license :cc0 :tags ["calm" "night"]
   :gen-job-id "job-123" :prompt "calm night, sparse"
   :created "2026-08-07T00:00:00Z"})

(deftest manifest-carries-machine-readable-provenance
  (let [m (datalad/->isekai-manifest (assoc base :modality :music :fn-id "gen.music"))]
    (is (= :generated (:work/provenance m))
        "下流の gate は :generated として扱えなければならない")
    (is (= {:murakumo/engine :audio
            :murakumo/modality :music
            :gen/job-id "job-123"
            :murakumo/fn-id "gen.music"}
           (:work/generator m)))))

(deftest model-id-is-deliberately-absent
  (testing "murakumo の function entry はモデルを持たないので、この actor は観測できない"
    (let [m (datalad/->isekai-manifest (assoc base :modality :sfx :fn-id "gen.sfx"))]
      (is (not (contains? m :work/model-id))
          ":fn/id を model-id として書くのは嘘になる（関数 ID であってモデルではない）")
      (is (= "gen.sfx" (get-in m [:work/generator :murakumo/fn-id]))
          "観測できる事実は generator 側に記録する"))))

(deftest fn-id-is-optional
  (testing "fn-id が無くても generator は engine/modality/job-id を運ぶ"
    (let [m (datalad/->isekai-manifest (assoc base :modality :music))]
      (is (= {:murakumo/engine :audio :murakumo/modality :music :gen/job-id "job-123"}
             (:work/generator m))))))

(deftest existing-manifest-shape-is-unchanged
  (testing "既存の consumer が読むキーは 1 つも変えていない"
    (let [m (datalad/->isekai-manifest (assoc base :modality :sfx))]
      (is (= "aud-001" (:asset/id m)))
      (is (= :asset (:asset/kind m)))
      (is (= "wav" (:asset/format m)))
      (is (= :cc0 (:asset/license m)))
      (is (= :gen (:asset/source m)))
      (is (= :sfx (get-in m [:asset/gen :stage])))
      (is (= "job-123" (get-in m [:asset/gen :job-key])))
      (is (string? (get-in m [:asset/gen :provenance]))))))
