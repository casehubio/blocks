# blocks Workspace
**Name:** casehub-blocks

**Physical path:** `/Users/mdproctor/claude/casehub/blocks/CLAUDE.md`
**Symlinked at:** `/Users/mdproctor/claude/public/casehub/blocks/CLAUDE.md`
**Project repo:** `/Users/mdproctor/claude/casehub/blocks`
**Workspace:** `/Users/mdproctor/claude/public/casehub/blocks`
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/blocks` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/blocks`) — methodology artifacts: handover, blog (staging before publish), plans, snapshots
- **Project repo** (`/Users/mdproctor/claude/casehub/blocks`) — source code, ADRs (`docs/adr/`), specs

Never rely on CWD for git operations — the session may have started in either repo. Always use explicit paths:
```bash
git -C /Users/mdproctor/claude/public/casehub/blocks ...   # workspace artifacts
git -C /Users/mdproctor/claude/casehub/blocks ...           # project artifacts
```

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| design     | project     | journal file lives in workspace design/; DESIGN.md merge target is project docs/DESIGN.md |
| snapshots  | workspace   | |
| specs      | project     | lands in project `docs/` |
| plans      | workspace   | |
| handover   | workspace   | |

---

# CaseHub Blocks

## Project Type

type: java

## Repository Role

Reusable building blocks for CaseHub applications — composed from qhorus, engine, and work primitives. Foundation-adjacent library (sits between foundation and application tier). Single module, single artifact: `casehub-blocks`.

**Peer repos (each has its own Claude session — do not commit to these):**
platform, eidos, ledger, connectors, iot, work, worker, qhorus, pages, engine, claudony, openclaw, neural-text, devtown, aml, clinical, drafthouse, life, quarkmind, flow, soc, fsitrading, ras, ops, workers, desiredstate

## Build Commands

```bash
mvn --batch-mode install
mvn --batch-mode test
```

## Testing

No Quarkus runtime — plain JUnit 5 tests with CDI via Jandex indexing.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `CLAUDE.md` | Project conventions |
| `docs/` | Documentation |

## IntelliJ MCP Routing

One IntelliJ MCP server is available:

- **`mcp__intellij-index__*`** — use this for ALL code intelligence and navigation. Supports auto-opening projects via `project_path` — pass the project path and the plugin opens it automatically. Never ask the user to open a project manually.

`mcp__intellij__*` (built-in JetBrains MCP) is **disabled** due to a memory leak. Do not attempt to use it.

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
For all Java work: `java-dev`
Before committing: `superpowers:requesting-code-review`
After implementation: `implementation-doc-sync`

## Work Tracking

Issue tracking: enabled
GitHub repo: casehubio/blocks
Changelog: GitHub Releases
