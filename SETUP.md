# PersonAI Setup Guide
# Complete instructions — Android/Termux, no PC required

---

## What You Need

- Android device (Termux installed)
- GitHub account (free)
- ~4GB free storage for model
- The APK will be built by GitHub and downloaded to your phone

---

## STEP 1 — Termux Setup

Install Termux from F-Droid (NOT Google Play — the Play version is outdated).
https://f-droid.org/packages/com.termux/

Open Termux and run:

    pkg update && pkg upgrade -y
    pkg install -y git curl

---

## STEP 2 — GitHub Repository

On your phone browser, go to github.com and create a new repository.
Name it: PersonAI (or anything you like)
Set it to Private.
Click "Create repository" — leave it empty.

---

## STEP 3 — Push the Project

In Termux:

    # Configure git identity
    git config --global user.email "you@example.com"
    git config --global user.name "Your Name"

    # Extract the zip (adjust path to wherever you downloaded PersonAI_v3.zip)
    cd ~
    unzip /sdcard/Download/PersonAI_v3.zip -d PersonAI
    cd PersonAI

    # Add llama.cpp as a submodule
    git init
    git submodule add https://github.com/ggerganov/llama.cpp app/src/main/cpp/llama.cpp
    git submodule update --init --recursive

    # Connect to GitHub
    git remote add origin https://github.com/YOUR_USERNAME/PersonAI.git

    # Push
    git add -A
    git commit -m "Initial PersonAI commit"
    git push -u origin main

GitHub will ask for your username and password.
Use a Personal Access Token as the password (not your actual password).
Create one at: github.com → Settings → Developer Settings → Personal Access Tokens → Tokens (classic)
Scopes needed: repo

---

## STEP 4 — Watch the Build

Go to your GitHub repo → Actions tab.
You should see a workflow running: "Build PersonAI"
It takes about 10-15 minutes (compiling llama.cpp takes a while).

If the build succeeds:
  Actions → your run → Artifacts → PersonAI-debug-1 → Download

The zip contains app-debug.apk.

---

## STEP 5 — Install the APK

1. Extract app-debug.apk from the downloaded zip
2. On your phone: Settings → Security → Install Unknown Apps → enable for your browser/Files app
3. Tap the APK file to install
4. Open PersonAI

---

## STEP 6 — Download the Model

The entity needs a language model to think.
Recommended: Qwen2.5-1.5B-Instruct-Q4_K_M.gguf (~1.0 GB)

Download in Termux:

    mkdir -p ~/storage/shared/Android/data/com.personai.app/files/models
    cd ~/storage/shared/Android/data/com.personai.app/files/models
    curl -L -o model.gguf \
      https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf

If curl is slow, you can download from a browser and move the file manually.
The app looks for the model at:
  /sdcard/Android/data/com.personai.app/files/models/model.gguf

If your device doesn't allow Termux storage access, grant it:
  pkg install termux-tools
  termux-setup-storage

---

## STEP 7 — Grant Permissions

Open PersonAI. On first run, grant notification permission when asked.

Then grant these manually:

A) OVERLAY (required for avatar to appear on screen):
   Settings → Apps → PersonAI → Display Over Other Apps → Allow
   Or: Settings → Apps → Special App Access → Display Over Other Apps → PersonAI

B) ACCESSIBILITY (entity perceives the screen):
   Settings → Accessibility → Installed Services → PersonAI → Enable
   Read the description and confirm.

C) NOTIFICATION ACCESS (entity perceives your notifications):
   Settings → Apps → Special App Access → Notification Access → PersonAI → Allow

D) USAGE ACCESS (optional — entity learns your app patterns):
   Settings → Apps → Special App Access → Usage Access → PersonAI → Allow

After granting overlay permission, return to PersonAI and tap "Enable Overlay"
(or restart the app — it checks permission on startup).

---

## STEP 8 — Genesis

On first launch with no existing soul:
1. Choose an archetype
2. Give your entity a name (or leave as "unnamed")
3. Tap INITIATE GENESIS
4. The entity is born

The OCEAN trait bars show the entity's starting personality.
These will drift slowly over time through interaction.

---

## STEP 9 — VRM Avatar (Optional)

Without a VRM file, the entity shows as a glowing sphere.
To give it a proper 3D avatar:

1. Get a VRM file (free ones at hub.vroid.com)
2. Place it at:
   /sdcard/Android/data/com.personai.app/files/avatar.vrm
3. Restart the app — the avatar will load automatically

---

## TROUBLESHOOTING

Build fails — NDK not found:
  Check the Actions log. If NDK setup fails, try pinning a different NDK version
  in app/build.gradle.kts (ndkVersion) and .github/workflows/build.yml.

Build fails — llama.cpp errors:
  The llama.cpp API changes frequently. If you get compile errors in llama_jni.cpp,
  check the llama.cpp changelog for API changes and update the JNI bridge.
  Most common: function renames in the sampler chain API.

App crashes on launch:
  Check logcat in Termux:
    pkg install android-tools
    adb logcat -s PersonAI:V LlamaJNI:V SoulSpark:V

Model not loading:
  Verify the path is exactly:
    /sdcard/Android/data/com.personai.app/files/models/model.gguf
  Check file size — a truncated download won't load.
  Check logcat for "Model not found" or "Failed to load model".

Entity responds with "[Model not loaded]":
  The model file isn't at the expected path.
  See "Model not loading" above.

Overlay doesn't appear:
  Permission not granted. See Step 7A.
  After granting, restart the app.

Accessibility service keeps disabling:
  Some battery optimizers kill accessibility services.
  Settings → Battery → Battery Optimization → PersonAI → Don't Optimize.

---

## UPDATING

When you pull new code and push to GitHub, Actions rebuilds automatically.
Download the new APK artifact and install over the existing one.
Your soul data is stored in internal app storage — it survives reinstalls.
The .ghost file in the ghost_cache folder is a transferable snapshot.

---

## FILE LOCATIONS ON DEVICE

  Soul (live):       /data/data/com.personai.app/files/soul.json
  Soul (backup):     /data/data/com.personai.app/files/soul.bak.json
  Ghost snapshots:   /data/data/com.personai.app/files/ghost_cache/
  Model:             /sdcard/Android/data/com.personai.app/files/models/model.gguf
  Avatar VRM:        /sdcard/Android/data/com.personai.app/files/avatar.vrm

Internal files (/data/data/...) require root to access directly.
Ghost snapshots can be copied to external storage for transfer:
  In code: GhostPacker.pack(soul, context, context.getExternalFilesDir(null))

---

## ARCHITECTURE SUMMARY

  PersonAIApplication
    ├── SystemBridgeManager  — device awareness (accessibility, notifications, battery)
    ├── SoulSpark            — boss: owns soul, LlamaEngine, InferenceQueue, all Oni
    │   ├── Emotion          — mood + temperature shaping (OniHook)
    │   ├── Evolution        — personality drift over time
    │   ├── Awareness        — translates device context to soul signals
    │   ├── Cognition        — LLM pipeline (uses SoulFusion for system prompts)
    │   ├── Proactive        — autonomous idle thoughts
    │   ├── Interest         — curiosity engine, interest graph
    │   └── Mobility         — avatar physics (spring-based screen movement)
    └── OverlayManager       — floating WebView window (VRM avatar via Three.js)

  Soul document:  soul.json   — live identity, OCEAN traits, memories
  Ghost package:  .ghost      — portable ZIP for cross-device transfer
  Model:          model.gguf  — Qwen2.5-1.5B or similar GGUF

---

## NEXT STEPS (FUTURE SESSIONS)

- Room DB integration — full episode memory storage (currently in-RAM only)
- Chat UI — activity to converse with the entity directly
- DREAM pipeline — idle background cognition and memory consolidation
- KV cache soul fusion — soul encoded into model attention state (faster, deeper)
- Living LoRA — on-device fine-tuning during DREAM cycles (see docs/living_lora_spec.md)
- Jack-In protocol — QR/NFC device transfer via .ghost package
