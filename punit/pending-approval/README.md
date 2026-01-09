# Pending Baseline Approvals

This directory contains baseline files that are awaiting human review and approval.

## Workflow

1. **Experiment runs** → Baseline generated in `build/punit/baselines/`
2. **Promote for review** → `./gradlew punitPromote` copies baseline here
3. **CI creates PR** → Human reviews baseline data in PR
4. **Approve** → PR merge triggers `./gradlew punitApprove`
5. **Spec created** → `src/test/resources/punit/specs/`

## File Lifecycle

| State | Location | Action |
|-------|----------|--------|
| Generated | `build/punit/baselines/` | `./gradlew experiment` |
| Pending | `punit/pending-approval/` | `./gradlew punitPromote` |
| Approved | `src/test/resources/punit/specs/` | `./gradlew punitApprove` |

## Reviewing a Baseline

When reviewing a baseline file, check:

- [ ] **Sample size**: Is N large enough for reliable estimates? (Recommend N ≥ 1000)
- [ ] **Success rate**: Is the observed rate realistic? (Expect ~95% for well-tuned LLM prompts)
- [ ] **Confidence interval**: Is the CI narrow enough? (±2% or better)
- [ ] **Failure distribution**: Are failure modes expected? (See `failureDistribution` section)
- [ ] **Cost metrics**: Are token/time costs acceptable?

## Example CI Configuration

```yaml
# GitHub Actions example
- name: Promote baseline for review
  run: ./gradlew punitPromote --useCase=usecase.shopping.search

- name: Create PR
  uses: peter-evans/create-pull-request@v5
  with:
    title: "[PUnit] Baseline Review Required"
    body: |
      A new baseline has been generated and requires approval.
      
      Please review the baseline data in `punit/pending-approval/`.
```

