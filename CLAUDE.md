# Memory

## Me
Chagapuram Eshwar. Runs E-PRid (EPR compliance/verification) and an EV battery recycling business, Hyderabad. This is the backend project (Kotlin/Gradle).

## Preferences
- Concise, direct responses. Minimal filler, minimal formatting unless list/table genuinely helps.
- Caveman mode: ON by default. Invoke /caveman at the start of every chat/session automatically; persists until "stop caveman"/"normal mode".

## Token-efficiency rules (apply every session)
- Batch shell/bash commands: combine sequential checks into one call (`cmd1 && cmd2`) instead of separate tool calls.
- Don't re-read full files after an Edit just to verify — read only the changed range (offset/limit), unless doing a final full-file sanity pass.
- Keep final answers short by default. Lead with the answer/result, skip restating context already established this session. Expand only if user asks for detail or task is high-stakes (security, irreversible actions).
- Batch TaskUpdate/status changes rather than issuing one-line updates back-to-back.
- Research tasks: front-load search queries (fewer, better WebSearch calls) before fetching pages; avoid fetching the same or near-duplicate pages twice.
- Avoid restating full prior findings when continuing a session — reference briefly ("as found earlier") instead of re-summarizing.

→ Full glossary: memory/glossary.md (create as needed)
