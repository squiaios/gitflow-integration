# GitFlow Pilot

A plugin for PhpStorm (and the other IntelliJ Platform IDEs) that brings the
[git flow](https://nvie.com/posts/a-successful-git-branching-model/) branching model into the IDE.

The implementation is **native**: it relies on plain git, not on the `git flow` command, so that
command does not need to be installed. The configuration is stored in the repository's git config
under the `gitflow.*` keys.

## Features

| | |
|---|---|
| **Initialize** | production and development branches, branch prefixes, version tag prefix |
| **Start** | `feature`, `bugfix`, `release`, `hotfix`, `support` |
| **Finish** | `feature` / `bugfix`: merge (with or without fast-forward), squash or rebase; `release` / `hotfix`: merge into production, annotated tag, merge back into development |
| **Publish** | pushes the branch and sets up tracking |
| **Check out / delete** | a remote-only branch is first tracked locally; local and remote deletion |
| **Git Flow tool window** | the git flow branches of each repository, grouped by type |
| **Status bar** | where the current branch stands (`Feature: login`, `Git Flow: off`…); a click opens the actions |

Ways to reach the actions:

- the **Git ▸ Git Flow** menu;
- the **Git Flow** button in the main toolbar, just before the search (it can be hidden through
  *Customize Toolbar*);
- the **Git Flow** entry at the bottom of the menu that opens when clicking the branch;
- the status bar widget (it can be hidden by right-clicking the status bar);
- the **Git Flow** tool window, or *Search Everywhere* ("Git Flow").

The button, the branches menu entry and the widget all open the same list of actions
(`GitFlow.Actions` in `plugin.xml`).
The defaults of the dialogs are set in **Settings ▸ Version Control ▸ Git Flow**.

### Behavior worth knowing

- **Resuming after a conflict.** A conflict interrupts the operation and opens the IDE's merge tool.
  Once the conflicts are resolved and the merge is committed, *finishing again* picks up where it
  stopped: what is already merged or tagged is not redone (as with `git flow`).
- Only one `release` and one `hotfix` at a time.
- Finishing a `hotfix` merges it into production and into **development**; if a `release` is open,
  a warning says so (it does not contain the fix).
- After a verified merge, the branch is deleted with `git branch -D`, because git's `-d` compares
  against the *upstream* branch and would refuse a branch that is merged but not pushed.
- The working tree must be clean (tracked files) to finish a branch.

## Development

Requirements: a JDK 25 (the one bundled with PhpStorm 2026.2, `<PhpStorm>/jbr`, works).
The plugin is written in **Kotlin**; the 2026.2 platform is compiled to Java 25 bytecode and bundles
the Kotlin standard library, so the plugin does not ship its own copy.

```bash
./gradlew test            # engine tests, run against real git repositories
./gradlew buildPlugin     # build/distributions/gitflow-integration-<version>.zip
./gradlew verifyPlugin    # Plugin Verifier against the recommended IDEs
./gradlew runIde          # test IDE with the plugin installed
```

## License

Released under the [MIT License](LICENSE). To report a security issue, see [SECURITY.md](SECURITY.md).
