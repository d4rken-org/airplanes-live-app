# Code Style

## General Principles

- Prioritize **correctness** and **clarity** over performance
- Only write comments to explain **why** something is done in a non-obvious way — no summaries
- Prefer adding to **existing files** unless it's a new logical component
- Organize new files by **feature**, not by layer (package by feature)
- Extract user-facing text to `strings.xml`

## What NOT To Do

- Don't generate `README.md` files for modules or packages
- Don't rename existing classes or files unless asked
- Don't create example/mock code unless explicitly requested

## Tech Context

- Jetpack Compose UI with Navigation3
- Dagger/Hilt for dependency injection
- Kotlin Coroutines & Flow for async
- Kotlinx.serialization for data serialization
- Coil 3 for image loading
