# gftd-audio-actor

A **free music + SFX generation** loop actor for
[`network-isekai`](https://github.com/gftdcojp/network-isekai), gftdcojp's
sixth of seven per-modality asset actors (ADR-2607123000). Persona: **リツ
(Ritsu)**, 作曲家 (composer) — "静けさを恐れない作曲家。曲もSFXも間を大事に
する — 音数より余白で語る。" (see `resources/persona.edn`). Sibling actors:
`gftd-illust-actor` (illustration), `gftd-sculpt-actor` (3D), `gftd-rig-actor`
(auto-rig), `gftd-motion-actor` (motion clips), `gftd-avatar-actor` (VRM
compositing), `gftd-voice-actor` (TTS voice).

Built on the same "sealed intelligence ⊣ independent governor ⊣ append-only
ledger" containment pattern as this workspace's other actors
(`gftd-talent-actor`, `wami-actor`, `cloud-itonami`) — here it is
**co-scientist tournament ⊣ AssetGovernor**, run by a **durable outer loop**
(not a StateGraph — murakumo generation jobs are async, minutes-scale, and
this workspace's CLAUDE.md is explicit that long-running work belongs in a
lease/tick/budget loop, not a StateGraph interrupt).

## Dual modality: this actor covers both `:music` and `:sfx`

Unlike its six siblings, each of which has exactly one fixed murakumo
modality, `gftd-audio-actor` is **dual-modality**. Per cloud-murakumo's
`murakumo.edn` (`:apps :generation`), `:music` and `:sfx` are two separate
`:fn/modality` entries that happen to share the same `:fn/engine :audio` —
and ADR-2607123000 groups them under one persona ("作曲家・リツ, composer,
music+sfx"), so one repo/persona/ledger covers both rather than splitting
into `gftd-music-actor` + `gftd-sfx-actor`.

Concretely, this changes three places relative to the single-modality
reference shape (`gftd-illust-actor`):

- **`audio.murakumo`** has no single `(def modality ...)` — `function` and
  `submit!` both take a modality argument (`:music`/`:sfx`), resolved
  per-candidate from the candidate's own `:modality` key. `actor-id` is
  still a fixed constant (`"gftd-audio-actor"`); only the murakumo function
  lookup varies.
- **`audio.generate/round-candidates`** produces a MIX within every round:
  even candidate index → `:music` (gene pool: mood/tempo/instrumentation),
  odd index → `:sfx` (gene pool: kind/character/length). Every candidate now
  carries a `:modality` key that travels with it through submit → poll →
  judge → cosci → the accepted asset.
- **`audio.datalad/->isekai-manifest`** sets `:asset/gen :stage` to whichever
  modality actually won the round (`(:modality candidate)`), not a
  hardcoded keyword — either flavor can win.

Everything else — `audio.cosci/reflect` (format-based: both modalities
declare `"wav"` per murakumo's `default-out-by-modality`), `audio.governor`,
polling/settling in `audio.loop` — needed **no** modality-aware branching,
since a `:done` job's declared output format already disambiguates what
matters structurally.

## The core contract

```
audio.generate              murakumo fleet (async gen.job)       audio.judge
 (closed gene pool,    ──▶  submit via cloud-murakumo.gen +   ──▶ (persona-fit
  music/sfx mixed)          queue-kotoba, poll for :done)          prompt score)
                                    │
                                    ▼
                        audio.cosci/run-round
              (Reflection=HARD gate, Ranking=Elo on judge score,
                    Proximity, Evolution, Meta-review)
                                    │
                              round winner
                                    ▼
                          audio.governor/violations
                    (license-free? format-ok? safe? titled?
                          write-kind is :asset only)
                          │                    │
                        ok?                  hard
                          ▼                    ▼
          audio.datalad + audio.aozora      audio.ledger
          (save to assets/, datalad push,   (:held — no binary
           publish to net.audio.asset)       is ever saved)
```

**The actor never commits/publishes an asset the AssetGovernor would
reject**, and it never writes anything but `:kind :asset` — it does not
touch network-isekai's game logic or canon, it only produces free material
for games to consume.

**HONEST LIMITS** (state these, do not pretend otherwise):
- `audio.judge` scores the candidate's **prompt text** for persona-fit, not
  the rendered audio the job actually produced. A real perceptual judge (an
  audio quality model, a listening-capable critique call) is follow-up.
- Whether a submitted job ever leaves `:queued` depends on a murakumo fleet
  worker (Mac-mini / `gad`) being up and consuming the `gftd-murakumo` kotoba
  queue — this actor only submits/polls, it never runs GPU inference itself.
- `audio.murakumo/artifact-url`'s CID→URL resolution is a best-effort guess
  (`KOTOBASE_ARTIFACT_BASE_URL` overrides it), not a confirmed contract.

## This repo IS its own DataLad dataset

Unlike a typical actor repo, `assets/` here is **git-annex + Backblaze B2**
(`-c text2git`: code/EDN stay plain git, binaries get annexed) — accepted
assets are saved straight into this repo and pushed to B2, so "actor's own
git repo" and "asset storage" are the same thing (ADR-2607123000 §5).
`assets/<id>.edn` is written in the `network-isekai` `isekai.asset` manifest
shape so a later Asset Hub import needs no conversion.

```sh
datalad get assets/            # fetch real bytes from B2 (skeleton clones without them)
datalad push --to b2           # push new bytes after a local save
```

## Running

```sh
clojure -M:run tick     # one durable-loop step (cron/launchd)
clojure -M:run run      # stay resident, tick on an interval
clojure -M:run status   # print ledger tail + loop state
clojure -M:test         # offline, fully faked (no network) — see test/audio/loop_test.clj
clojure -M:lint         # clj-kondo, errors fail
```

Env: `ASSET_ACTOR_DAILY_BUDGET` (default 8 gen jobs/day),
`MURAKUMO_KOTOBA_URL`/`MURAKUMO_KOTOBA_GRAPH`/`MURAKUMO_KOTOBA_TOKEN`
(queue-kotoba auth), `MURAKUMO_GATEWAY_URL` (judge's chat-completions
gateway).

CACAO identity is self-minted to `.audio/identity.edn` on first run
(gitignored — never commit a private key). aozora collection:
`net.audio.asset.publish`.

## Design

ADR-2607123000 (`network-isekai 向け murakumo 生成アセット持続ループ actor
群`) is the SSoT for this actor and its six siblings. Direct code ancestry:
`cloud-itonami`'s `src/cloud_itonami/media/{murakumo,aozora,cacao,publisher,
publish}.clj(c)` (murakumo→governor→aozora pipeline), `cloud-murakumo`'s
`src/cloud_murakumo/cosci.cljc` (co-scientist tournament shape), and
`gftd-illust-actor` (this ADR's first-built, single-modality reference
implementation — this repo is a faithful port of its shape with the
dual-modality difference described above).
