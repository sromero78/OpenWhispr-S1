# Regression rules

These checks are mandatory before promoting any build between Canary, Beta, and Stable when text injection, editor detection, candidate selection, or clipboard fallback has changed.

## Text-injection invariants

1. **Normal insertion must not touch the clipboard.**
   - Direct `ACTION_SET_TEXT` is the preferred path.
   - Clipboard is a last-resort compatibility fallback only.
   - A successful normal dictation must not trigger Android/WhatsApp clipboard notices or make Gboard surface the dictated text in its clipboard UI.

2. **Never prepend placeholder/accessibility UI text.**
   - Empty composers must receive only the dictated text.
   - Phantom labels/hints such as WhatsApp's `Mensaje` must never become part of the message.

3. **Preserve real existing user text.**
   - If a field contains real text and exposes a valid selection/cursor, insert at the selection/cursor without deleting unrelated text.
   - Never treat uncertain accessibility text as trustworthy content merely because `node.text` is non-empty.

4. **Fallback must be explicit and rare.**
   - Clipboard fallback is allowed only after direct accessibility insertion genuinely fails.
   - If fallback succeeds, clear the clipboard immediately as already implemented.
   - Any increase in fallback frequency is a regression, even if the final text appears correct.

## Mandatory smoke tests

For any change touching `injectText`, `tryDirectInsertIntoNode`, `editableTextContent`, candidate scoring/selection, or paste fallback, test at minimum:

- WhatsApp conversation with self, empty composer.
- WhatsApp group, empty composer.
- WhatsApp with manually typed existing text, then dictate.
- One standard Android text field / ChatGPT-style editor.
- Verify Gboard clipboard UI remains unchanged during normal direct insertion.
- Verify no `Mensaje`/placeholder prefix appears.

## Promotion rule

A Canary must not be promoted to Beta, and a Beta must not be promoted to Stable, if any invariant above regresses. Fix the regression first, even if the new change solves a different bug.

This checklist exists because Canary 3.10.0-canary.3 fixed the phantom `Mensaje` prefix by becoming too conservative and unintentionally increased clipboard fallback usage. Future fixes must preserve both properties at the same time.
