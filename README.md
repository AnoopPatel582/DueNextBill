# DueNextBill

An Android app that tracks credit card bills across all your cards, so a due date is never missed.

It reads the transaction SMS your banks already send, works out each card's current bill and the next bill so far (including EMI installments), and reminds you before the due date. Manual entry is the fallback for anything the SMS doesn't cover.

## Status

Personal project, in development.

| Milestone | Scope | Status |
|---|---|---|
| M0 | Project setup | ✅ Done (`v0.0.1`) |
| M1 | Billing engine: cycles, bills, payment status, reminders | ✅ Done (`v0.1.0`) |
| M2 | Data layer (Room, encryption) and CI | 🚧 In progress |
| M3 | Manual-entry app (Compose UI) | Planned |
| M4 | Bank SMS parsers | Planned |
| M5 | SMS capture | Planned |
| M6 | Reminder notifications | Planned |
| M7 | Release | Planned |

## Privacy

- **Everything stays on the phone.** No server, no account, and no internet permission.
- **Only the bank name and the last 4 digits** of a card are stored. Never the full card number, CVV or PIN.
- **OTP messages are never stored.**
- The database will be encrypted (SQLCipher, with the key held by the Android Keystore).

## How bills are computed

Bills follow the same formula as a bank statement:

```
closing = opening − payments − credits + charges + EMI
```

- Each card has a **statement day** and a **due offset** (e.g. statement on the 8th, due 20 days later). Short months are handled: a statement day of 31 becomes the 30th in April and the 28th or 29th in February.
- Calculation starts from the last **official statement total** (the anchor), and each bill's closing balance becomes the next bill's opening. A real statement always overrides what was computed.
- A spend **converted to EMI** is not billed in full; only its monthly installments (principal + interest + tax) are.
- Spends made in the last days before the statement are flagged as **may move to the next bill**, because banks bill by the date the merchant settles.
- Money is stored as whole **paise in a `Long`**, never as floating point, and is formatted the Indian way (₹1,00,000.00).

## Project structure

| Module | What it is |
|---|---|
| `app` | The Android app (Jetpack Compose UI) |
| `core/engine` | Pure Kotlin, no Android: money, billing cycles, bills, payment status, reminders |
| `core/data` | Room database: stores cards (and later entries, EMI lines and statements) and converts them to engine types |

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite), with KSP code generation
- Gradle (Kotlin DSL) with a version catalog; JDK 25 for the build, Java 11 bytecode
- Tests: JUnit 5, Kotest assertions and property tests, Robolectric for database tests on the JVM
- Min SDK 26 (Android 8.0), target SDK 37

## Build and test

Requirements: Android Studio with the Android SDK. Gradle downloads JDK 25 automatically if it isn't installed.

```bash
./gradlew :core:engine:test                # engine tests
./gradlew :core:data:testDebugUnitTest     # database tests (Robolectric, no phone needed)
./gradlew :app:assembleDebug               # build the debug APK
```

Every pull request runs the full test suite, lint and a debug build on GitHub Actions (`.github/workflows/ci.yml`).

## Development workflow

- One feature branch per piece of work, merged to `main` through a squash-merged pull request.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `ci:`, `docs:`).
- Each milestone is tagged (`v0.0.1`, `v0.1.0`, …).
