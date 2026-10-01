---
type: entity
sources: [NOTES.md#round-8]
updated: 2026-10-01
tags: [external, card-art]
---

# Scryfall

Scryfall is the public Magic card database and API. The web build gets card art from Scryfall's API
through Forge's `LibGDXImageFetcher`. CORS is allowed, so it works from the browser directly.

- Art is cached **in memory only**, with no cap yet (an LRU cap is planned; see [[memory-budget]]).
- If the site becomes public, keep to Scryfall's guidance of about 10 requests per second.
- The desktop bulk CDN download prompt is disabled on the web ([[classic-code-pruning]]).
