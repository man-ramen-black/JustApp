/**
 * What the box says Claude is doing: `idle` between turns, a model phase
 * (`requesting`, `thinking`, `writing`, `composing`), a tool phase (`tool`,
 * `agent`, `approval`, `question`), `compacting`, or `waiting` between turns
 * on background shells and monitors whose notices will wake it.
 */
export type ActivityPhase =
  | 'idle'
  | 'requesting'
  | 'thinking'
  | 'writing'
  | 'composing'
  | 'tool'
  | 'agent'
  | 'approval'
  | 'question'
  | 'compacting'
  | 'waiting'

/** The box's headline: the phase, its words, and when it began. */
export type ActivityNow = {
  phase: ActivityPhase
  label: string
  sinceMs: number
}

/**
 * A subagent at work: what it was asked to do, its latest action, and when
 * it started.
 */
export type ActivityAgent = {
  name: string
  label: string
  sinceMs: number
}

/**
 * A background shell or monitor at work: what it runs, when it started, and
 * for a monitor how many events it reported and when the last came.
 */
export type ActivityTask = {
  id: string
  kind: 'shell' | 'monitor'
  label: string
  sinceMs: number
  events: number
  lastEventMs: number | null
}

export type ActivityOutcome = 'ok' | 'error' | 'denied' | 'interrupted'

/**
 * One row of the expanded box: a prompt that started a turn, a stretch of
 * thinking, a reply, a tool call or background task, a compaction, a
 * background task's notice, or how a turn ended.
 *
 * A notice row names its task (`taskId`) so a monitor's events in a row fold
 * into one, `count` of them.
 */
export type ActivityEntry = {
  kind: 'turn' | 'thought' | 'reply' | 'tool' | 'compact' | 'notice' | 'end'
  label: string
  durationMs: number | null
  outcome: ActivityOutcome
  taskId?: string
  count?: number
}

/** The session's catch-up: what it is for, where it stands, what comes next. */
export type SummaryLines = {
  goal: string
  status: string
  next: string
}

/**
 * The catch-up the open box draws above its history: the lines of the last
 * update (none before the first), when it ended, whether one runs now, and
 * why the last one failed.
 */
export type ActivitySummary = {
  lines: SummaryLines | null
  updatedAtMs: number | null
  isUpdating: boolean
  error: string | null
}

declare module 'claude-code' {
  interface PluginState {
    'whats-agent-doing-ko': {
      now: ActivityNow
      history: readonly ActivityEntry[]
      agents: readonly ActivityAgent[]
      background: readonly ActivityTask[]
      isExpanded: boolean
      summary: ActivitySummary
    }
  }
}
