(ns audio.judge
  "JVM I/O edge: score a candidate for persona-fit/creative quality via the
  murakumo chat gateway (same synchronous OpenAI-chat-completions proxy
  `cloud_itonami.media.murakumo` uses — `bb murakumo infer gateway`,
  MURAKUMO_GATEWAY_URL to override).

  HONEST LIMIT (state this, do not pretend otherwise — same discipline as
  cloud_itonami.media's own docstring): this judges the CANDIDATE'S PROMPT
  TEXT for persona-fit and descriptive quality, not the rendered audio the
  generation job actually produced. A real perceptual judge (an audio
  quality model, a listening-capable critique call) is follow-up work — see
  ADR-2607123000 Consequences."
  (:require [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))

(defn- gateway-url [] (or (System/getenv "MURAKUMO_GATEWAY_URL") "http://localhost:8790"))

(def default-model
  "Never hardcode elsewhere (llm-model-ssot discipline, root CLAUDE.md)."
  (or (System/getenv "MURAKUMO_MEDIA_MODEL") "gemma4:12b-it-qat"))

(defn jvm-http-fn [url body-str]
  (let [req (-> (HttpRequest/newBuilder (URI/create url))
                (.timeout (Duration/ofSeconds 60))
                (.header "Content-Type" "application/json")
                (.POST (HttpRequest$BodyPublishers/ofString body-str))
                .build)
        resp (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode resp) :body (.body resp)}))

(defn- extract-content [parsed] (get-in parsed ["choices" 0 "message" "content"]))

(defn parse-score
  "First 0-100 integer in the critique text, or nil (never a fabricated
  default) if the model didn't emit a parseable one."
  [text]
  (when text
    (some (fn [s] (let [n (parse-long s)] (when (and n (<= 0 n 100)) n)))
          (re-seq #"\d{1,3}" text))))

(defn score!
  "candidate + persona -> {:score 0-100 :critique \"...\"} or nil on ANY
  failure (gateway unreachable, non-200, unparseable) — audio.cosci/reflect
  must treat nil as 'skip this candidate, do not crash the round', never as
  a silently-fabricated 0."
  ([candidate persona] (score! candidate persona {}))
  ([{:keys [prompt]} {:keys [voice tags]}
    {:keys [http-fn model url] :or {http-fn jvm-http-fn model default-model}}]
   (try
     (let [url (or url (gateway-url))
           sys (str "You are " voice " Rate the following music/SFX-generation "
                    "prompt for creative quality and fit to your own tags ("
                    (str/join ", " tags) ") on a 0-100 scale. "
                    "Reply with the integer score first, then one short sentence why.")
           body {:model model :stream false
                 :messages [{:role "system" :content sys}
                            {:role "user" :content prompt}]}
           resp (http-fn (str url "/v1/chat/completions") (json/generate-string body))]
       (when (= 200 (:status resp))
         (let [content (extract-content (json/parse-string (:body resp)))]
           (when-let [score (parse-score content)]
             {:score score :critique (str/trim content)}))))
     (catch Exception _ nil))))
