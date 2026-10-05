# Canary Next plan

Development branch for the next experimental OpenWispr build while Canary 4 continues its 72-hour stability soak unchanged.

## UI language
- Spanish throughout the app UI: settings, dialogs, pop-ups, toasts, status messages and command examples.
- Default voice-command trigger phrase: **Comando Whisper**.
- Voice-command instructions/examples shown in Spanish.
- Keep technical/provider names as-is where translation would be misleading (Groq, API, GitHub, ACTION_SET_TEXT, etc.).

## Overlay appearance
- Add a user setting for the **idle indicator color** used by both bubble and minimized dot.
- Provide a small preset palette plus a custom color option.
- Recording remains red and transcribing/busy keeps a distinct status color so functional state is never ambiguous.
- Changing the idle color must not rebuild/resize the outer overlay host window; only the inner drawable changes, preserving the Samsung stability fix.

## About / build identity
Display the build identity clearly inside the app, for example:
- OpenWispr 3.10.0
- Canal: Canary 5 / Beta / Stable
- Proyecto / mantenedor: **@sromero78**
- GitHub repository link

Do not derive the channel only from UI text; keep it tied to the build/version metadata so Canary/Beta/Stable identify themselves correctly.

## Already planned for this development branch
- Local dictation history ("historial"), privacy-first and clearable.
- Writing profiles.
- Long-dictation visual warning around 4:30, with 5:00 as a soft warning threshold, not an automatic stop.

## Non-regression requirement
All work here remains subject to REGRESSION_TESTS.md. In particular:
- no normal-path clipboard use;
- no phantom placeholder prefixes such as WhatsApp "Mensaje";
- preserve real existing text;
- do not disturb the fixed-size overlay host that eliminated Samsung freezes.

Canary 4 itself remains unchanged during its stability test.

## Build validation
Canary Next changes are compiled in CI before promotion to the public Canary branch.
