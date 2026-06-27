# Tool-call benchmark (L1, offline)

Measures the one thing every prompt/schema change moves: **given a frozen
situation, does the model pick the right tool with valid, correct arguments.**
It replays canned contexts through the *real* harness — the same tool schemas
(`ToolRegistry`), the same system prompt (`NumenPrompts.ENTITY_PROMPT`), the same
wire format (`LlmProvider`) the live loop uses — so a prompt edit and its measured
effect travel together. No Minecraft world, no companion body: the only live
dependency is the LLM call, so a run is seconds and reproducible.

This is **not** an end-to-end test. Whether `move_to` actually pathfinds is a
separate concern; this scores *tool choice + argument formation* only.

## Run it

Copy the template to a git-ignored `.env` and fill in your key, then run one command:

```bash
cp benchmark/.env.example benchmark/.env      # then edit benchmark/.env
./gradlew :common:toolBench
```

`benchmark/.env` (and a repo-root `.env`) are git-ignored, so the key never touches
VCS or your shell history. With no key configured the task **skips** (never fails),
and `./gradlew test` never runs it, so normal builds/CI make no network call.

Config precedence (low → high): `benchmark/.env` < repo-root `.env` < shell env var
< `-Dnumen.bench.<x>` flag — so you can keep defaults in `.env` and override one
value on the command line, e.g. `./gradlew :common:toolBench -Dnumen.bench.model=deepseek-reasoner`.

### Config keys (`.env` / env var name, or `-Dnumen.bench.<x>` system property)

| var | default | meaning |
|---|---|---|
| `NUMEN_BENCH_API_KEY` | — (required) | API key; absent → skip |
| `NUMEN_BENCH_PROVIDER` | `openai` | `openai` or `deepseek` (wire format) |
| `NUMEN_BENCH_MODEL` | `gpt-4o-mini` | model id |
| `NUMEN_BENCH_BASE_URL` | provider default | OpenAI-compatible base (no `/chat/completions`) |
| `NUMEN_BENCH_SAMPLES` | `3` | calls per case (variance / noise floor) |
| `NUMEN_BENCH_TEMPERATURE` | unset | set `0` for determinism if your model allows it |
| `NUMEN_BENCH_PROXY` | — | `host:port` if needed |

## What it reports

Per case and aggregate, three dimensions:

- **tool selection** — was the first tool call one of the case's acceptable tools.
- **args valid** — does every call's arguments pass the tool's *own* validator?
  World-action tools run the real `toTaskRecord(...)`; query/local tools fall back
  to a schema-required-keys check. (Cheap oracle: no model needed to grade this.)
- **args match** — for cases that pin specific args (e.g. `count: 10`), do they match.

Because models are nondeterministic, each case runs `SAMPLES` times and the rates
are fractions (e.g. `4/5`). A change is **real** only when the aggregate moves
beyond the run-to-run noise — watch per-case lines too: a tool merge can fix some
cases and break others while the aggregate looks flat.

Every run appends one row to [`history.csv`](./history.csv) (git-tracked), so you
can diff headline numbers across commits. **Workflow:** run on the baseline commit,
make the change, run again, compare.

## Adding cases

Edit [`../common/src/test/resources/bench/cases.json`](../common/src/test/resources/bench/cases.json).
Each case freezes a context and declares what's acceptable:

```jsonc
{
  "name": "mine_iron_count",
  "request": "去挖10块铁矿石回来",        // the owner's final message (omit for a mid-trajectory case)
  "env": { "dimension": "minecraft:overworld", "owner_name": "Steve" },
  "known_blocks": [ { "type": "furnace", "x": 120, "y": 64, "z": -33 } ],
  "prior": [                              // optional earlier turns
    { "role": "user", "content": "..." },
    { "role": "assistant", "tool_call_id": "c1", "name": "scan_blocks", "arguments": "{...}" },
    { "role": "tool", "tool_call_id": "c1", "content": "{...}" }
  ],
  "expect": {
    "anyOf": ["auto_mine", "equip_item"], // acceptable FIRST tool call
    "args":  { "auto_mine": { "count": 10 } } // optional key-arg checks, keyed by tool
  },
  "note": "why this is the right answer"
}
```

Two kinds of case, both seeded:

- **Hand-authored** — target the confusion points the tool descriptions try to
  separate (`break_block` vs `auto_mine`, `eat_item` vs `interact_at`, reusing a
  `known_blocks` furnace). These double as **regression guards for tool merges**:
  run before/after a merge to prove you didn't break a boundary the model used well.
- **Mined from real logs** — pulled from `fabric/runs/.../conversations/*.jsonl`
  (the live JSONL the mod writes). Reconstruct the context as `prior` (a
  mid-trajectory case omits `request` and ends at a tool result), then set `expect`
  to what the right next move was. See `real_scan_then_act` for the shape.

## Files

- `../common/src/test/java/com/dwinovo/numen/bench/ToolBench.java` — engine (compose prompt, build request, call, score)
- `../common/src/test/java/com/dwinovo/numen/bench/ToolBenchTest.java` — JUnit entry (`@Tag("bench")`), report + history
- `../common/src/test/resources/bench/cases.json` — the cases
- `history.csv` — appended one row per run
