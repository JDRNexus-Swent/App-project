# JDRNexus - Data Architecture & Conventions (Firestore)

This document defines the architectural decisions and invariants of the NoSQL data model used in JDRNexus. All pull requests and new features must adhere to these rules. When a rule must change, change this document in the same PR.

Sections marked **(proposed)** are defaults that still need a team decision. Section 12 lists what is known to be missing

**Out of scope for now:** moving a document between folders, splitting an operation over several batches.

**Not described here:** the game session (`campaign_groups/{groupId}/gm_data`) and the lore (`campaign_groups/{groupId}/gm_lore`). They are mentioned in some sections (permissions, rules), but their data model and exact behaviour are not documented in this file yet.
 
---

## 1. Data model

### 1.1 One flat collection

All items (text files, character sheets, folders, group roots) are stored in a single flat collection `documents` as `JDRFile` entities. Their behavior and UI representation are dictated by `type`.

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Document id. Never `""`. |
| `ownerId` | `String` | The user who owns the document. Only the owner can delete it. |
| `name` | `String` | Display name. |
| `type` | `DocumentType` | `FOLDER`, `TEXT`, `CHARACTER`, `GROUP_ROOT`, `IMAGE` (1.9). |
| `personalParentId` | `String` | Parent container in the owner's personal space. `""` = personal root. |
| `placements` | `Map<groupId, Placement>` | **Source of truth** for group placements: at most one entry per group. |
| `sharedGroupsIds` | `List<String>` | **Derived** from `placements.keys`. Used only for indexing and security rules. |
| `content` | `String` | Empty for containers. For an `IMAGE`, the Storage path of its picture (1.9). |

```
Placement = { folderId: String, isHidden: Boolean }
```

- `folderId` is the container that holds the document **inside that group** (the group root id for top-level items).
- The stored keys are exactly `folderId` and `isHidden` (`Placement.isHidden` carries a `@get:PropertyName("isHidden")` annotation, otherwise Firestore would store it as `hidden`). Queries and security rules rely on these names.
- Personal placement is **not** stored in `placements`. It lives in `personalParentId`, so there is no reserved `"personal"` key and `isHidden` never applies to the personal space.
### 1.2 Containers

A container is a document of type `FOLDER` or `GROUP_ROOT`. Only containers may be referenced as a parent (`personalParentId` or `Placement.folderId`). Code must use `isContainer()`, never compare against `FOLDER` directly.

### 1.3 Conventions for "empty" values

- **No `null` in stored documents.** Every field always has a value.
- `personalParentId == ""` means "personal root". `""` is queryable in Firestore. It must never be used as a document id. Prefer a named constant over the literal when writing new code.
- Empty collections are `emptyList()` / `emptyMap()`, meaning "none".
- The root of a group is identified by its id (the `groupId`), never by an empty value.
- `groupId` must not contain `.`, `/` or other special characters, since it is used as a map key and in dotted field paths (`placements.<groupId>.folderId`). Auto-generated Firestore ids satisfy this.
### 1.4 Identifier uniqueness

Document ids come from Firestore's automatic id generator (`WorkspaceRepository.getNewUid()`) and group ids from the same generator (`GroupRepository.getNewGroupId()`). A group root reuses its `groupId` as document id. Uniqueness relies on the randomness of these ids: nothing enforces it, and the group root is written with a plain `set`, so a collision would overwrite a document (negligible probability). Ids must never be derived from user input.

### 1.5 Legacy data

The move from `parentFolderIds` to `placements` is a **breaking change**. Documents written with the old schema (a `parentFolderIds` list, group roots typed `FOLDER`) are not supported and no migration exists: the project only holds test data, which is wiped.

### 1.6 Group membership list (`users/{userId}`)

`users/{userId}` holds one field, `groupIds: List<String>`: the groups the user belongs to. It is a copy of `participantIds`, kept per user, because security rules cannot search: they read this document to know which groups the caller is in (reading shared documents, dice rolls, activities and shared lore, adding a document to a group).

- A user can only read their own document.
- It must always agree with the `participantIds` of the groups, so it is written **in the same batch** as the group: `createGroup` (the GM), `addUserToGroup` and `removeUserFromGroup` (the user concerned), and `joinGroupWithCode` (the user who joins, see 1.7). The rules refuse the batch otherwise, and refuse any change that disagrees with the groups. One group is added or removed per write.
- Groups created before this field existed are not supported (same policy as 1.5): wipe them, or backfill the field.

### 1.7 Invitations (`invitations/{code}`)

A Game Master shares a short code; a signed-in user who knows it adds themselves to the group. Knowing a valid code is the only way in for someone who is not already a member.

#### Data shape

| Where | Content |
|---|---|
| `invitations/{code}` | `{ groupId, createdBy }`. The code is the document id. Never `""`. |
| `campaign_groups/{groupId}.inviteCode` | The group's current code. |
| `invitations/{code}/redemptions/{uid}` | `{ groupId }`. The "ticket" written by a user when they join with the code. |

- **The code** has 8 characters drawn from a `SecureRandom`, from the alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no `0/O`, `1/I`, nor `L`). User input is normalized (trimmed, uppercased) and accepted with 6 to 8 characters. Generation and validation live in `InvitationCodes` (pure, tested without the emulator).
- **`inviteCode` is not part of `CampaignGroup`.** Only `GroupRepositoryFirestore` reads and writes it. It is absent until the first `createInvitation`, and never `null` or `""`. `updateGroup` writes `toMap()`, which never contains it, so it is not overwritten. `createGroup` writes the whole object, so it must only be used for new groups.
- **One valid code per group.** The group points to its invitation (`inviteCode`) and the invitation points to its group (`groupId`). The rules refuse a pointer without its target and a target without its pointer (see below).

#### Operations (`GroupRepository`)

The repository does not authenticate: the caller passes the user id (like `addUserToGroup`) and the rules check `request.auth.uid`. A blank id gives `NOT_SIGNED_IN`. Every failure is `Result.failure(InvitationException(error))`.

| Operation | Writes (one atomic unit) |
|---|---|
| `createInvitation(groupId, gameMasterId)` | Transaction: read the group, then create `invitations/{code}` and set `inviteCode`. Returns the existing code if there is one, writing nothing, so two concurrent calls cannot create two codes. |
| `regenerateInvitation(groupId, gameMasterId)` | `WriteBatch`: delete `invitations/{old}`, create `invitations/{new}`, update `inviteCode`. The old code stops working. Acts like `createInvitation` if the group had no code. |
| `joinGroupWithCode(code, userId)` | `WriteBatch`: create `redemptions/{uid}`, `arrayUnion(uid)` on `participantIds`, `arrayUnion(groupId)` on `users/{uid}.groupIds` (merge). Only the current user is added, never a whole list. |

- **"Already a member" is checked before writing**, on `users/{uid}.groupIds`: a non-member cannot read the group, and this is the list the rules trust (1.6). The GM is detected the same way.
- A revoked code and a code that never existed both give `UNKNOWN_CODE`. A group that does not exist gives `GROUP_NOT_FOUND`. If the group was deleted but its invitation survives, nothing is written. Under the security rules the join batch is refused (`PERMISSION_DENIED`, because the redemption rule cannot read the missing group), which `joinGroupWithCode` reports as `NETWORK`. Without the rules (repository tests), the group update fails and the call returns `GROUP_NOT_FOUND`.
- If the join batch succeeds but the final read of the group fails, the call returns `NETWORK` although the user is a member, and a retry returns `ALREADY_MEMBER`. Callers should rely on `getUserGroups` to know whether the user joined.

#### Security rules

- `invitations/{code}`: any signed-in user may `get` one, nobody may `list` (a code can be used by someone who knows it, not discovered). It is never updated: it is deleted and recreated.
- **Create** an invitation: only the GM of the target group, shape exactly `{ groupId, createdBy }`, `createdBy == uid`, and the group's `inviteCode` equals the code **after** the batch (`getAfter`). **Delete**: only the GM of the invitation's group.
- **GM edit of `inviteCode`** (`isInviteCodeEdit`): a separate case from the metadata edit. Only the GM, only the `inviteCode` field, a non-empty string, and the invitation it names exists after the batch and points to this group (`getAfter`).
- **Redemption**: only its own user (`{uid} == request.auth.uid`), shape `{ groupId }`, the invitation exists (`get`), `groupId` is the invitation's, and the code is the group's **current** `inviteCode`. `create` and `update` are allowed, `delete` and `read` are not. Because `update` is allowed, a user who left a group can rejoin with the same code as long as it has not been regenerated: this is intended. A GM who wants to keep someone out regenerates the code.
- **Self join** (`isSelfJoin`): a non-GM updates a group only by adding **their own uid** to `participantIds` (exactly one added, none removed, no other field changed). The code used is the group's current `inviteCode` read before the write, never a value typed by the user, so an old code is useless. The redemption must exist after the write (`existsAfter`) and the group must be in `users/{uid}.groupIds` after the write (`getAfter`). The new uid is expected in last position, which is what `arrayUnion` does (same assumption as `isParticipantAdded`).
- The redemption exists because rules cannot see which code a user typed. Writing it in the **same batch** as the membership turns "this user knows a valid code" into a document the rules can read. The chain is: valid current code, then redemption allowed, then membership allowed. The order of the writes inside the batch does not matter, `existsAfter` reads the final state.
- Reads of other documents by the rules count towards the limit of document-access calls (20 per batched write). A join makes about 6 to 8, see section 12.

### 1.8 Profiles (`profiles/{userId}`)

`profiles/{userId}` holds the profile a user shows: `username`, `bio` and `pictureUrl` (all `String`, `""` = not set, see 1.3). The document id is the user's uid. It is separate from `users/{userId}`, whose rules only accept `groupIds` (1.6).

- **Only the owner reads and writes it for now.** The `read` rule must be widened when other users need to see a profile (e.g. the members of a shared group).
- A user who never saved a profile has no document: `ProfileRepository.getProfile` returns empty fields, and the profile ViewModel shows the sign-up username (Firebase Auth display name) until the first save.
- `updateProfile` writes `username` and `bio` with a merge: it creates the document on the first save and never overwrites `pictureUrl`.
- `uploadProfilePicture` needs a saved profile. It shrinks the picked image to a JPEG of at most 256 px, stores it in Firebase Storage at `profilePictures/{userId}.jpg` (a new upload replaces the old file) and saves its download URL in `pictureUrl`. The Storage rules only let the owner read or write that file, as a JPEG under 1 MB; every other path is denied, except the pictures of images (1.9).
- The rules refuse any other field, a username that is not trimmed or outside 3 to 30 characters, and a bio above 300 characters. The profile ViewModel checks the same limits. Deletion is refused (it comes with account deletion, section 9).
- **Stats are not stored.** `getProfileStats` counts on the server (`count()`), filtering on the caller as the rules require: groups with `participantIds` containing the user (joined) and also `gameMasterId` equal to the user (created), and the user's own `TEXT` and `CHARACTER` documents (folders and group roots are not files). These filters need no composite index.

### 1.9 Images (`IMAGE` documents and `images/{ownerId}/{fileId}` in Storage)

An image is two parts:

1. the picture, in Firebase Storage at `images/{ownerId}/{fileId}`;
2. a normal `JDRFile` in `documents/{fileId}` with `type == IMAGE` and `content == "images/{ownerId}/{fileId}"` (the path, never the picture).

Because the second part is a normal document, placements, hiding, sharing and unsharing (sections 4 to 7) work on images unchanged.

- **The path is locked by the Firestore rules.** On create, an `IMAGE` document's `content` must be `'images/' + uid() + '/' + docId`. On update, its `content` never changes; renaming stays allowed (`isOwnEdit`). The stored path can therefore be trusted by the app and by the Storage rules.
- **Storage rules for `images/{ownerId}/{fileId}`:**
    - create: only the user `ownerId`, as `image/jpeg`, `image/png` or `image/webp`, at most 10 MiB (10,485,760 bytes, `<=`). No overwrite (`resource == null`) and no metadata update (no `update` rule);
    - read: the owner, or a signed-in user when `documents/{fileId}` exists, its `type` is `IMAGE`, its `ownerId` equals the `ownerId` of the path, and its `sharedGroupsIds` contains one of the reader's `users/{uid}.groupIds` (1.6). A hidden placement still counts: hidden is a display preference, not a security boundary (5). A user without a `users` document is refused;
    - delete: only the owner.
- **Two documents per check.** The read rule reads `documents/{fileId}` and `users/{uid}` with `firestore.get` / `firestore.exists`. Storage rules may read at most two Firestore documents per evaluation (Firebase Blog, "Announcing cross-service Security Rules", 2022-09-22); repeated calls on the same document do not count again.
- **Download URLs stay valid.** The rules check who asks for a download URL. After that, anyone holding the URL can load the picture, also after leaving the group, like profile pictures (1.8). Accepted: pictures are not sensitive.
- **First deploy:** deploying these Storage rules asks once to let Storage read Firestore (cross-service rules).
- **Emulator:** the Storage emulator reads Firestore under the project given to `firebase emulators:exec --project` (`demo-jdrnexus`), not the project of `google-services.json` that the app writes to. The instrumented Storage rules tests (`storageRules/`) therefore write the documents the rules read directly there, with `StorageEmulatorTestUtils`.

---

## 2. Group root (pseudo-root)

Every campaign group has one root document, created with the group.

- `id == groupId`, `type == GROUP_ROOT`.
- `ownerId` is the GM at creation time. **It is never used for authorization.** Authorization uses the group's `gameMasterId` / `participantIds`.
- `name` is a **copy of the group name**. The group document is the source of truth.
- **Creation:** `GroupRepository.createGroup` writes the group, its root and the GM's `users/{uid}` document (see 1.6) in **one batch**, so a group never exists without its root, nor a GM without membership.
- **Renaming:** `GroupRepository.updateGroup` updates the group and renames the root in the same batch. It fails if the root does not exist (a group without root is a broken state, better seen than hidden).
- `sharedGroupsIds == [groupId]` so members can read it through the standard group read rule.
- `placements` is empty. This is the single exception to the invariant "keys of `placements` == `sharedGroupsIds`" (see 4).
- It has **no functional personal placement**: its `personalParentId` defaults to `""` to satisfy the no-null rule, but it never appears in a user's personal space because personal queries (`getPersonalFolderFiles`) explicitly exclude `GROUP_ROOT` through a shared repository filter.
- Top-level items of a group have `placements[groupId].folderId == groupId`.
- It is immutable for users: `addItemToGroup`, `setItemHidden`, `removeItemFromGroup` and `deleteFile` all refuse a `GROUP_ROOT`. Its name only changes with the group's.
---

## 3. Creation and ownership

1. Every document except `GROUP_ROOT` has exactly one `ownerId` and exists in its owner's personal space (`personalParentId` always has a value).
2. A group does not own files. It is a view by reference.
3. Creating a document directly inside a group is **one atomic operation**: the new file is built with its group placement (`JDRFile.withPlacement`) and written by a single `createFile`. There are no orphan files.
4. The creation target must be a container the creator can see in that group.
---

## 4. Placements and uniqueness

- A document appears at most once per group, because `placements` is keyed by `groupId`. Duplication inside a group is impossible by construction.
- Invariant: `placements.keys == sharedGroupsIds` (as sets), except for `GROUP_ROOT`. Security rules enforce it on every write.
- Invariant: **if a document is in a group, its parent in that group is in that group** (the chain ends at the group root). This is what makes tree traversal from the root reliable.
- The personal tree and each group tree are **independent**. Sharing or unsharing never changes `personalParentId`.
### How placements are written

`placements` and `sharedGroupsIds` always change **together**, never one without the other. Two mechanisms, for two different situations:

| Situation | Mechanism |
|---|---|
| A **new** document | `JDRFile.withPlacement` builds an in-memory copy that is written whole by `createFile`. |
| A document that **already exists** | A targeted update in the repository (`addPlacementUpdate`, `removePlacementUpdate`, `hiddenUpdate`) that touches one group only: it writes `placements.<groupId>` and uses `arrayUnion` / `arrayRemove` on `sharedGroupsIds` in the **same** update. |

Why not the helpers for an existing document: sending a previously read `placements` map could overwrite concurrent changes (like a GM hiding the file), accidentally revealing secrets. For the same reason, **`updateFile` never writes `placements` or `sharedGroupsIds`**: saving an edit made from an older copy must not undo visibility changes made meanwhile.

Never use `arrayUnion` / `arrayRemove` on `sharedGroupsIds` alone, and never write these fields by hand outside these mechanisms.

### Queries

- Everything in group `G`: `whereArrayContains("sharedGroupsIds", G)`.
- Children of `F` in `G`: `array-contains G` **and** `whereEqualTo("placements.G.folderId", F)` (equality only, no composite index per group). Repository function: `getGroupFolderFiles(folderId, groupId)`.
- Children of `F` in a personal space: `ownerId` **and** `personalParentId` in the same query (security rules cannot authorize the query otherwise). Repository function: `getPersonalFolderFiles(folderId, ownerId)`.
- Sorting is done client-side. Do not create composite indexes per `groupId`.
- Never fetch a broader set than the security rules authorize: a list query that could return an unreadable document fails entirely. The query must be designed so the rules accept it. Client-side filtering is never a security boundary.
---

## 5. Visibility ("Secret GM")

- `Placement.isHidden` marks a document as not yet revealed to other participants of that group.
- The GM can prepare lore, monsters or rules with `isHidden = true`, then toggle the flag to reveal them during the session.
- **`isHidden` is a display preference, not a security boundary.** Hidden documents are downloaded by every member and filtered client-side. Never store sensitive data relying on it.
- Invariant: **a visible document has a visible parent** in that group. Hiding a container hides its descendants. Revealing a container does not reveal its children. Enforced by every write operation.
- The owner of a document toggles its own placement. The GM may hide any document in the group, but only reveals their own. The rules do not enforce this (see 7.5): any member can change a placement.
### Operations

- `setItemHidden(itemId, groupId, isHidden)`:
    - **Hide:** the item and everything below it in the group tree become hidden (items already hidden are skipped, not rewritten).
    - **Reveal:** only the item is revealed, and only if the folder holding it is visible, otherwise the call fails with "Reveal the parent folder first." The group root counts as visible.
    - Already in the requested state: success, nothing written.
    - Fails for a `GROUP_ROOT`, for an item that is not in the group, and above 500 items.
- Anything added or reattached under a hidden folder becomes hidden. Nothing is ever revealed implicitly.
### The filter

- `List<JDRFile>.visibleInGroup(groupId, userId, isGameMaster)` is the **single** place where hidden items are filtered. A hidden item is kept for the GM and for the item's owner; being hidden in another group changes nothing.
- Repositories never apply it (the GM must keep seeing what they hide). View models call it, in one place.
### Why the filter is not a Firestore query

- It depends on who looks (GM and owner see hidden items), which the repository does not know. A query would need an `OR` or two merged listeners.
- It would not protect anything: the rules cannot enforce it on a group query (the group id is a dynamic map key), so a modified client could drop the filter.
- The only gain would be less data downloaded, which is small. If real secrecy is ever needed, it needs a different data model plus rules, not a query filter.
---

## 6. Explicit sharing and container isolation

- Sharing a container does **not** share its current or future children, unless the user asks for it. A new personal draft added to a shared personal folder stays personal until explicitly shared.
- To share a document, the target must be a container already in that group (or the group root).
- A user can only share documents they own (enforced by the security rules, not yet by the client code).
- Moving a document already in the group to another folder is out of scope: adding an item that is already in the group is refused.
### `addItemToGroup(itemId, groupId, folderId, isHidden, includeContent)`

- **Hide** (isHidden): if true, the placement starts hidden for the other players. Default: false (visible).
- **Include content** (`includeContent`, folders only): the whole personal subtree of the folder is added in **one batch**. Default: off, so sharing stays explicit.
- Checks, in this order: ids not blank, item exists, item is not a `GROUP_ROOT`, item is not already in the group, destination exists, destination is a container, destination belongs to the group.
- If the destination is hidden, Hide is **forced on**.
- Without `includeContent`, a folder arrives empty.
- With `includeContent`, the personal structure is mirrored: the folder goes into the destination, and every descendant goes into the group folder that matches its personal parent.
    - An item already in the group keeps its place and its visibility; its own content is placed under it.
    - If Hide is on, every new item is hidden. A new item under a hidden item is hidden too.
    - Only the owner's personal tree is walked.
- The operation counts the folder and everything added. Above 500 it is refused and nothing is written.
---

## 7. Unsharing and deletion

### 7.1 Principles

- A document is unique, so deleting it removes it **globally**.
- **Only the owner deletes.** The GM cannot delete a player's document, but can unshare it from the group.
- Unsharing never deletes a document. The document stays in its owner's personal space.
- Firestore has no cascades. Every cascade below is performed by the repository inside a single `WriteBatch`.
- **Nothing owned by someone else is ever deleted or silently lost by a cascade**: it is reattached.
### 7.2 Unsharing a container (`removeItemFromGroup`)

The cascade follows the **group tree**. When a container `F` leaves group `G`:

1. We go down from `F` through `placements[G].folderId` and **stop at the first item owned by someone else**. `F` and the items of the **same owner as `F`**, linked to it by an unbroken chain, form the leaving set `L`.
2. Every document in `L` loses its placement in `G` (`placements` and `sharedGroupsIds` updated together).
3. Every item of another owner found directly under a folder of `L` is **reattached** to the folder that held `F` in `G`. Everything under it stays under it, untouched, even items of `F`'s owner.
4. A reattached item becomes hidden if the new parent is hidden. It is never revealed.
   Example: Marie unshares "Dungeon". It holds her "Map", her folder "Room" (with "Goblin"), and Paul's folder "Workshop" (with a file of Marie's). "Dungeon", "Map", "Room" and "Goblin" leave. "Workshop" is reattached to the folder that held "Dungeon", and Marie's file stays in it.

The "same owner" is the owner of the **container being unshared**, not the user who triggers the action. If the GM unshares a player's folder, the player's items leave and the GM's and other players' items are reattached.

### 7.3 Unsharing a single item

- Applies to **one group `G`** only. Placements in other groups are untouched.
- A non-container: its placement in `G` is removed. Nothing else changes.
- A container: rule 7.2.
- Who can unshare: the owner (own documents) and the GM (any document of the group). `GROUP_ROOT` can never be unshared. The item must be in the group.
### 7.4 Deleting (`deleteFile`)

- Fails for a `GROUP_ROOT` and for an unknown id.
- A **file** is deleted alone: it has no content below it, so nothing is reattached.
- A **folder** `F`: the deleted set `D` is `F` plus all its descendants **in the owner's personal tree** (all owned by the same user). `D` is deleted. The cascade follows the **personal tree**.
- In every group where a document of `D` is placed, that document leaves the group. Items that were under a deleted folder **in the group tree** and are not in `D` are **reattached**, never deleted, to the nearest ancestor above the deleted folder that remains (the group root if none). This applies to items of other owners and also to items of the owner that are not below `F` in the personal space.
- Reattached items follow the hidden rule of 7.2.
  Example: in Alice's personal space, "D" is at the root and "K" holds "F". In the group, "K" holds "F", Bob's "V" and "D". Deleting "K" deletes "K" and "F". "V" and "D" both stay in the group, reattached to the nearest ancestor of "K". "D" is not removed from the group because it was not linked to "K" in the personal space.

The two cascades differ on purpose: unsharing a folder follows what is **below it in the group**, while deleting follows what is **below it in the personal space**. Deleting a folder from a personal space must not remove from a group a file that is not linked to that folder there.

The UI must ask for confirmation before deleting a folder, showing how many documents are deleted.

### 7.5 Security-rule exceptions

Normally only the owner can write a document. The cascades above write documents the actor does not own, so the rules contain two exceptions on top of the owner's own edits. In both, nothing but `placements` and `sharedGroupsIds` can change.

1. **Unshare (owner or GM):** a document leaves group `G` when `G` is removed from `placements` and `sharedGroupsIds` in the same write. Allowed for the owner, and for the GM of `G` on any document of the group.
2. **Placement entry change (any member of `G`):** exactly one entry of `placements` changes, in a group the caller belongs to, and the set of groups does not change. This is what lets a cascade reattach other users' documents (new `folderId`) and hide their descendants (`isHidden`).

**Known and accepted limit of exception 2.** The group id is a map key, and the rules cannot extract it from a diff, so they cannot check who changes what, nor what the entry becomes. Any member of `G` can therefore change the placement of any document of `G`: reveal a hidden one, hide a visible one, or point it to another parent. On this path the rules do not check the new parent, nor the "visible child, visible parent" invariant. They still guarantee that nothing else of the document changes: content, name, owner and the list of groups stay out of reach. This is accepted because `isHidden` is not a security boundary (section 5) and every member can already read the document. The effect is limited to display and tree structure.

**Why it is this broad.** The narrow version (reattach only if the previous parent has left the group, new parent a container of `G`, `isHidden` only from `false` to `true`) needs the rules to name the group whose entry changed, which they cannot do.

**Document-access limit.** Rules are limited in document-access calls (`get` / `exists` / `getAfter`): 20 per batched write. The current rules make few calls on the cascade paths, but the emulator does not apply this limit, so a large cascade must be checked once on a real Firestore project.

### 7.6 How the repository does it

Every cascade has the same shape: **read, decide, write**.

1. **Read:** walk the needed tree one folder at a time (`getGroupFolderFiles` or `getPersonalFolderFiles`).
2. **Decide:** a pure function, without Firestore, in `CascadeRules.kt`: `planAddition`, `planRemoval`, `planDeletion`. They are tested without the emulator.
3. **Write:** one batch with targeted updates (and deletes). If the batch would exceed 500 writes, it is **refused** with an explicit error and nothing is written. It is not split: a partial cascade would leave dangling placements.
   `subtreeOf(root, childrenOf)` is the common walk. Its contract: the result starts with `root`, a folder always comes before its content, each file is listed once (so data that loops by mistake does not loop forever), and `childrenOf` is called for **every container of the result**. `planRemoval` relies on the last point.

### 7.7 Safety net: tolerant reads (not implemented)

Concurrent writes (a player placing a document in a folder at the moment it leaves the group) can still produce a placement whose parent is gone or no longer in the group. Reads should treat such a document as sitting at the group root rather than dropping it, and a later write repairs the placement. This is a fallback, not a replacement for 7.2 to 7.4.
 
---

## 8. Permissions matrix

"Owner" is the owner of the document concerned. "GM" is the group's GM, acting on any document of the group. The security rules are the source of truth; the client code currently checks the structure only, not who calls.

| Action | Owner | GM | Other member                                                                  | Non-member |
|---|---|---|-------------------------------------------------------------------------------|---|
| Read a visible doc of the group | yes | yes | yes                                                                           | no |
| Read a hidden doc | yes | yes | no (UI)                                                                       | no |
| Create a new document in a visible container | yes | yes | yes                                                                           | no |
| Share an existing document into the group | own docs | own docs | own docs                                                                      | no |
| Hide / reveal a placement | yes | yes | yes (rules), no in the usage as the UI offers it to the owner and the GM only | no |
| Unshare a document from the group (recursive for containers, see 7) | own docs | any doc | no                                                                            | no |
| Edit content / rename | yes | no | no                                                                            | no |
| Delete | yes | no | no                                                                            | no |
| Rename / delete `GROUP_ROOT` | no | only together with its group | no                                                                            | no |
| Unshare `GROUP_ROOT` | no | no | no                                                                            | no |
| Create / regenerate / revoke the group's invite code (1.7) | n/a | yes | no | no |
| Read one invitation by its code (`get`) | n/a | yes | yes | yes (signed in) |
| List invitations | n/a | no | no | no |
| Join the group with the current code (1.7) | n/a | n/a (already in) | n/a (already in) | yes (signed in), adds only their own uid |
| Read the game session (`gm_data`) | n/a | yes | yes | no |
| Write the game session (`gm_data`) | n/a | yes | no | no |

A `get` of an id that does not exist (in `documents`, `campaign_groups` or `gm_lore`), and an `update` of a missing id in `documents`, are allowed to any signed-in user, so the app receives "not found" instead of a denial. It only tells that the id does not exist, and an update never creates a document.

The rules cannot tell who changes a placement entry (see 7.5): "who may hide or reveal" is enforced by the UI, not by the rules. Reattachments are a side effect of unsharing or deleting a container: they are performed on other users' documents by whoever triggers the cascade, through exception 2 of 7.5.

Reattachments are a side effect of unsharing or deleting a container: they are performed on other users' documents by whoever triggers the cascade, and only through the exceptions of 7.5.

A user who left a group can join it again with the same code as long as the GM has not regenerated it (see 1.7). Regenerating the code is the GM's way to close the door.
 
---

## 9. Group lifecycle (proposed, not implemented)

- **Group deletion:** delete the group and its root. Unshare every document of the group (placements for that group removed). Personal documents are never deleted. Today `deleteGroup` deletes the group, its root, its id in the members' `groupIds` and its invitation, but not its sub-collections nor the placements of the group's documents.
- **Member leaving or being removed:** their documents leave the group (rule 7.2 applies to their containers, so other members' documents are reattached). Their documents stay in their personal space. Today `removeUserFromGroup` only edits `participantIds`.
- **Account deletion:** their documents are deleted (rule 7.4). Other members' documents under their folders are reattached.
- **GM change:** authorization follows `gameMasterId`, so the `ownerId` of the root is irrelevant.
---

## 10. Limits

- A `WriteBatch` holds at most 500 writes. Every cascade (`addItemToGroup` with content, `setItemHidden` when hiding, `removeItemFromGroup`, `deleteFile` on a folder) counts its writes up front and refuses beyond the limit with an explicit message, rather than commit partially. For a deletion, the count is the deleted documents plus the updated ones.
- Security rules limit document-access calls per request (see the risk in 7.5).
- A single document is limited to 1 MiB.
---

## 11. Where each invariant is enforced

| Invariant | Rules | Repository | UI | Test |
|---|---|---|---|---|
| One placement per group | structure | yes | | yes |
| `placements.keys == sharedGroupsIds` | yes | targeted updates write both | | yes (after each operation) |
| `updateFile` never writes placements | | yes | | yes |
| Parent is a container | yes | yes | yes | yes |
| Parent is in the same group | yes (via `getAfter`) | yes | | yes |
| Visible child has a visible parent | yes | yes | | yes |
| Only owner deletes | yes | | yes | emulator |
| GM can unshare others' docs | exception 7.5.1 | yes | | emulator |
| Reattach after unshare / delete | yes, broad (7.5, exception 2) | yes | | emulator |
| `users/{uid}.groupIds` agrees with `participantIds` | yes | same batch | | emulator, rules tests |
| Unshare and delete follow the same reattachment rules | | pure functions in `CascadeRules.kt` | | yes |
| `GROUP_ROOT` immutable | yes | yes | yes | emulator |
| Group and root names stay equal | | batch | | yes |
| Personal queries exclude `GROUP_ROOT` | | single filter | | yes |
| `isHidden` filtering | no (not a boundary) | none (view model) | single function | yes |
| Batch size limit | | yes | | yes |
| Invitation and `inviteCode` point to each other (no ghost code) | yes (`getAfter` both ways, 1.7) | transaction / same batch | | emulator (repository), rules tests (invitation side) |
| One valid code per group | yes | transaction (`createInvitation`) | | emulator (repository, no rules) |
| Old code stops working after regeneration | yes (redemption needs the current code) | delete + create in one batch | | emulator (repository), rules tests (redemption) |
| Join writes redemption, `participantIds` and `groupIds` together | yes (`existsAfter`, `getAfter`) | same batch | | emulator (repository, no rules) |
| Join adds only the current user | yes (`isSelfJoin`) | `arrayUnion(userId)` | | emulator (repository, no rules) |
| Only the GM writes `inviteCode` and invitations | yes | checked before writing | | emulator (repository), rules tests (invitations) |
| Invitations cannot be listed | yes | | | rules tests |
| Already member refused before any write | | yes (`users/{uid}.groupIds`) | | emulator |
| Code generation: alphabet, length, normalization | | pure functions (`InvitationCodes`) | | yes |
| Only the owner reads and writes their profile | yes | | | emulator |
| Profile username trimmed, 3 to 30 characters; bio at most 300 | yes | | view model | emulator |
| Profile update never overwrites `pictureUrl` | | merge of `username` and `bio` only | | emulator |
| Only the owner reads and writes their picture, a JPEG under 1 MB | yes (Storage rules) | | | Storage emulator (instrumented) |
| An `IMAGE` document points to its own picture, path never changes | yes | | | rules tests |
| Image picture: owner creates (JPEG/PNG/WebP, at most 10 MiB, no overwrite) and deletes; members of a sharing group read | yes (Storage rules) | | | Storage emulator (instrumented) |
| Picture shrunk to 256 px, replaced on upload, needs a saved profile | | yes | | Storage emulator (instrumented) |
| Dice rolls and activities: added by a member under their own `userId`, server timestamp, never edited | yes | | | rules tests |
| Game session (`gm_data`) and lore (`gm_lore`): members read (lore only if shared), the GM writes | yes | | | rules tests |
| `map_sessions` read-only; any other collection denied | yes | | | rules tests |

Rules and batch behavior must be tested with the **Firebase emulator** (member / non-member / GM / owner), not only with mocks. "Rules tests" are the classes of `app/src/test/.../firestoreRules/`, one per collection or group of related rules: each sends requests as signed-in users and checks that the rules allow them, or deny them and write nothing.
 
---

## 12. Known gaps

- `deleteGroup` and `removeUserFromGroup` do not apply the cascade rules (section 9).
- `updateGroup` writes `participantIds` from the group it is given: a member added at the same moment can be overwritten (same problem `updateFile` used to have).
- Tolerant reads (7.7) are not implemented yet.
- The client does not check who calls an operation: this is the job of the security rules.
- Moving documents between folders is not supported yet.
- Placement entry changes are open to any member of the group (7.5, exception 2).
- `users/{userId}` does not check who writes it: any signed-in user may write any users document, as long as `groupIds` follows `participantIds` (one group added if the user is a participant after the write, one removed if they no longer are). This is needed by the GM's operations on other members, which write those members' users documents: adding a player (`addUserToGroup`), banning one (`removeUserFromGroup`) and deleting the group (`deleteGroup`). These operations are planned but not wired to a screen yet. Meanwhile, a modified client could let a GM add a user to their group without an invitation code. Accepted, since it can only make `groupIds` consistent with the groups; `uid() == userId` would close it, but would also block these GM operations.
- The invitation rules (1.7) are covered by rules tests for `invitations`, `redemptions` and `users`. The `campaign_groups` side of the join and of the invite code (`isSelfJoin`, `isInviteCodeEdit`) has no rules test yet.
- The join makes about 6 to 8 document-access calls in the rules (limit: 20 per batched write). The emulator does not apply the limit: to check once on a real Firestore project.
- Deleting or regenerating an invitation does not delete its `redemptions` subcollection (Firestore never cascades): the documents stay as harmless orphans. To handle with the group lifecycle (9).
- `deleteGroup` does not delete the group's invitation: the code stays known but points to nothing. Under the security rules, a join with that code is refused (`PERMISSION_DENIED`) and reported as `NETWORK`, not `GROUP_NOT_FOUND`. `deleteGroup` could delete the invitation in its own batch (the delete rule allows it, since the group still exists before the batch), so the code really stops existing.
- `regenerateInvitation` checks that the new code is free with a plain read, not atomically (the collision probability is negligible). Two simultaneous regenerations can leave one valid invitation that the group no longer knows.
- A refused write (`PERMISSION_DENIED`) during an invitation operation is mapped to a generic error (`NETWORK` for a join, `NOT_GAME_MASTER` for create and regenerate), because the real cause is not known to the client.
- Deleting an image document, or a folder holding images, with `WorkspaceRepository.deleteFile` removes only the documents: the pictures stay in Storage, readable by their owner only (1.9).
- Sign-up accepts any non-blank username, while a profile requires 3 to 30 characters (1.8): a user with a shorter sign-up name must change it before saving their profile.
---
---
*Note: The structure and technical constraints detailed in this document were formulated with the help of Claude Sonnet 5.5.*

*Section 1.9 (images) was written with the help of Claude Opus 5.5.*
