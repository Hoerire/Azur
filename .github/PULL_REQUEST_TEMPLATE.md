## What breaks without this change

<!-- Describe the failure or the limitation, not the implementation. -->

## What this changes

<!-- Short list. Mention files worth reviewing first. -->

## How it was verified

<!--
Commands you ran and what they printed. For UI changes, a screenshot.
For tool changes, the tool call and its result.
-->

```
```

## Checklist

- [ ] Builds: `./gradlew :app:assembleDebug`
- [ ] Tests pass: `./gradlew :app:testDebugUnitTest :agent-runtime:testDebugUnitTest`
- [ ] No new dependency without justification
- [ ] Documents describing the changed behaviour are updated (`AGENTS.md`, skills, `README`)
- [ ] No keystore, API key, or personal path committed
