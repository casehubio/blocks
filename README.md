# casehub-blocks

[![Build](https://github.com/casehubio/blocks/actions/workflows/publish.yml/badge.svg?branch=main)](https://github.com/casehubio/blocks/actions/workflows/publish.yml)

Reusable building blocks for CaseHub applications — composed from qhorus, engine, and work primitives.

## What This Is

A library of pre-composed interaction patterns that sit between the foundation modules (qhorus, engine, work) and application-tier repos (aml, clinical, life, etc.). Each block encapsulates a coordination pattern that would otherwise be duplicated across applications.

## Dependencies

```
casehub-blocks
├── casehub-qhorus-api    (channels, commitments, speech acts)
├── casehub-work-api      (work items, task lifecycle)
└── casehub-engine-api    (cases, plans, routing)
```

## Build

```bash
mvn install
```

## Adding a New Block

1. Create a package under `io.casehub.blocks.<name>`
2. Compose from qhorus, engine, and work SPIs — blocks never depend on runtime implementations
3. Add tests using `casehub-qhorus-testing` and `casehub-engine-testing` for in-memory stores
4. The Jandex plugin indexes all CDI beans automatically

## Part of

[CaseHub](https://github.com/casehubio) — Epic [#310](https://github.com/casehubio/parent/issues/310)
