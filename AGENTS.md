<!-- Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com> -->
<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->

## Architecture Rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.

## UI conventions

- Follow the [UI conventions](wiki/UI-conventions.md) when building or modifying UI.

## Definition of Done

- The feature works and matches its acceptance criteria (the milestone's tests).
- **All new code comes with unit tests**, written as described in `TESTING.md`.
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting) before you submit.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check` passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/), as the wiki's Team Collaboration Decisions require: `<type>[optional scope]: <description>`, for example `feat(auth): add Google sign-in`. Use `feat`, `fix`, `docs`, `test`, `refactor` or `chore`. Add a body wrapped at 72 characters when the subject is not enough.
- **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. An AI agent is a contributor, so credit it.

## Opening a PR

- If a PR has to be open, refer yourself to the [PR template](.github/PULL_REQUEST_TEMPLATE.md)
- Add the person who opened the PR as the PR owner