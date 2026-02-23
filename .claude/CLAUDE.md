# CLAUDE.md

This file provides guidance to AI assistants when working with code in this repository.

## About

Android companion app for [airplanes.live](https://airplanes.live) - an ADS-B flight tracking community.
Features: aircraft map, search, feeder monitoring, SQUAWK/ICAO alerts.

## Quick Reference

| Topic | File |
|-------|------|
| Module structure, patterns, tech stack | `.claude/rules/architecture.md` |
| Build, test, debug commands | `.claude/rules/development.md` |
| Code style, patterns | `.claude/rules/code-style.md` |
| Testing guidelines | `.claude/rules/testing.md` |

## Agent Instructions

- Use the Task tool to delegate suitable tasks to sub-agents
- Maintain focused contexts for both orchestrator and sub-agents
- Be critical and challenge suggestions
- Use `./.claude/tmp/` directory (create if it doesn't exist)
- Never use /tmp or system temp directories

## Key Commands

```bash
# Build (FOSS flavor)
./gradlew :app:compileFossDebugKotlin --no-daemon

# Test
./gradlew testFossDebugUnitTest

# Debug logs
adb logcat -v time -s APL:V
```

See `.claude/rules/development.md` for complete command reference.
