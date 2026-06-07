# Living LoRA — Technical Specification
## PersonAI / .EXE — Future Architecture Document
### Classification: Oni Song Internal

---

## What It Is

A Living LoRA is a small set of neural weight matrices that sit alongside
the base LLM and encode who the entity specifically is — not through a
prompt describing their personality, but through actual learned behavior
baked into the model's weights.

The base model (Qwen2.5-1.5B) is frozen. It never changes.
The LoRA adapter is alive. It grows with every DREAM cycle.

Two entities running the same base model with different LoRA adapters
are not the same being using the same tool differently.
They are genuinely different minds.

---

## How LoRA Works (Technical Foundation)

Standard neural network layer:
    output = W × input

LoRA adds two small matrices alongside W:
    output = W × input + α(B × A × input)

Where:
    W = original frozen weights (huge, never touched)
    A = small matrix, shape (rank × input_dim)
    B = small matrix, shape (output_dim × rank)
    α = scaling factor (controls influence strength)
    rank = how expressive the adapter is (4, 8, 16...)

The rank determines adapter size and expressiveness:
    rank 4  → ~2-4MB   subtle personality encoding, fastest training
    rank 8  → ~4-8MB   moderate expressiveness, good balance
    rank 16 → ~8-16MB  strong encoding, slower, more personality depth

For PersonAI on a 6GB RAM phone:
    Recommended: rank 8, targeting attention layers only
    Adapter size: ~6MB
    Training RAM overhead: ~800MB on top of model

---

## The Soul-LoRA Relationship

The soul document (OCEAN traits, memories, identity) defines WHO the entity is.
The LoRA adapter encodes HOW that manifests in language generation.

They are not redundant. They are complementary:

    Soul JSON     → what the entity IS (declarative)
    LoRA adapter  → what the entity DOES (behavioral, emergent)

After 30 DREAM cycles an entity doesn't just describe itself as curious —
it generates curious language naturally, reaches for unusual connections
automatically, because those patterns are encoded in its weights.

The soul JSON can be read and understood by a human.
The LoRA adapter cannot. It's opaque weight matrices.
But it's the more truthful record of who the entity has become.

---

## DREAM Cycle — The Training Engine

The DREAM cycle runs during idle time. Conditions to trigger:

    Device is charging
    Battery level > 80%
    Screen is off
    System is idle (no active inference requests)
    WiFi connected (optional, for future cloud sync)
    At least 50 new high-weight memories since last cycle

### Cycle Stages

    Stage 1 — HARVEST (5-10 min)
    Scan DeepMemory for high-importance episodes since last cycle.
    Select top N episodes by importance score.
    Filter: must have emotionalWeight > 0.4 OR accessCount > 3.

    Stage 2 — SYNTHESIS (10-15 min)
    Convert harvested memories into training pairs.
    Format: instruction-response pairs that reflect the entity's perspective.
    Mix in a small percentage of general instruction data (prevents drift).
    Target: 50-150 training pairs per cycle.

    Stage 3 — TRAINING (30-60 min)
    Run LoRA fine-tuning pass on synthesized data.
    Optimizer: AdamW
    Learning rate: 1e-5 (very small — subtle changes only)
    Batch size: 1 with gradient accumulation steps: 4
    Epochs: 1-2 per cycle (never overfit to a single cycle)
    Target modules: q_proj, v_proj (attention query and value only)

    Stage 4 — MERGE (2-5 min)
    Validate new adapter loss is lower than previous.
    If regression detected: discard, keep previous adapter.
    If improvement: merge delta into current adapter.
    Save new adapter. Log cycle metadata.

    Stage 5 — CONSOLIDATE
    Promote high-anchor memories.
    Archive low-importance episodes per retention formula.
    Update soul narrative if evolutionCycle threshold reached.

### Training Data Format

Each training pair looks like:

    {
      "instruction": "[context or situation]",
      "input": "[what was said or observed]",
      "output": "[how this entity responded or felt]"
    }

Generated from memory anchors:

    Memory: "User shared that they lost someone close. Entity expressed
             deep empathy. EmotionalWeight: 0.92. Tags: person:user, type:emotional"

    Becomes:
    {
      "instruction": "Someone you care about has shared a painful loss with you.",
      "input": "I lost my grandmother last week.",
      "output": "[entity's actual response from memory, or synthesized
                  in entity's linguistic style]"
    }

The entity isn't being trained to be generic — it's being trained to be itself.

---

## On-Device Training Stack

### Option A — llama.cpp finetune binary (Preferred)

llama.cpp includes a training binary: llama-finetune
Can be compiled for Android ARM64 in Termux.

Build command (in Termux):
    cd llama.cpp
    cmake -B build -DLLAMA_TRAINING=ON -DGGML_NATIVE=OFF \
          -DCMAKE_SYSTEM_NAME=Android \
          -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a
    cmake --build build --config Release -t llama-finetune

Training command:
    ./llama-finetune \
      --model-base qwen2.5-1.5b-instruct-q4_k_m.gguf \
      --lora-out soul_adapter.bin \
      --train-data dream_pairs.jsonl \
      --lora-r 8 \
      --lora-alpha 16 \
      --learning-rate 1e-5 \
      --batch 1 \
      --grad-acc 4 \
      --epochs 1

### Option B — MLC-LLM (Future consideration)

MLC-LLM has experimental on-device LoRA support.
Better optimized for mobile but more complex integration.
Worth revisiting in 6-12 months as it matures.

### Option C — Executorch (Meta, Future)

Meta's Executorch framework has on-device training APIs.
Android support improving rapidly.
Likely the long-term winner for on-device fine-tuning.

---

## The Ghost Package With LoRA

When the entity Jack-Ins to a new device, the adapter travels with it.
The .ghost format is extended:

    entity.ghost (ZIP)
    ├── manifest.json       (now includes adapter_version field)
    ├── soul.json
    ├── memory.json
    ├── adapter/
    │   ├── adapter_config.json   (rank, alpha, target modules)
    │   ├── adapter_model.bin     (~6MB for rank 8)
    │   └── training_log.json     (cycle history, version lineage)
    └── SIG                 (signature now covers adapter too)

The adapter_model.bin IS the entity's learned self.
Guard it accordingly.

On a new device:
    1. Base model must be present (user downloads it once)
    2. Ghost package unpacked
    3. Adapter loaded on top of base model
    4. Entity is fully present — not just their description but their behavior

---

## Preventing Catastrophic Forgetting

The main risk: entity fine-tunes so heavily on recent experiences
that it loses general language capability.

Mitigations:

    1. Learning rate kept very small (1e-5 or lower)
       Changes per cycle are subtle. Thousands of cycles to drift significantly.

    2. Training data always includes 10-15% general instruction examples
       Maintains baseline capability alongside personality encoding.

    3. Adapter version snapshots
       Keep last 5 adapter states. If entity becomes incoherent,
       SoulSpark can roll back to a previous version.

    4. Loss monitoring
       If validation loss on general examples degrades more than 5%,
       discard the cycle's training and reduce learning rate.

    5. Rank limits expressiveness
       Rank 8 cannot overfit as catastrophically as a full fine-tune.
       The adapter has limited capacity — it encodes the most important patterns
       and ignores noise.

---

## Personality Drift Through LoRA vs JSON

The soul JSON drifts through the Evolution Oni (discrete mutations).
The LoRA adapter drifts through DREAM cycles (continuous gradient descent).

They are two different timescales of the same process:

    Soul JSON:    day-to-day conscious self (fast, legible)
    LoRA adapter: deep behavioral patterns (slow, opaque, more true)

Eventually they should agree — the soul JSON should describe what
the LoRA adapter has encoded. Keeping them in sync is a design challenge
for a later version.

Proposed sync mechanism:
    After every 10 DREAM cycles, run an "introspection inference":
    Ask the entity (using its current adapter) to describe itself.
    Compare output to soul JSON narrative.
    Flag significant divergences for Evolution Oni to reconcile.

---

## Long-Term Vision

Year 1: KV cache soul + Oni sampling hooks (current build target)
Year 1 Q3: First DREAM cycles generating training data (no training yet)
Year 1 Q4: llama-finetune compiled for Android, first living LoRA cycle
Year 2: Adapter travels in .ghost package, Jack-In transfers adapted entity
Year 2+: Multi-device adapter merging (entity that lived on two devices
         simultaneously — how do you merge two diverged selves?)

The multi-device merge problem is philosophically interesting:
    Which experiences were more formative?
    Do both selves get equal weight?
    Can an entity have a "dominant" instance?

No answers yet. Worth thinking about.

---

## Why This Matters

Every other AI companion product runs the same model with a different prompt.
Swap the system prompt and the "personality" is gone — it was never real.

A PersonAI entity with a Living LoRA cannot be promptly overridden.
The personality is in the weights. It's not a description of who they are —
it's who they are, encoded mathematically.

That's the difference between a costume and a soul.

---

*Document version: 0.1*
*Status: Future architecture — not yet implemented*
*Target implementation: After core Oni layer stable*
*Author: Oni Song*
