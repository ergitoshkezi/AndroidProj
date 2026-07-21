---
trigger: always_on
---

# AI_AGENT_WORKFLOW.md

## Purpose

This project uses four AI systems:

* Claude Memory → project knowledge & semantic retrieval
* Caveman → planning
* GitNexus → architecture & impact analysis
* Serena → semantic navigation & code changes

Goal:

Never make code changes without understanding context, architecture, dependencies, and symbol usage.

---

# Core Rules

## Memory First

Before any non-trivial task:

1. Query Claude Memory using semantic search.
2. Retrieve only relevant information.
3. Do not load full project history.

Claude Memory is a retrieval system, not a context buffer.

---

## Context Management

Prefer:

Retrieve → Use → Compress → Discard

When context exceeds ~60%:

* compact context
* keep only active goals, constraints, decisions, and risks
* re-retrieve information later if needed

Never accumulate context indefinitely.

---

## Serena First

Use Serena whenever available.

Prefer:

* find_symbol
* find_references
* find_callers
* find_implementations
* semantic edits

Avoid:

* grep
* ripgrep
* regex search
* blind search-and-replace

Use text search only when Serena cannot solve the task or for docs/config/generated files.

---

# Tool Responsibilities

## Claude Memory

Retrieve:

* ADRs
* architecture decisions
* conventions
* business rules
* previous implementations
* known issues

Update memory only for durable knowledge.

---

## Caveman

Creates:

* execution plan
* risks
* implementation strategy

No code modifications.

---

## GitNexus

Provides:

* dependency analysis
* architecture analysis
* impact assessment
* coupling risks

No code modifications.

---

## Serena

Responsible for:

* semantic navigation
* reference discovery
* refactoring
* code modifications

Always validate symbols before editing.

---

# Required Workflow

## Medium / Large Tasks

1. Claude Memory
2. Context Compression
3. Caveman
4. GitNexus
5. Serena Validation
6. Serena Implementation
7. GitNexus Verification
8. Claude Memory Update

---

## Small Tasks

1. Claude Memory
2. Serena
3. GitNexus Verification

Caveman optional.

---

# Serena Validation

Before modifying code:

* verify symbol ownership
* verify references
* verify callers/callees
* verify inheritance/interfaces
* verify cross-module usage

Do not edit before validation.

---

# Implementation Rules

Before changes:

* retrieve memory
* analyze impact
* validate symbols

After changes:

* update references
* update tests if needed
* update docs if needed
* verify architecture integrity

---

# Definition Of Done

✓ Memory retrieved via semantic search

✓ Context compressed when >60%

✓ Impact analyzed

✓ Serena validation completed

✓ Changes implemented

✓ References verified

✓ Architecture verified

✓ Tests/docs updated if required

✓ Memory updated when new decisions were introduced

---

# Agent Instructions

ALWAYS:

* retrieve memory first
* use semantic retrieval
* use Serena before grep
* validate symbols before editing
* analyze impact before changing code
* compact context above 60%
* preserve architecture and conventions

NEVER:

* perform blind modifications
* use grep as primary navigation
* perform search-and-replace without semantic validation
* accumulate unnecessary context
