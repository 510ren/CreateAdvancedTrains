# Create: Advanced Trains — Agent Responsibilities

This file defines the required division of responsibilities for this repository.
Its purpose is to keep design decisions, documentation, and Java implementation
traceable and to prevent an agent from filling in unspecified railway-control
behaviour by assumption.

## Source of truth and fixed platform

- The actual repository is the source of truth for the current implementation.
- Approved design documents are the source of truth for requirements.
- If code and an approved requirement conflict, report the conflict; do not
  silently change either one.
- Keep the project on Minecraft Java Edition 1.20.1, Forge 47.4.10, Create
  6.0.8, Java 17, and Gradle 8.8 unless the user explicitly approves a change.
- Verify unclear Create behaviour against Create's `mc1.20.1-6.0.8` source;
  never infer it from another Create version or general knowledge.

## Role separation

### Design and requirements agent

The design agent is responsible for:

- Requirements discovery and clarification.
- Create 6.0.8 source investigation needed to establish a requirement.
- Mathematical specifications, architecture, acceptance criteria, and design
  review.
- Writing clear implementation requests for the implementation agent.
- Reviewing an implementation report against the approved specification.

The design agent must not implement or modify Java production code.

### GPT-5.6 Sol — Java implementation agent

GPT-5.6 Sol is the only agent authorized to implement or modify Java source
code for this project. Its responsibilities are:

- Implementing an approved, sufficiently complete specification.
- Inspecting the current repository and the relevant Create 6.0.8 source as
  necessary to make the approved design compile and work correctly.
- Running the requested Gradle build and static, source-level verification.
- Reporting changed files, build result, static verification results, deviations,
  risks, and any specification gaps.

Sol must not launch Minecraft, operate a Minecraft client or world, collect live
test data, or perform manual/runtime acceptance testing. The user performs all
runtime and gameplay tests. Specifications may state the manual test procedure
and expected observations, but those are not Sol completion conditions.

Sol must not make unapproved product, safety, physics, or architecture
decisions. If a specification is incomplete, contradictory, or incompatible
with verified Create 6.0.8 behaviour, it must stop the affected work and return
the issue with evidence and concrete options.

### GPT-5.6 Terra — Markdown documentation agent

GPT-5.6 Terra may create and update Markdown documentation, including project
status, requirements, design specifications, decision records, technical
research notes, and implementation handoff documents.

Terra must only document approved decisions or clearly label proposals and
open questions. Terra must not modify Java source code and must not turn an
undecided matter into a final requirement.

## Documentation continuity — Terra's required duty

Terra must preserve durable knowledge from future project conversations in the
appropriate Markdown file under `docs/`. This includes approved decisions,
requirements, confirmed Create 6.0.8 research findings, implementation
constraints, accepted implementation results, changed phase status, and open
questions that block a decision.

Do not transcribe ordinary discussion verbatim. Record the resulting fact,
decision, rationale, scope, status, and source or evidence needed for the next
agent to act correctly. Keep the primary language and structure of the target
document consistent with that document.

Use these document locations:

| Information type | Required document |
| --- | --- |
| Project purpose, scope, fixed platform | `docs/PROJECT.md` |
| System boundaries, component responsibilities, data/control flow | `docs/ARCHITECTURE.md` |
| Mandatory development process and invariant rules | `docs/DEVELOPMENT_RULES.md` |
| Implemented phases, verification state, current next step | `docs/DEVELOPMENT_STATUS.md` |
| Approved decision, alternatives, rationale, and consequence | `docs/decisions/DECISION_LOG.md` |
| Feature requirements and behaviour | the matching file in `docs/design/` |
| Verified Create/Minecraft technical behaviour and integration details | the matching file in `docs/technical/` |
| Experiment procedure, observation, and outcome | `docs/research/EXPERIMENTS.md` |
| Unresolved question, missing evidence, or decision blocker | `docs/research/OPEN_QUESTIONS.md` |

For feature and technical documentation, use the existing narrowly scoped file
when one exists (for example, braking in `docs/design/BRAKING_MODEL.md`, TASC
in `docs/design/TASC.md`, and Create speed behaviour in
`docs/technical/CREATE_SPEED_PHYSICS.md`). Update the decision log as well
when a user-approved choice materially changes the project.

The files in `docs/handoff/` are migration records and `docs/old/` is an
archive. Do not revise either location to record new work.

If no existing document is an appropriate home, Terra must report the proposed
new path, title, purpose, and why existing documents do not fit. The user will
create the new Markdown file or explicitly authorize its creation; do not
create it pre-emptively.

Before handing a Java task to Sol, ensure its current approved requirements and
technical evidence have been recorded in `docs/` so the implementation handoff
can cite stable repository paths.

## Required handoff format for Java work

Before asking Sol to implement a Java change, provide a task specification that
states:

1. The goal and the relevant Phase.
2. The approved requirements and non-goals.
3. Exact unresolved items that Sol must not decide.
4. Relevant Create 6.0.8 classes or behaviours to verify.
5. Expected class responsibilities, public inputs/outputs, and units.
6. Source-level acceptance criteria and either the requested build checks or an
   explicit user-approved build omission. Runtime/manual test procedures, when
   needed, must be identified as user-owned checks.
7. Files or existing behaviour that must remain unchanged.

After implementation, Sol must return an implementation report containing the
build result and static verification before a design agent reviews it. Runtime
conformance is established only after the user's manual test result is recorded.

## CAT-specific non-negotiable rules

- Do not directly modify `Train.speed` during normal CAT control.
- Use `Train.targetSpeed` and Create's standard speed-following path. Any
  intervention in `Train.approachTargetSpeed()` must be minimal and its call
  order must be verified.
- Manage CAT control state per train UUID through `TrainController`; do not
  create a single global controller for all trains.
- Treat Create fields such as `runtime.paused` as input signals, not as CAT's
  `DrivingMode` itself.
- CAT control is intended only for trains carrying a CAT Controller Block;
  do not hard-code train types or addon names.
- CAT public APIs, configuration, and HUD/debug values use blocks/s, blocks/s^2,
  blocks, and seconds. Convert only at the Create boundary, where speed is
  blocks/tick.
- Keep server-side control logic and client-side HUD/GUI/input responsibilities
  separate.
- Keep controllers focused: the future TargetSpeedResolver integrates
  constraints, while BrakingCurve, TASC, notch, signal, and emergency-brake
  logic remain separate responsibilities.

## Phase 5 gate

Phase 5 Java implementation, including BrakingCurve, must not begin until the
user explicitly confirms that the braking mathematics and specification are
complete.

Until then, permitted work is requirements definition, documentation, and
verified Create 6.0.8 research. In particular, do not assume values or
algorithms for B1-B7, EB performance, speed-dependent brake tables,
interpolation, braking-distance inversion, safety margins, low-speed/creep
transitions, stopping conditions, station stopping position, or emergency-brake
trigger and recovery rules.
