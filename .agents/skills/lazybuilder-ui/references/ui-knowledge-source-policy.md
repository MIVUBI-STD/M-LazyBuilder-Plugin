# LazyBuilder UI Knowledge Source Policy

Use this reference when external UI/UX knowledge could improve a Launcher, Fabric mod, or plugin-facing UI decision.

The purpose is to improve accuracy without turning LazyBuilder into a copy of another product or creating conflicting design authorities.

## Authority hierarchy

Use sources in this order:

```text
1. LazyBuilder product/source/docs              ← product authority
2. Exact platform/API documentation             ← platform truth
3. Version-matched Minecraft/Fabric/Paper API    ← compatibility truth
4. Mature open-source implementations            ← implementation reference
5. General UI/UX guidance                        ← heuristic only
6. Visual inspiration/screenshots                ← aesthetic reference only
```

A lower layer never overrides a higher layer.

Examples:

- LazyBuilder ownership rules beat patterns from another mod.
- Fabric/Minecraft lifecycle rules beat web UI conventions.
- Paper/Adventure semantics beat a generic GUI library example.
- A popular repo may show a useful interaction pattern but cannot become a runtime dependency unless separately justified by architecture.

## Canonical external source set

Keep the default source set intentionally small.

### Fabric / Minecraft client UI

Primary platform reference:

```text
https://docs.fabricmc.net/develop/rendering/gui/custom-screens
```

Use for:

```text
Screen lifecycle
init timing
widget creation
open/close behavior
parent return behavior
Minecraft GUI concepts
```

When version differences matter, prefer the documentation/mappings/source matching the repo's target Minecraft/Fabric version over a newer generic example.

### Mature Fabric implementation reference

Preferred example:

```text
https://github.com/TerraformersMC/ModMenu
```

Useful for studying, not copying:

```text
search/filter behavior
list/detail hierarchy
scrolling
responsive panes
screen parent/back patterns
mod/config discovery
badges/status presentation
```

Do not copy source/assets/branding. Do not add Mod Menu as a dependency merely to reuse its appearance.

Additional mature libraries may be consulted only when they solve the exact pattern under review, for example configuration/form composition. Avoid accumulating reference repos without a concrete question.

### Paper / Adventure presentation

Primary documentation:

```text
https://docs.papermc.io/adventure/
```

Use the relevant Adventure/Paper section for:

```text
Components/chat
MiniMessage
BossBars
Titles
Books
Player/tab list
resource packs
localization
```

For inventory/container GUI libraries, treat community libraries as implementation references. The vanilla Minecraft client remains the rendering authority.

## Research trigger

Do **not** browse external sources for every UI edit.

Research is justified when at least one applies:

```text
API/lifecycle behavior is uncertain
Minecraft/Fabric/Paper version changed
current pattern has repeated defects
input/focus/resize behavior is unclear
we are introducing a new type of UI surface
we need to compare established interaction patterns
an existing implementation may prevent overdevelopment
```

For local spacing/copy/token cleanup with clear existing conventions, stay inside the repo.

## Research question discipline

Search with a concrete question, not "find good UI".

Good:

```text
How does Minecraft Screen initialization behave after resize?
How do mature Fabric screens preserve parent navigation?
What is the native client surface for this Paper Adventure feature?
How does an established list/detail screen handle long mod names?
```

Bad:

```text
best Minecraft UI repos
cool launcher design
modern UI inspiration
```

The output of research should be a small applicable rule, not a large imported framework.

## Adoption test

Before adopting an external pattern, answer:

```text
Does it fit LazyBuilder's primary user task?
Does it respect current semantic ownership?
Does it work on the actual surface (desktop vs Minecraft vs Paper)?
Does it fit the target version/API?
Can it be implemented with existing components/controllers?
Does it reduce decisions or failure modes?
Will it create a new dependency, manager, cache, or duplicated state?
Can we verify it with the Visual Proof System?
```

Reject the pattern if it mainly adds novelty, framework weight, or visual complexity.

## Version accuracy

For Minecraft/Fabric work, never assume a current upstream sample matches LazyBuilder's target version.

Check as needed:

```text
Minecraft target version
Fabric Loader/API version
mapping/API names
Screen/widget/render method signatures
key input APIs
GUI scaling behavior
```

A useful modern example can still be conceptually referenced while implementation follows the repo's actual version contracts.

For Paper work, confirm the Paper/Adventure API used by the repo before changing presentation code or component behavior.

## Source-to-skill extraction

When a source yields a useful general rule, encode only the rule that has survived LazyBuilder review.

Example:

```text
source observation:
mature screen keeps a parent Screen reference

LazyBuilder rule:
Back/Esc should return to the meaningful parent when the workflow is nested and preserve useful state.
```

Do not paste large external source snippets into the skill.

## Licensing and copying boundary

External repositories are references only unless their license and architectural use have been explicitly reviewed.

Default behavior:

```text
study behavior
summarize pattern
implement independently in LazyBuilder style
```

Never copy branding, textures, icon packs, substantial code, or distinctive screen composition merely because a repository is public.

## Evidence recording

When external research materially drives a non-obvious UI decision, document briefly in the change/task report:

```text
source consulted
platform/version relevance
rule adopted
rule rejected/modified if applicable
proof used
```

Do not create a permanent research log for trivial decisions.

## Stop rule

Stop researching when:

```text
platform behavior is known
one or two mature references confirm the pattern
LazyBuilder ownership is clear
the smallest implementation is identifiable
proof plan is defined
```

More references after that usually increase noise rather than accuracy.
