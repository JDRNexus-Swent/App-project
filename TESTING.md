<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->

# JDRNexus - Testing Conventions

How we test, for the team and for AI agents. Every PR that adds or changes code follows these rules (see the Definition of Done in `AGENTS.md`). When a rule must change, change this document in the same PR.

---

## 1. Principles

1. **A test must be able to fail.** Before you submit, break the code under test (flip a condition, return early) and check that your test goes red. A test that stays green proves nothing.
2. **Assert the observable result**: the exact state, value, error or displayed text. Never assert only that "nothing crashed".
3. **Deterministic.** No `Thread.sleep`, no real network, no dependence on test order, the clock or random data you do not control. A flaky test is a failing test: fix it, never retry it.
4. **One behavior per test.** Several asserts are fine when they describe the same outcome.
5. **Cover the error and edge cases**, not only the happy path: blank ids, missing documents, repository failures, empty lists, the 500-write batch limit.

### Forbidden

| Don't | Do instead |
|---|---|
| `assertTrue(true)`, or a test without any assert | Assert the expected value. |
| `assertNotNull(x)` alone | `assertEquals(expected, x)` |
| `assertTrue(list.isNotEmpty())` / `assertTrue(a == b)` | `assertEquals(listOf(a, b), list)` / `assertEquals(b, a)`: the failure message shows both values. |
| `try { … } catch (e: Exception) {}` around the code under test | `assertThrows(X::class.java) { … }` and assert on the message, or assert on the returned `Result` / `AuthResult.Failure`. |
| `@Ignore`, or a commented-out test to make CI pass | Fix the test or the code. |
| `Thread.sleep` / `delay` to "wait a bit" | Coroutine test dispatcher (VM), `waitFor` / `pumpMainLooperUntil` (emulator), `waitUntil` (Compose). |
| Copy the production logic into the test to compute the expected value | Write the expected value literally. |
| Tests written only to raise coverage | Coverage is a consequence. Each test checks a behavior a user or a caller relies on. |

---

## 2. Where tests live

| Source set | Runner | Runs in | Use it for |
|---|---|---|---|
| `app/src/test/` | JUnit + Robolectric | `./gradlew check` (CI, every PR) | Almost everything: pure logic, VMs, repositories, Compose screens. |
| `app/src/androidTest/` | Device / emulator | `./gradlew connectedDebugAndroidTest` (CI) | End-to-end flows (`endToEnd/`) and what Robolectric cannot render. |

- **Mirror the package of the class under test**: `ui/dice/DiceViewModel.kt` → `app/src/test/java/.../ui/dice/DiceViewModelTest.kt`.
- Shared test helpers go in `utils/` of the source set (e.g. `utils/FirebaseEmulatorTestUtils.kt`). Do not copy a helper into a second test file: move it to `utils/`.

---

## 3. Splitting test classes

- **One test class per production class**, named `<ClassUnderTest>Test`.
- Split by **kind of dependency**, with a suffix, when a class is tested in two ways:

| File | Contains |
|---|---|
| `GroupRepositoryFirestoreTest` | The repository against the **Firestore emulator**. |
| `AuthRepositoryFirebaseTest` | The repository with a **fake client** (no emulator). |
| `AuthRepositoryFirebaseEmulatorTest` | The same repository against the **Auth emulator**. |
| `CharacterEditionE2ETest` | A user flow across several screens (`androidTest/endToEnd/`). |

- When a class grows past ~500 lines, split it **by feature**, not by helper: `WorkspaceRepositorySharingTest`, `WorkspaceRepositoryDeletionTest`. Shared builders go in `utils/`.
- **Test names** describe the behavior in camelCase: `revealFailsWhenParentFolderIsHidden()`, `playerOnlySeesWorkspaceAction()`. Read as a sentence: _subject + expected result + condition_. No `test1`, no `testRepository`.
- Inside a test, use the **arrange / act / assert** order, separated by a blank line.

---

## 4. How to test each layer

### 4.1 Pure logic (`CascadeRules.kt`, codecs, `visibleInGroup`, dice logic)

- Plain JUnit, no Robolectric, no emulator, no mocks.
- This is where the rules of the wiki page _Data Architecture & Conventions (Firestore)_ are tested exhaustively: every invariant of its section 11 marked "Test" has at least one test here or in 4.2.
- Build inputs with small local builders (`groupFile(id, parentId, hidden = true)`) so each test shows only what matters.

### 4.2 Repositories (`model/`, `*Firestore` / `*Firebase`)

- **Firestore behavior is tested on the emulator**, not with mocks: queries, batches, security rules, `arrayUnion`. A mock reproduces what you think Firestore does, the emulator what it does.
- The emulator runs the real security rules (`firebase.json`), so every test acts as a signed-in user. Use `utils/FirebaseEmulatorTestUtils`:
    - `@Before`: `firestore = FirebaseEmulatorTestUtils.signInAs(uid)` for the user the test acts as (the owner, the GM…), then build the repository with it. Another `signInAs(otherUid)` gives a second user in the same test.
    - Starting state the test does not test (a group, corrupted data): `writeBypassingRules(firestore, path, data)`; a user's group membership, which the rules read: `writeUserGroupIdsBypassingRules(firestore, uid, groupId…)`. The code under test always goes through a `signInAs` instance.
    - Checks on what is stored, whoever may read it: `readBypassingRules(firestore, path, Type::class.java)`; on collections no client may list: `documentIdsBypassingRules`; a user's `groupIds`, read as that user: `userGroupIds(uid)`.
    - `@After`: `runBlocking { tearDown(firestore, "documents", "campaign_groups") }`, listing every collection the test writes. It also empties the cache of the test clients.
    - Wait on listeners with `flow.waitFor { condition }` and on callbacks with `pumpMainLooperUntil { … }`.
- After every write operation, **read the documents back** and assert the stored fields and invariants (e.g. `placements.keys == sharedGroupsIds`), not only that the call returned success.
- Test each documented failure (`"Reveal the parent folder first."`, item not in group, above 500 writes) by asserting the error **and** that nothing was written.
- **Security rules** are tested in `firestoreRules/`, one class per collection or group of related rules, named after what it covers, with direct Firestore calls (no repository, which never sends a forbidden request). Each rule gets an allowed case and a denied one: `assertPermissionDenied("case") { … }` fails unless the rules answer PERMISSION_DENIED, then check that nothing was written. Denied variants of one rule go in one test, each with its own case name.
    - The emulator reloads `firestore.rules` only for the project given to `--project`, while the tests use the project of `google-services.json`: after editing the rules, restart the emulator before running the tests, and run them with `--rerun` (Gradle does not see the rules file as a test input).
- `mockk` of Firestore classes is acceptable only for paths the emulator cannot reach (a listener error, a thrown exception). Keep those few tests in the same class.
- Repositories that wrap Firebase behind an interface (`FirebaseAuthClient`) get a fake of that interface for their translation logic, plus an emulator test class for the real errors.

### 4.3 ViewModels

- **No Firebase, no emulator, no Robolectric.** Pass **fake repositories** implementing the interfaces through the constructor (the same reason ViewModels never import Firebase).
- Fakes are small in-memory classes with controllable results (`MutableStateFlow` of data, a `CompletableDeferred` to hold an operation, a field to inject a failure). Prefer them to `mockk`: they fail at compile time when the interface changes.
- Replace `Dispatchers.Main` with a test dispatcher through a `TestWatcher` rule, and run tests in `runTest { … }`.
- Advance the test scheduler explicitly:
  - `runCurrent()` runs what is already scheduled, without moving virtual time. Use it by default.
  - `advanceUntilIdle()` runs until no task is left. Use it only when the work under test eventually finishes. If the VM polls, retries or repeats a `delay`, it never returns: use `advanceTimeBy(ms)` followed by `runCurrent()` instead.
  - With `SharingStarted.WhileSubscribed(timeout)`, avoid `advanceUntilIdle()`: it skips past the timeout and can stop the upstream. Keep a subscriber active and advance by a specific amount.
- Launch collectors that never complete (e.g. collecting a `StateFlow` to record its emissions) in `backgroundScope`, never directly in the `runTest` scope, or `runTest` fails because the coroutine is still active.
- Assert on the exposed `StateFlow` value **at each step**: the loading state, then the final state, then the error state. For actions, also assert what reached the fake repository (the arguments it was called with).
- Test the `Factory` only if it holds logic.

### 4.4 UI (Compose screens)

- Use `createComposeRule()` and find nodes **by test tag from `C.Tag`** (`onNodeWithTag(C.Tag.x)`). Add missing tags to `resources/C.kt`, never as inline strings in the screen or the test.
- Render the screen from a **fixed UI state** or a **fake VM** (`DiceInterfaceVM`, `EditFileInterfaceVM`): screen tests check the display and the callbacks, not the VM logic.
- Assert both sides: what is displayed (`assertIsDisplayed`, `assertTextEquals`, `assertIsNotEnabled`, `assertCountEquals(0)` for what must be absent) and what an action triggers (the navigation lambda received `"group-42"`, the VM method was called).
- Wait for asynchronous UI with `composeTestRule.waitUntil(timeoutMillis) { … }`, never with `sleep`.
- Navigation tests use `JDRNexusApp(authViewModel = …)` with a fake auth repository.

### 4.5 End-to-end (`androidTest/endToEnd/`)

- Few, and only for the main user flows of a milestone (sign-in, character edition…).
- Run against the Firebase emulators (`TestFirebaseEmulators.configure()` in `@BeforeClass`), seed the data the test needs and remove it afterwards.

---

## 5. Test data

- Use readable constants (`GROUP_ID`, `GM_ID`, `"The Sunken Crown"`) declared once in the class or its companion.
- Emulator tests generate unique ids or emails (`UUID.randomUUID()`) so they never collide, and clean every collection they touch.
- Never point a test at the real Firebase project. `signInAs()`, `getAuth()` and `getFirestore()` refuse to run if the instance is not the emulator: do not bypass them.

---

## 6. Before submitting

```bash
./gradlew ktfmtFormat
firebase emulators:exec --only auth,firestore './gradlew check' --project demo-jdrnexus
```

Checklist:

- [ ] Every new or changed public behavior has a test, including its failure cases.
- [ ] I broke the code once and saw each new test fail.
- [ ] No forbidden pattern of section 1.
- [ ] Tests are in the mirrored package, named after the behavior, split as in section 3.
- [ ] The test file starts with its `Co-authored-by` line if an AI wrote it.

---

## 7. Known gaps

- Fakes (`FakeAuthRepository`, `FakeGroupRepository`, `FakeWorkspaceRepository`) are duplicated as private classes in several test files. New tests should reuse a shared copy in `utils/` once one exists; moving them is a separate PR. The main-dispatcher rule is shared in `utils/MainDispatcherRule.kt`; `GroupWorkSpaceViewModelTest` still has its own copy.
- Test naming is mixed (camelCase, `snake_case`, backticks). New tests use camelCase (section 3); existing ones are renamed only when touched.
- `ExampleUnitTest`, `ExampleRobolectricTest` and `ExampleInstrumentedTest` are template leftovers that assert nothing about the app.

---

_Note: this document was written with the help of Claude Opus 5.5._
