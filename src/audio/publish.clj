(ns audio.publish
  "Identity lifecycle + the accepted-asset -> aozora wire record mapping.
  Mirrors `cloud_itonami.media.publish`'s shape (never re-validates what the
  governor already checked; refuses to publish anything the governor held —
  callers only reach this ns after audio.governor/ok? is true)."
  (:require [json.compat :as json]
            [audio.cacao :as cacao]
            [audio.aozora :as aozora]
            [audio.publisher :as pub-proto]))

(def identity-path ".audio/identity.edn")

(defn load-or-create-identity! [] (cacao/load-or-create-identity! identity-path))

(def display-name "gftd-audio-actor — リツ")

(def description
  (str "🎵 AI-GENERATED music and SFX for network-isekai, produced by a "
       "co-scientist-style generation loop over the murakumo fleet, gated by "
       "an independent AssetGovernor before anything is published. Every "
       "asset here is free (:cc0/:cc-by) — github.com/gftdcojp/gftd-audio-actor"))

(defn json-opts []
  {:json-write json/generate-string
   :json-read #(json/parse-string % true)})

(defn ensure-profile! [opts]
  (aozora/set-profile! opts display-name description))

(defn asset->wire
  "governor-passed asset -> the wire shape aozora.clj expects. :rkey derived
  from the murakumo job id so re-running the same accepted job is idempotent."
  [{:keys [gen-job-id title license tags prompt cid]}]
  {:rkey (str "asset." gen-job-id)
   :title title
   :license (name license)
   :tags (vec tags)
   :prompt prompt
   :cid cid
   :note "free asset for network-isekai"})

(defn publish!
  "asset (already audio.governor/ok?) -> {:uri :cid}. `publisher` defaults
  to nil, which is an error — callers must pass a real
  audio.aozora/aozora-publisher (or audio.publisher/mock-publisher in
  tests); there is no silent offline fallback for a real publish call."
  [publisher asset]
  (let [pub (or publisher (throw (ex-info "publish! requires a Publisher" {})))]
    (pub-proto/publish! pub (asset->wire asset))))
