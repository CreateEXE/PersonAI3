# PersonAI v0.2 — Oni Layer Complete

## What's in this version
- Soul foundation (Soul, SoulGenesis, SoulEngine, Ghost package system)
- System bridge (Accessibility, Notifications, SystemBridgeManager)
- Oni base class + OniHealth enum
- SoulSpark — boss class, owns all Oni
- All 7 Oni: Emotion, Evolution, Awareness, Cognition, Proactive, Interest, Mobility
- InferenceQueue (CRITICAL/NORMAL/BACKGROUND priority)
- InferenceParams + OniHook interface
- MemoryManager (tiered memory structure, personality-driven retention)
- SoulMigration (schema versioning)
- SoulHeartbeatWorker (WorkManager periodic save)

## What's stubbed / next
- LlamaEngine.kt — llama.cpp JNI bridge (wire into SoulSpark.wireInferenceQueue)
- OverlayManager — floating avatar window (wire into Mobility.onOverlayReady)
- Room DB — full episode storage for MemoryManager.DEEP tier
- KV cache soul state fusion (soul encoded into llama.cpp KV cache)

## Setup in Termux
1. Download gradle-wrapper.jar:
   curl -L -o gradle/wrapper/gradle-wrapper.jar \
     https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar
2. chmod +x gradlew
3. ./gradlew assembleDebug

## Permissions to enable manually
- Settings > Accessibility > PersonAI
- Settings > Notifications > Device & App Notifications > PersonAI
- Settings > Privacy > Usage Access > PersonAI (optional)

## Architecture
PersonAIApplication
  └── SoulSpark (boss)
        ├── SoulEngine (persistence)
        ├── InferenceQueue (LLM access — CRITICAL/NORMAL/BACKGROUND)
        ├── Emotion    (mood + OniHook → temperature shaping)
        ├── Evolution  (personality drift, interaction signals)
        ├── Awareness  (device context → soul signals)
        ├── Cognition  (pipeline orchestrator, prompt builder)
        ├── Proactive  (idle monologue, triggered thoughts)
        ├── Interest   (curiosity engine, interest graph)
        └── Mobility   (avatar physics — stub until overlay built)
