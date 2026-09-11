# LazyBuilder Development Brief Skill

Use only for genuinely complex or ambiguous development where a normal Standard contract cannot reliably identify ownership, success criteria, or execution partition.

## Inputs

- current user requirement;
- exact affected domain docs;
- current source/evidence;
- execution context.

## Output Contract

Produce the smallest development brief that resolves:

```text
Goal
Success metric
First wrong owner
Material unknowns
In scope / out of scope
Non-goals / forbidden proxies
Execution partition
Acceptance evidence
Higher-context residue
STOP condition
```

## Rules

- Do not create architecture merely because the task is large.
- Prefer deletion/consolidation over parallel systems.
- One responsibility gets one canonical owner.
- Separate source/CI proof from local/live-server proof.
- Finish all independently GitHub-verifiable work before handing off local/server residue.
- Do not preserve legacy behavior unless it remains a real requirement.
- Stop once the ambiguity blocking implementation is resolved; hand control back to the exact domain owner.
