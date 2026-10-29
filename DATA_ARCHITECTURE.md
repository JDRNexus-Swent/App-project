# JDRNexus - Data Architecture & Conventions (Firestore)

This document defines the architectural decisions and invariants of the NoSQL data model used in JDRNexus. All pull requests and new features must adhere to these rules. When a rule must change, change this document in the same PR.

Sections marked **(proposed)** are defaults that still need a team decision. Section 13 lists what is known to be missing, and section 14 what must be checked on a real Firestore before the security rules are enabled.

**Out of scope for now:** moving a document between folders, migration of legacy data, splitting an operation over several batches.
 
---

## 1. Data model

### 1.1 One flat collection

All items (text files, character sheets, folders, group roots) are stored in a single flat collection `documents` as `JDRFile` entities. Their behavior and UI representation are dictated by `type`.

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Document id. Never `""`. |
| `ownerId` | `String` | The user who owns the document. Only the owner can delete it. |
| `name` | `String` | Display name. |
| `type` | `DocumentType` | `FOLDER`, `TEXT`, `CHARACTER`, `GROUP_ROOT`. |
| `personalParentId` | `String` | Parent container in the owner's personal space. `""` = personal root. |
| `placements` | `Map<groupId, Placement>` | **Source of truth** for group placements: at most one entry per group. |
| `sharedGroupsIds` | `List<String>` | **Derived** from `placements.keys`. Used only for indexing and security rules. |
| `content` | `String` | Empty for containers. |

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
 
---

## 2. Group root (pseudo-root)

Every campaign group has one root document, created with the group.

- `id == groupId`, `type == GROUP_ROOT`.
- `ownerId` is the GM at creation time. **It is never used for authorization.** Authorization uses the group's `gameMasterId` / `participantIds`.
- `name` is a **copy of the group name**. The group document is the source of truth.
- **Creation:** `GroupRepository.createGroup` writes the group and its root in **one batch**, so a group never exists without its root.
- **Renaming:** `GroupRepository.updateGroup` updates the group and renames the root in the same batch. It fails if the root does not exist (a group without root is a broken state, better seen than hidden).
- `sharedGroupsIds == [groupId]` so members can read it through the standard group read rule.
- `placements` is empty. This is the single exception to the invariant "keys of `placements` == `sharedGroupsIds`" (see 4).
- It has **no personal placement**: it is an exception to the rule of section 3. Personal-space queries (`getPersonalRootFiles`, `getPersonalFolderFiles`) exclude `GROUP_ROOT` through one shared filter in the repository.
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

Why not the helpers for an existing document: they would send a `placements` map read earlier, so two members sharing the same file into different groups at the same time would erase each other's placement. For the same reason **`updateFile` never writes `placements` or `sharedGroupsIds`**: saving an edit made from an older copy of a file must not undo a share made meanwhile.

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
- The owner of a document toggles its own placement. The GM may hide any document in the group, but only reveals their own **(proposed)**.
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

- **Hide** (`isHidden`): the placement starts hidden. Default: off.
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

### 7.5 Required security-rule exceptions

Normally only the owner can write a document. The cascades above write documents the actor does not own, so the rules need two narrow exceptions, each verifying a **diff** (no other field may change):

1. **Unshare (GM):** the GM of group `G` may remove the key `G` from `placements` and `sharedGroupsIds` of any document.
2. **Reattach (any member of `G`):** `placements[G].folderId` may change only if the previous parent **no longer belongs to `G` after the batch**: `!existsAfter(old) || !(G in getAfter(old).data.sharedGroupsIds)`. The new parent must be a container in `G`, and `isHidden` may only change from `false` to `true`.
   **Known risk:** rules are limited in document-access calls (`get` / `exists` / `getAfter`) per request, and the limit is much lower for batched writes than the size of a batch. A large cascade could be refused by the rules even if it is under 500 writes. The emulator did not apply this limit in our tests, so it must be verified on a real Firestore (does accessing the same parent from several writes count once?). **Chosen fallback:** drop exception 2 and rely on tolerant reads (7.7), so no other user's document is written. Tolerant reads are not implemented yet.

### 7.6 How the repository does it

Every cascade has the same shape: **read, decide, write**.

1. **Read:** walk the needed tree one folder at a time (`getGroupFolderFiles` or `getPersonalFolderFiles`).
2. **Decide:** a pure function, without Firestore, in `CascadeRules.kt`: `planAddition`, `planRemoval`, `planDeletion`. They are tested without the emulator.
3. **Write:** one batch with targeted updates (and deletes). If the batch would exceed 500 writes, it is **refused** with an explicit error and nothing is written. It is not split: a partial cascade would leave dangling placements.
   `subtreeOf(root, childrenOf)` is the common walk. Its contract: the result starts with `root`, a folder always comes before its content, each file is listed once (so data that loops by mistake does not loop forever), and `childrenOf` is called for **every container of the result**. `planRemoval` relies on the last point.

### 7.7 Safety net: tolerant reads (not implemented)

Concurrent writes (a player placing a document in a folder at the moment it leaves the group) can still produce a placement whose parent is gone or no longer in the group. Reads should treat such a document as sitting at the group root rather than dropping it, and a later write repairs the placement. This is a fallback, not a replacement for 7.2 to 7.4.
 
---

## 8. Permissions matrix (proposed)

"Owner" is the owner of the document concerned. "GM" is the group's GM, acting on any document of the group. The security rules are the source of truth; the client code currently checks the structure only, not who calls.

| Action | Owner | GM | Other member | Non-member |
|---|---|---|---|---|
| Read a visible doc of the group | yes | yes | yes | no |
| Read a hidden doc | yes | yes | no (UI) | no |
| Create a new document in a visible container | yes | yes | yes | no |
| Share an existing document into the group | own docs | own docs | own docs | no |
| Hide / reveal own placement | yes | yes | no | no |
| Hide another's placement | no | yes | no | no |
| Unshare a document from the group (recursive for containers, see 7) | own docs | any doc | no | no |
| Edit content / rename | yes | no | no | no |
| Delete | yes | no | no | no |
| Rename / delete / unshare `GROUP_ROOT` | no | no | no | no |

Reattachments are a side effect of unsharing or deleting a container: they are performed on other users' documents by whoever triggers the cascade, and only through the exceptions of 7.5.
 
---

## 9. Group lifecycle (proposed, not implemented)

- **Group deletion:** delete the group and its root. Unshare every document of the group (placements for that group removed). Personal documents are never deleted. Today `deleteGroup` only deletes the group document.
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
| Reattach after unshare / delete | exception 7.5.2 | yes | | emulator |
| Unshare and delete follow the same reattachment rules | | pure functions in `CascadeRules.kt` | | yes |
| `GROUP_ROOT` immutable | yes | yes | yes | emulator |
| Group and root names stay equal | | batch | | yes |
| Personal queries exclude `GROUP_ROOT` | | single filter | | yes |
| `isHidden` filtering | no (not a boundary) | none (view model) | single function | yes |
| Batch size limit | | yes | | yes |

Rules and batch behavior must be tested with the **Firebase emulator** (member / non-member / GM / owner), not only with mocks.
 
---

## 12. Known gaps

- `removeItemFromGroup` keeps a `parentFolderId` parameter that is no longer read. It will be removed in a separate PR.
- `deleteGroup` and `removeUserFromGroup` do not apply the cascade rules (section 9).
- `updateGroup` writes `participantIds` from the group it is given: a member added at the same moment can be overwritten (same problem `updateFile` used to have).
- Tolerant reads (7.7) are not implemented yet.
- The client does not check who calls an operation: this is the job of the security rules.
- Moving documents between folders is not supported yet.
---
 