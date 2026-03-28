# Safer Image Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move chat attachments to a safer preprocessing pipeline that preserves normal image quality, resizes only oversized images, enforces a 50 MB global cap, and keeps send/retry/edit flows off the UI thread.

**Architecture:** Add draft attachment state in `ChatViewModel`, preprocess attachments immediately after attach into upload-ready files and encoded payloads, persist prepared file paths into messages, and make send/retry/edit flows wait on attachment readiness. Repository request building will consume prepared payloads or encode prepared files on background dispatchers.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, coroutines, Hilt, existing `FileUtils` / `ChatViewModel` / `ChatRepositoryImpl` flow.

---

## Task 1: Add attachment draft models and preprocessing utilities

**Files:**
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatAttachmentDraft.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/util/FileUtils.kt`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/util/FileUtilsTest.kt`

- [ ] Define a draft attachment model with status, resize flag, notice text, prepared file path, and optional prepared payload.
- [ ] Add `FileUtils` helpers for file-size validation against 50 MB, image dimension inspection, conditional resize-to-file, and background-safe base64 encoding from prepared files.
- [ ] Write/expand unit tests for size limits, dimension-threshold decisions, and payload encoding helpers.
- [ ] Run: `./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.util.FileUtilsTest"`
- [ ] Commit the utility/model changes.

## Task 2: Move chat draft state from raw file paths to attachment drafts

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatDialogs.kt` (only if needed for edit flow helpers)
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/` (create a focused unit test if feasible)

- [ ] Replace `selectedFiles` draft state with attachment draft state and keep helper accessors needed by the UI.
- [ ] Start preprocessing immediately when a file is attached.
- [ ] Remove or retry failed attachments through ViewModel actions.
- [ ] Update thumbnail UI to show preparing/failed states and a resize notice for oversized images.
- [ ] Ensure attach-time errors (50 MB rejection, resize/prep failure) surface to the UI.
- [ ] Run the smallest relevant verification command for changed tests or compile.
- [ ] Commit the draft-state/UI changes.

## Task 3: Make send wait for attachment readiness and persist prepared file paths

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/database/entity/MessageV2.kt` (only if extra metadata becomes necessary; avoid if paths are enough)
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImplTest.kt`

- [ ] Update `askQuestion()` so it can enter a sending/loading state while attachments are still preparing.
- [ ] Persist prepared upload-ready file paths into the outgoing `MessageV2.files`.
- [ ] Ensure failed attachments block send until resolved.
- [ ] Keep edited-message send and retry paths compatible with prepared file reuse.
- [ ] Add tests for blank/failed content guards and send gating helpers.
- [ ] Run targeted unit tests.
- [ ] Commit the send-gating changes.

## Task 4: Update repository request building to use prepared payloads/files on background dispatchers

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/util/FileUtils.kt`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImplTest.kt`

- [ ] Move attachment encoding work onto `Dispatchers.IO`.
- [ ] Prefer prepared payloads when available; otherwise encode from stored prepared file paths.
- [ ] Keep MIME lookups minimal and preserve non-resized files at original quality.
- [ ] Preserve existing empty-content guard behavior.
- [ ] Run targeted tests plus compile if needed.
- [ ] Commit the repository changes.

## Task 5: Verify end-to-end behavior

**Files:**
- Modify any files needed from earlier tasks

- [ ] Run: `./gradlew :app:testDebugUnitTest`
- [ ] Run: `./gradlew :app:compileDebugKotlin`
- [ ] Manually verify: attach a normal image, attach an oversized-dimension image, attach a file over 50 MB, tap Send before preprocessing finishes, retry a message with attachments, and edit-send a message with attachments.
- [ ] Commit final polish.
