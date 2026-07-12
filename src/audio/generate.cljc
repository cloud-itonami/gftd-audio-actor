(ns audio.generate
  "Pure candidate builder for one co-scientist round (ADR-2607123000 §2/§3).

  Same 'closed hypothesis pool, no LLM in Generation' discipline
  cloud_murakumo.cosci uses: a small enumerable gene pool of mood/tempo/
  instrumentation (music) or kind/character/length (sfx) variations,
  persona-flavored via `:persona/tags`. round-candidates is a pure function
  of (persona, round, k) — re-running the same round number reproduces the
  same candidates; exploration across the pool happens by round number
  advancing (audio.loop) and by biasing one gene slot toward the previous
  round's elite (audio.cosci/evolve-round).

  DUAL-MODALITY (STEP 2, ADR-2607123000 — this actor's one structural
  difference from its six single-modality siblings): each round MIXES
  :music- and :sfx-flavored candidates rather than producing one fixed
  modality — even candidate index -> :music (from `music-gene-pool`), odd
  index -> :sfx (from `sfx-gene-pool`). Every candidate now carries its own
  `:modality` key so downstream (audio.murakumo/submit!, audio.datalad)
  knows which murakumo function/asset stage it belongs to."
  (:require [clojure.string :as str]))

(def music-gene-pool
  {:mood ["quiet travel theme" "bustling market ambience" "gentle mystery" "warm homecoming"]
   :tempo ["slow" "mid-tempo" "upbeat"]
   :instrumentation ["solo acoustic guitar" "small chamber ensemble" "soft synth pad" "folk strings"]})

(def sfx-gene-pool
  {:kind ["footstep on gravel" "door creak" "coin pickup chime" "notification ping" "wind gust" "page turn"]
   :character ["short and crisp" "soft and rounded" "bright and metallic"]
   :length ["very short (under 1s)" "short (1-2s)"]})

(defn- pick [xs seed n] (nth xs (mod (+ seed n) (count xs))))

(defn- music-gene-for
  "One :music candidate's gene map. `bias` (from audio.cosci/evolve-round's
  elite, or nil on round 0) pins ONE randomly-chosen slot to the prior
  winner's value instead of round-robining it — elitism without literal
  crossover machinery. If `bias` doesn't carry a music-shaped gene (e.g. the
  prior round's winner was an :sfx candidate), select-keys is a safe no-op
  and this just falls back to the plain pick."
  [round i bias]
  (let [raw {:mood            (pick (:mood music-gene-pool) round i)
             :tempo           (pick (:tempo music-gene-pool) round (+ i 1))
             :instrumentation (pick (:instrumentation music-gene-pool) round (+ i 2))}]
    (if (and bias (pos? round) (zero? (mod (+ round i) 3)))
      (merge raw (select-keys bias [(nth [:mood :tempo :instrumentation] (mod round 3))]))
      raw)))

(defn- sfx-gene-for
  "One :sfx candidate's gene map — same elitism-bias discipline as
  music-gene-for, over the :kind/:character/:length gene pool instead."
  [round i bias]
  (let [raw {:kind      (pick (:kind sfx-gene-pool) round i)
             :character (pick (:character sfx-gene-pool) round (+ i 1))
             :length    (pick (:length sfx-gene-pool) round (+ i 2))}]
    (if (and bias (pos? round) (zero? (mod (+ round i) 3)))
      (merge raw (select-keys bias [(nth [:kind :character :length] (mod round 3))]))
      raw)))

(defn round-candidates
  "persona + round n (0-based) + k candidates + optional elite bias
  -> [{:candidate/id :modality :prompt :gene :params} ...]. Mixes :music
  and :sfx flavored candidates within a single round: even index -> :music,
  odd index -> :sfx."
  ([persona n k] (round-candidates persona n k nil))
  ([{:keys [tags]} n k bias]
   (vec
    (for [i (range k)]
      (if (even? i)
        (let [{:keys [mood tempo instrumentation]} (music-gene-for n i bias)]
          {:candidate/id (str "r" n "-c" i)
           :modality :music
           :prompt (str/join ", " (concat [mood (str tempo " tempo") instrumentation]
                                           tags))
           :gene {:mood mood :tempo tempo :instrumentation instrumentation}
           :params {}})
        (let [{:keys [kind character length]} (sfx-gene-for n i bias)]
          {:candidate/id (str "r" n "-c" i)
           :modality :sfx
           :prompt (str/join ", " (concat [kind character length]
                                           tags))
           :gene {:kind kind :character character :length length}
           :params {}}))))))
