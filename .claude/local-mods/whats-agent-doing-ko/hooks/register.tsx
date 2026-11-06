import { atom, read, update } from 'claude-code'
import type { AgentInfo, EngineInterface, Register, Timer, TurnCompleteReason, TurnStepChunk } from 'claude-code'

import type {
  ActivityAgent,
  ActivityEntry,
  ActivityNow,
  ActivityOutcome,
  ActivityPhase,
  ActivitySummary,
  ActivityTask,
} from '../types'
import { activityOf, partialArgsOf } from './activity-of'
import { endingOf, noticesIn, outcomeOfStatus, topicOf } from './background'
import type { Notice } from './background'
import {
  KEEP_SESSIONS,
  NO_SAVED_SUMMARY,
  SUMMARY_MODEL,
  SUMMARY_SYSTEM,
  clockOf,
  fingerprint,
  gitTextOf,
  parseSummary,
  promptOf,
  readOf,
  savedOf,
  startOf,
} from './summary'
import type { SavedSummary } from './summary'
import { clip, durationOf, oneLine, printable, sizeOf, thoughtOf, wordsAdded } from './text'

const IDLE: ActivityNow = {
  phase: 'idle',
  label: '프롬프트 입력 대기',
  sinceMs: 0,
}

const MAX_HISTORY = 100
const MAX_HISTORY_ROWS = 12
const MAX_THOUGHT_CHARS = 70
const MAX_THOUGHT_BUFFER = 2000
const MAX_INPUT_HEAD = 4000
const LARGE_INPUT_CHARS = 1024
const MAX_LABEL_CHARS = 120
const MAX_AGENT_NAME_CHARS = 40
const MAX_AGENT_ROWS = 6
const MAX_TASK_ROWS = 6
const MAX_EVENT_CHARS = 100
/** How long past a monitor's deadline the box waits for its own notice. */
const DEADLINE_GRACE_MS = 15_000
const STREAM_THROTTLE_MS = 250
const TICK_MS = 1000

const now = atom({ plugin: 'whats-agent-doing-ko', key: 'now' } as const, IDLE)

const history = atom(
  { plugin: 'whats-agent-doing-ko', key: 'history' } as const,
  [] as readonly ActivityEntry[],
)

const team = atom(
  { plugin: 'whats-agent-doing-ko', key: 'agents' } as const,
  [] as readonly ActivityAgent[],
)

const background = atom(
  { plugin: 'whats-agent-doing-ko', key: 'background' } as const,
  [] as readonly ActivityTask[],
)

const isExpanded = atom(
  { plugin: 'whats-agent-doing-ko', key: 'isExpanded' } as const,
  false,
)

const NO_SUMMARY: ActivitySummary = { lines: null, updatedAtMs: null, isUpdating: false, error: null }

const summary = atom({ plugin: 'whats-agent-doing-ko', key: 'summary' } as const, NO_SUMMARY)

const SUMMARY_KEY = 'summary:'
const SUMMARY_ORDER_KEY = 'summaryOrder'
const SUMMARY_TIMEOUT_MS = 90_000
const MAX_SUMMARY_ERROR_CHARS = 60
const SUMMARY_LABELS: readonly { key: keyof NonNullable<ActivitySummary['lines']>; label: string }[] = [
  { key: 'goal', label: '목표' },
  { key: 'status', label: '상황' },
  { key: 'next', label: '다음' },
]

const PHASE_COLORS: Record<ActivityPhase, string> = {
  idle: 'gray',
  requesting: 'cyan',
  thinking: 'magenta',
  writing: 'green',
  composing: 'blue',
  tool: 'cyan',
  agent: 'cyan',
  approval: 'yellow',
  question: 'yellow',
  compacting: 'blue',
  waiting: 'cyan',
}

const OUTCOME_MARKS: Record<ActivityOutcome, { mark: string; color: string }> = {
  ok: { mark: '✓', color: 'green' },
  error: { mark: '✗', color: 'red' },
  denied: { mark: '⊘', color: 'yellow' },
  interrupted: { mark: '■', color: 'gray' },
}

const KIND_MARKS: Partial<Record<ActivityEntry['kind'], { mark: string; color: string }>> = {
  turn: { mark: '›', color: 'white' },
  thought: { mark: '∴', color: 'magenta' },
  reply: { mark: '✎', color: 'green' },
  compact: { mark: '⇣', color: 'blue' },
  notice: { mark: '⇠', color: 'cyan' },
}

const TASK_NAMES: Record<ActivityTask['kind'], string> = {
  shell: '셸',
  monitor: '모니터',
}

/** What the model is doing in the main loop, between and around tool calls. */
type ModelPhase = 'idle' | 'requesting' | 'thinking' | 'writing' | 'composing' | 'compacting'

/** A main-loop tool call in flight. */
type Call = {
  tool: string
  label: string
  startMs: number
  isAwaitingApproval: boolean
}

/**
 * A subagent at work, from its spawn (or its first call, when the spawn came
 * before a reload) to its stop. `toolUseId` names the Agent call that started
 * it; `activeMs` is when it last did something.
 */
type Agent = {
  name: string
  label: string
  startMs: number
  activeMs: number
  toolUseId: string | null
  isBackground: boolean
}

/**
 * A background shell or monitor the main loop started, followed from the
 * call that started it to its notice, a TaskStop, or for a monitor its
 * deadline (`deadlineMs`, none when persistent).
 */
type Task = ActivityTask & { deadlineMs: number | null }

/**
 * The module's live view of the session, from which the headline is drawn:
 * started over on a reload, while the headline and history in `$.state` stay.
 */
type Live = {
  calls: Map<string, Call>
  agents: Map<string, Agent>
  agentNames: Map<string, string>
  described: Map<string, string>
  tasks: Map<string, Task>
  turnTopic: string | null
  pendingTopic: string | null
  absorbedPrompts: Set<string>
  phase: ModelPhase
  phaseStartMs: number
  quietSinceMs: number
  turnStartMs: number | null
  turnActions: number
  isFirstRequest: boolean
  resultsToReview: number
  thought: string
  thoughtWords: number
  isThoughtInWord: boolean
  replyWords: number
  isReplyInWord: boolean
  composing: { tool: string; head: string; chars: number } | null
  lastAction: string | null
  lastTurn: string | null
  lastPublishMs: number
  trailing: Timer | null
  isSweeping: boolean
  ticker: Timer | null
}

/**
 * The catch-up's own bookkeeping: whose session the stored record is
 * (`sessionId`, null until read and after a `/clear` or resume), that record,
 * and the update running now with the one asked for meanwhile (`queued`, its
 * `onlyIfWork`).
 */
type CatchUp = {
  sessionId: string | null
  saved: SavedSummary
  isRunning: boolean
  queued: boolean | null
}

/**
 * Registers What's Agent Doing: a box above the prompt naming what Claude is
 * doing right now, and, behind its triangle, what it has done.
 *
 * The model's own stream (`turn.step`) says whether it is reading, thinking
 * (the thought's latest sentence), writing the reply or composing a tool's
 * input; `tool.call` says which tool runs, `classic.PermissionRequest` when
 * one waits for approval, and a subagent's calls show under its Agent call.
 *
 * Background shells and monitors show a row each while they run: from the
 * call's result (`backgroundTaskId`, a Monitor's `taskId`) to the notice the
 * engine delivers as a prompt (`prompt.submit`, origin `task-notification`)
 * or a TaskStop. The engine's own list of in-flight work rides `classic.Stop`,
 * which `cc-plugin-sec-default` sends on past this plugin, so it is not read.
 * Between turns the headline says Claude waits on them, so a turn that ended
 * with work still running never reads as finished.
 *
 * The open box also carries a three-line catch-up of the session (goal,
 * where things stand, what comes next), which Haiku keeps by folding the
 * messages added since the last update into it: at the end of each turn that
 * used a tool, and as the box opens. A closed box asks no model. The session
 * is told by `$.session.id()` before each update and cleared at `session.end`,
 * since `classic.SessionStart` is sent on past this plugin.
 *
 * @param on the engine's registrar
 */
export const register: Register = on => {
  const live: Live = {
    calls: new Map(),
    agents: new Map(),
    agentNames: new Map(),
    described: new Map(),
    tasks: new Map(),
    turnTopic: null,
    pendingTopic: null,
    absorbedPrompts: new Set(),
    phase: 'idle',
    phaseStartMs: 0,
    quietSinceMs: 0,
    turnStartMs: null,
    turnActions: 0,
    isFirstRequest: false,
    resultsToReview: 0,
    thought: '',
    thoughtWords: 0,
    isThoughtInWord: false,
    replyWords: 0,
    isReplyInWord: false,
    composing: null,
    lastAction: null,
    lastTurn: null,
    lastPublishMs: 0,
    trailing: null,
    isSweeping: false,
    ticker: null,
  }

  const catchUp: CatchUp = {
    sessionId: null,
    saved: NO_SAVED_SUMMARY,
    isRunning: false,
    queued: null,
  }

  on('session.start', async ($, e, next) => {
    const result = await next(e)

    await publish($, live)
    await followSession($, catchUp).catch(() => {})

    return result
  })

  // A /clear or a resume goes on under another session's id: its catch-up
  // is read before the next update draws.
  on('session.end', async ($, e, next) => {
    const result = await next(e)

    if (e.reason === 'clear' || e.reason === 'resume') {
      catchUp.sessionId = null
      catchUp.saved = NO_SAVED_SUMMARY
      await update($, summary, () => NO_SUMMARY)
    }

    return result
  })

  // A background task's notice reaches the session as a prompt: the box
  // takes its news here, whether it starts a turn or joins a running one.
  on('prompt.submit', async ($, e, next) => {
    if (e.origin.kind === 'task-notification') {
      try {
        const notices = noticesIn(e.text)

        if (notices.length > 0) {
          live.absorbedPrompts.add(e.text)

          for (const notice of notices) {
            await absorb($, live, notice)
          }

          if (e.turnId === undefined) {
            live.pendingTopic = topicOf(notices)
          }

          await publish($, live)
        }
      } catch {
        // The box never holds a prompt back.
      }
    }

    return next(e)
  })

  on('turn.start', async ($, e, next) => {
    if (live.turnStartMs !== null) {
      return next(e)
    }

    const notices = noticesIn(e.text)

    // A notice the prompt hook took is not taken twice; one whose turn came
    // without that hook is taken here.
    if (notices.length > 0 && live.pendingTopic === null && !live.absorbedPrompts.has(e.text)) {
      for (const notice of notices) {
        await absorb($, live, notice)
      }
    }

    live.turnTopic = notices.length > 0 ? topicOf(notices) : live.pendingTopic
    live.pendingTopic = null
    live.absorbedPrompts.clear()

    live.turnStartMs = await $.clock.now()
    live.turnActions = 0
    live.isFirstRequest = true
    live.resultsToReview = 0
    live.lastAction = null
    live.calls.clear()

    const prompt = oneLine(e.text)

    // A notice's turn is told by the notice's own row, not its markup.
    if (prompt !== '' && notices.length === 0) {
      await remember($, { kind: 'turn', label: clip(prompt, 60), durationMs: null, outcome: 'ok' })
    }

    await enter($, live, 'requesting')

    keepTicking($, live)
    await publish($, live)

    return next(e)
  })

  on('turn.step', async function* ($, e, next) {
    if (e.agentId !== undefined) {
      return yield* next(e)
    }

    await enter($, live, 'requesting')
    live.quietSinceMs = await $.clock.now()
    await publish($, live)

    try {
      for await (const chunk of next(e)) {
        await follow($, live, chunk)
        yield chunk
      }
    } finally {
      live.isFirstRequest = false
      live.composing = null
      await enter($, live, 'requesting')
      await publish($, live)
    }
  })

  on('tool.call', async ($, e, next) => {
    const args = e as unknown as Record<string, unknown>
    const label = e.agentId === undefined ? labelOf(live, e.tool, args) : activityOf(e.tool, args)

    if (e.agentId !== undefined) {
      const agent = await agentOf($, live, e.agentId)

      // Kept after the call: the agent's last action stands while it thinks.
      agent.label = label
      agent.activeMs = await $.clock.now()
      await publish($, live, false)

      try {
        return await next(e)
      } finally {
        agent.label = label
        await publish($, live, false)
      }
    }

    const startMs = await $.clock.now()

    live.calls.set(e.tool_use_id, { tool: e.tool, label, startMs, isAwaitingApproval: false })
    live.lastAction = label
    live.turnActions += 1
    await publish($, live)

    let outcome: ActivityOutcome = 'interrupted'

    try {
      const result = await next(e)

      outcome = result.deny !== undefined ? 'denied' : result.isError ? 'error' : 'ok'

      if (result.deny === undefined) {
        await followTask($, live, e.tool, args, result.result, startMs, outcome)
      }

      if (isAgentTool(e.tool)) {
        const handle = args['name']
        const description = args['description']

        // A named agent runs as a teammate, which the engine lists by the
        // call's `name`; the call's `description` is what to show for it.
        if (typeof handle === 'string' && typeof description === 'string' && oneLine(description) !== '') {
          live.described.set(handle, description)
          void sweep($, live)
        }

        await launched($, live, result.result, description)
      }

      return result
    } finally {
      live.calls.delete(e.tool_use_id)
      live.resultsToReview += 1

      // The step may have ended while the call ran: the review starts with the result.
      if (live.phase === 'requesting') {
        live.phaseStartMs = await $.clock.now()
      }

      await remember($, {
        kind: 'tool',
        label,
        durationMs: (await $.clock.now()) - startMs,
        outcome,
      })

      await publish($, live)
    }
  })

  on('classic.PermissionRequest', async ($, e, next) => {
    if (e.agent_id !== undefined) {
      const agent = await agentOf($, live, e.agent_id)

      agent.label = `승인 대기: ${agent.label}`
    } else {
      const waiting = [...live.calls.values()]
        .reverse()
        .find(call => call.tool === e.tool_name && !call.isAwaitingApproval)

      if (waiting) {
        waiting.isAwaitingApproval = true
      }
    }

    await publish($, live)

    return next(e)
  })

  // The run-in-background pill shows only under a call that is running, so
  // its drawing means the person has answered the approval it waited on.
  on('ui.render', { component: 'ToolProgress' }, ($, e, next) => {
    const call = live.calls.get(e.props.tool_use_id)

    if (call?.isAwaitingApproval) {
      call.isAwaitingApproval = false
      $.clock.after(0, () => void publish($, live))
    }

    return next(e)
  })

  on('agent.spawn', async ($, e, next) => {
    const result = await next(e)

    if (result.agentId !== undefined) {
      const startMs = await $.clock.now()
      const name = e.description || e.name || '에이전트'

      live.agentNames.set(result.agentId, name)
      live.agents.set(result.agentId, {
        name,
        label: '시작 준비 중',
        startMs,
        activeMs: startMs,
        toolUseId: e.tool_use_id,
        isBackground: e.background,
      })
      keepTicking($, live)
      await publish($, live)
    }

    return result
  })

  on('classic.SubagentStop', async ($, e, next) => {
    await finish($, live, e.agent_id, 'ok')

    return next(e)
  })

  on('classic.PreCompact', async ($, e, next) => {
    await enter($, live, 'compacting')
    await publish($, live)

    return next(e)
  })

  on('classic.PostCompact', async ($, e, next) => {
    const durationMs = (await $.clock.now()) - live.phaseStartMs

    live.phase = live.turnStartMs === null ? 'idle' : 'requesting'
    await remember($, { kind: 'compact', label: '대화 압축 완료', durationMs, outcome: 'ok' })
    await publish($, live)

    return next(e)
  })

  on('turn.complete', async ($, e, next) => {
    if (e.agentId !== undefined) {
      await finish($, live, e.agentId, outcomeOf(e.reason))

      return next(e)
    }

    // Not awaited: the turn ends without waiting on the catch-up.
    void foldWhileOpen($, catchUp)

    if (live.turnStartMs === null) {
      return next(e)
    }

    const durationMs = (await $.clock.now()) - live.turnStartMs
    const took = durationOf(durationMs)

    const [label, outcome]: [string, ActivityOutcome] =
      e.reason === 'aborted'
        ? [`${took} 만에 중단`, 'interrupted']
        : e.reason === 'error'
          ? [`${took} 만에 오류로 중단`, 'error']
          : e.reason === 'refusal'
            ? ['요청 거절', 'denied']
            : [`${took} 만에 완료, 동작 ${live.turnActions}개`, 'ok']

    await enter($, live, 'idle')
    await remember($, { kind: 'end', label: capitalized(label), durationMs, outcome })

    live.lastTurn = label
    live.turnStartMs = null
    live.turnTopic = null
    live.absorbedPrompts.clear()
    live.calls.clear()

    keepTicking($, live)
    await publish($, live)

    return next(e)
  })

  on('ui.render', { component: 'AbovePrompt' }, async ($, e, next) => {
    if (e.props.hasSurvey) {
      return next(e)
    }

    // The band is one site for every plugin: what the plugins beneath draw
    // stays, under the box, so another mod's band is never hidden.
    const below = await next(e)

    const { Box, Text, Button } = $.ui.resolve(e)

    const headline = await read($, now)
    const entries = await read($, history)
    const working = await read($, team)
    const tasks = await read($, background)
    const isOpen = await read($, isExpanded)
    const nowMs = await $.clock.now()

    const isWorking = headline.phase !== 'idle'
    const color = PHASE_COLORS[headline.phase]
    const elapsed = isWorking ? ` · ${durationOf(nowMs - headline.sinceMs)}` : ''

    // One agent the headline names; more, or one beside the main loop's
    // work, get a row each.
    const isTeamListed = working.length > 1 || (working.length === 1 && headline.phase !== 'agent')
    const listed = isTeamListed ? working.slice(-MAX_AGENT_ROWS) : []
    const unlisted = isTeamListed ? working.length - listed.length : 0

    const agentRows = listed.map((agent, i) => (
      <Box key={`agent-${i}`} flexDirection="row">
        <Box flexShrink={0}>
          <Text color={PHASE_COLORS.agent}>{'◆ '}</Text>
        </Box>
        <Text bold wrap="truncate-end">{printable(agent.name, MAX_AGENT_NAME_CHARS)}</Text>
        <Text wrap="truncate-end">{` › ${printable(agent.label, MAX_LABEL_CHARS)}`}</Text>
        <Box flexShrink={0}>
          <Text dimColor>{`  ${durationOf(nowMs - agent.sinceMs)}`}</Text>
        </Box>
      </Box>
    ))

    const moreAgents = unlisted > 0 ? [<Text dimColor>{`… 에이전트 ${unlisted}개 더`}</Text>] : []

    const teamBox = isTeamListed
      ? [
          <Box key="agents" flexDirection="column">
            {agentRows}
            {moreAgents}
          </Box>,
        ]
      : []

    const shownTasks = tasks.slice(-MAX_TASK_ROWS)
    const unshownTasks = tasks.length - shownTasks.length

    const taskRows = shownTasks.map((task, i) => (
      <Box key={`task-${i}`} flexDirection="row">
        <Box flexShrink={0}>
          <Text color={PHASE_COLORS.waiting}>{task.kind === 'shell' ? '$ ' : '◉ '}</Text>
          <Text bold>{TASK_NAMES[task.kind]}</Text>
        </Box>
        <Text wrap="truncate-end">{` › ${printable(task.label, MAX_LABEL_CHARS)}${eventsOf(task, nowMs)}`}</Text>
        <Box flexShrink={0}>
          <Text dimColor>{`  ${durationOf(nowMs - task.sinceMs)}`}</Text>
        </Box>
      </Box>
    ))

    const moreTasks = unshownTasks > 0 ? [<Text dimColor>{`… 백그라운드 ${unshownTasks}개 더`}</Text>] : []

    const taskBox = tasks.length > 0
      ? [
          <Box key="background" flexDirection="column">
            {taskRows}
            {moreTasks}
          </Box>,
        ]
      : []

    const catchUpRows = (isOpen ? catchUpLinesOf(await read($, summary)) : []).map((line, i) => (
      <Box key={`summary-${i}`} flexDirection="row">
        {line.label === null ? [] : [
          <Box key="label" flexShrink={0}>
            <Text color="cyan" bold>{`${line.label} `}</Text>
          </Box>,
        ]}
        {line.isError
          ? <Text color="red" wrap="truncate-end">{line.text}</Text>
          : <Text dimColor={line.label === null} wrap="truncate-end">{line.text}</Text>}
        <Box flexShrink={0}>
          <Text dimColor>{line.note === '' ? '' : `  ${line.note}`}</Text>
        </Box>
      </Box>
    ))

    // The catch-up's rows and the margin above them come out of the history's.
    const teamRows = listed.length + moreAgents.length + taskRows.length + moreTasks.length
    const summaryRows = catchUpRows.length > 0 ? catchUpRows.length + 1 : 0
    const rows = Math.min(MAX_HISTORY_ROWS, Math.max(1, e.props.maxRows - 4 - teamRows - summaryRows))
    const shown = entries.slice(-rows)
    const hidden = entries.length - shown.length

    const historyRows = shown.map((entry, i) => {
      const { mark, color: markColor } = KIND_MARKS[entry.kind] ?? OUTCOME_MARKS[entry.outcome]
      const isQuiet = entry.kind === 'thought' || entry.kind === 'end'
      const took = entry.durationMs === null ? '' : `  ${durationOf(entry.durationMs)}`

      return (
        <Box key={`row-${i}`} flexDirection="row">
          <Box flexShrink={0}>
            <Text color={markColor}>{`${mark} `}</Text>
          </Box>
          <Text bold={entry.kind === 'turn'} dimColor={isQuiet} wrap="truncate-end">
            {printable(entry.label, MAX_LABEL_CHARS)}
          </Text>
          <Box flexShrink={0}>
            <Text dimColor>{took}</Text>
          </Box>
        </Box>
      )
    })

    const earlier = hidden > 0 ? [<Text dimColor>{`… 이전 ${hidden}개`}</Text>] : []
    const empty = entries.length === 0 ? [<Text dimColor>아직 동작 없음</Text>] : []

    const body = isOpen
      ? [
          <Box key="summary" flexDirection="column" marginTop={1}>
            {catchUpRows}
          </Box>,
          <Box key="history" flexDirection="column" marginTop={1}>
            {earlier}
            {historyRows}
            {empty}
          </Box>,
        ]
      : []

    return (
      <Box flexDirection="column">
        <Box
          flexDirection="column"
          borderStyle="round"
          borderColor={isWorking ? color : 'gray'}
          paddingX={1}
          alignSelf="flex-start"
        >
          <Box key="activity" flexDirection="row">
            <Button
              key="toggle"
              plain
              label={isOpen ? '▾' : '▸'}
              onPress={() => toggle($, catchUp)}
            />
            {/* On a narrow screen only the label gives way: the rest would wrap. */}
            <Box flexShrink={0}>
              <Text color={color}>{isWorking ? ' ● ' : ' ○ '}</Text>
              <Text bold>Claude: </Text>
            </Box>
            <Text wrap="truncate-end">{printable(headline.label, MAX_LABEL_CHARS)}</Text>
            <Box flexShrink={0}>
              <Text dimColor>{elapsed}</Text>
            </Box>
          </Box>
          {teamBox}
          {taskBox}
          {body}
        </Box>
        {below}
      </Box>
    )
  })
}

/** The headline as the live view has it. */
function current(live: Live): ActivityNow {
  if (live.phase === 'compacting') {
    return { phase: 'compacting', label: '대화 압축 중', sinceMs: live.phaseStartMs }
  }

  const open = [...live.calls.entries()]
  const [newestId, newest] = open.at(-1) ?? []

  if (newestId !== undefined && newest !== undefined) {
    const more = open.length > 1 ? ` (외 ${open.length - 1}개)` : ''

    if (newest.isAwaitingApproval) {
      return {
        phase: 'approval',
        label: `승인 대기: ${newest.label}${more}`,
        sinceMs: newest.startMs,
      }
    }

    if (newest.tool === 'AskUserQuestion') {
      return { phase: 'question', label: newest.label, sinceMs: newest.startMs }
    }

    const inner = isAgentTool(newest.tool) ? agentUnder(live, newestId)?.label : undefined

    return inner === undefined
      ? { phase: 'tool', label: `${newest.label}${more}`, sinceMs: newest.startMs }
      : { phase: 'agent', label: `${newest.label} › ${inner}${more}`, sinceMs: newest.startMs }
  }

  if (live.phase === 'idle' && live.agents.size > 0) {
    // Between turns, background agents may still be at work.
    const working = [...live.agents.values()]
    const sinceMs = Math.min(...working.map(agent => agent.startMs))
    const busiest = working.reduce((a, b) => (b.activeMs > a.activeMs ? b : a))

    return {
      phase: 'agent',
      label: working.length === 1
        ? `${busiest.name} › ${busiest.label}`
        : `에이전트 ${working.length}개 작업 중`,
      sinceMs,
    }
  }

  if (live.phase === 'idle' && live.tasks.size > 0) {
    // The turn ended on work its notices will wake it for: waiting, not done.
    const startMs = Math.min(...[...live.tasks.values()].map(task => task.sinceMs))
    const last = live.lastTurn === null ? '' : ` · 지난 턴 ${live.lastTurn}`

    return {
      phase: 'waiting',
      label: `백그라운드 ${live.tasks.size}개 완료 대기${last}`,
      sinceMs: Math.max(live.phaseStartMs, startMs),
    }
  }

  const sinceMs = live.phaseStartMs

  switch (live.phase) {
    case 'requesting':
      return {
        phase: 'requesting',
        label: live.isFirstRequest
          ? live.turnTopic ?? '프롬프트 읽는 중'
          : live.resultsToReview > 0
            ? `동작 ${live.resultsToReview}개 결과 검토 중`
            : '모델 응답 대기',
        sinceMs,
      }
    case 'thinking': {
      // With no thought text to quote, the last action stands, the dot
      // turned to thinking's color; before any, the prompt is what it reads.
      const snippet = thoughtOf(live.thought, MAX_THOUGHT_CHARS)
      const label = snippet ? `생각 중: ${snippet}` : standingLabel(live)

      return { phase: 'thinking', label, sinceMs }
    }
    case 'writing':
      return {
        phase: 'writing',
        label: `답변 작성 중 · ${live.replyWords}단어`,
        sinceMs,
      }
    case 'composing': {
      // A call is named once its input says what it does: a file tool by
      // its path, a command by its description, which streams after the
      // command itself. Until then the last action stands.
      const { composing } = live
      const args = composing ? partialArgsOf(composing.head) : {}
      const isDescribed = composing !== null && (isShellTool(composing.tool)
        ? typeof args['description'] === 'string'
        : Object.keys(args).length > 0)

      if (!composing || !isDescribed) {
        return { phase: 'composing', label: standingLabel(live), sinceMs }
      }

      const size = composing.chars >= LARGE_INPUT_CHARS ? ` · ${sizeOf(composing.chars)}` : ''

      return { phase: 'composing', label: `${activityOf(composing.tool, args)}${size}`, sinceMs }
    }
    case 'idle':
      return live.lastTurn === null
        ? IDLE
        : { ...IDLE, label: `대기 · 지난 턴 ${live.lastTurn}` }
  }
}

/**
 * Writes the headline. A streaming update (`isUrgent` false) lands at most
 * every STREAM_THROTTLE_MS, the last one held on a timer.
 */
async function publish($: EngineInterface, live: Live, isUrgent = true): Promise<void> {
  const nowMs = await $.clock.now()

  if (!isUrgent && nowMs - live.lastPublishMs < STREAM_THROTTLE_MS) {
    live.trailing ??= $.clock.after(STREAM_THROTTLE_MS, () => {
      live.trailing = null
      void publish($, live)
    })

    return
  }

  live.lastPublishMs = nowMs
  const headline = current(live)
  const working = [...live.agents.values()].map(agent => ({
    name: agent.name,
    label: agent.label,
    sinceMs: agent.startMs,
  }))

  const tasks = [...live.tasks.values()].map(task => ({
    id: task.id,
    kind: task.kind,
    label: task.label,
    sinceMs: task.sinceMs,
    events: task.events,
    lastEventMs: task.lastEventMs,
  }))

  await update($, now, () => headline)
  await update($, team, () => working)
  await update($, background, () => tasks)
}

/** The agent at work under `agentId`, taken up if its spawn came before a reload. */
async function agentOf($: EngineInterface, live: Live, agentId: string): Promise<Agent> {
  const known = live.agents.get(agentId)

  if (known) {
    return known
  }

  const startMs = await $.clock.now()
  const agent: Agent = {
    name: live.agentNames.get(agentId) ?? '에이전트',
    label: '시작 준비 중',
    startMs,
    activeMs: startMs,
    toolUseId: null,
    isBackground: true,
  }

  live.agents.set(agentId, agent)
  keepTicking($, live)

  return agent
}

/**
 * Lets an agent go. A background agent's run is recorded in the history; a
 * foreground one's is its Agent call's row.
 */
async function finish(
  $: EngineInterface,
  live: Live,
  agentId: string,
  outcome: ActivityOutcome,
): Promise<void> {
  const agent = live.agents.get(agentId)

  if (!agent) {
    return
  }

  live.agents.delete(agentId)

  if (agent.isBackground) {
    await remember($, {
      kind: 'tool',
      label: `에이전트: ${agent.name}`,
      durationMs: (await $.clock.now()) - agent.startMs,
      outcome,
    })
  }

  keepTicking($, live)
  await publish($, live)
}

/**
 * Names agents first met by their calls, and lets go of agents the engine
 * lists as no longer running, in case their stop never reached the box.
 */
async function sweep($: EngineInterface, live: Live): Promise<void> {
  if (live.agents.size === 0 || live.isSweeping) {
    return
  }

  live.isSweeping = true

  try {
    await sweepListed($, live, await $.agent.list())
  } catch {
    // The list is a backstop; the box keeps going without it.
  } finally {
    live.isSweeping = false
  }
}

async function sweepListed($: EngineInterface, live: Live, listed: readonly AgentInfo[]): Promise<void> {
  const statuses = new Map(listed.map(info => [info.id, info.status]))
  let isRenamed = false

  for (const [agentId, agent] of [...live.agents.entries()]) {
    const status = statuses.get(agentId)

    // An agent first met by its calls is named once the engine lists it.
    if (!live.agentNames.has(agentId)) {
      const handle = listed.find(info => info.id === agentId)?.name
      const described = handle === undefined ? undefined : live.described.get(handle)

      if (described !== undefined) {
        live.agentNames.set(agentId, described)
      }

      const name = described ?? handle

      if (name !== undefined && name !== agent.name) {
        agent.name = name
        isRenamed = true
      }
    }

    if (status !== undefined && status !== 'running') {
      await finish($, live, agentId, status === 'completed' ? 'ok' : status === 'failed' ? 'error' : 'interrupted')
    }
  }

  if (isRenamed) {
    await publish($, live)
  }
}

/**
 * Keeps the elapsed time ticking while a turn runs, an agent works or a
 * background task runs, and stops it once none does.
 */
function keepTicking($: EngineInterface, live: Live): void {
  const isBusy = live.turnStartMs !== null || live.agents.size > 0 || live.tasks.size > 0

  if (isBusy && live.ticker === null) {
    live.ticker = $.clock.every(TICK_MS, () => {
      $.ui.invalidate('ui.render')
      void sweep($, live)
      void expire($, live)
    })
  } else if (!isBusy && live.ticker !== null) {
    live.ticker.cancel()
    live.ticker = null
  }
}

async function remember($: EngineInterface, entry: ActivityEntry): Promise<void> {
  await update($, history, entries => [...entries, entry].slice(-MAX_HISTORY))
}

/** Enters a model phase, recording the thinking or reply block it closes. */
async function enter($: EngineInterface, live: Live, next: ModelPhase): Promise<void> {
  if (next === live.phase) {
    return
  }

  const nowMs = await $.clock.now()
  const durationMs = nowMs - live.phaseStartMs

  if (live.phase === 'thinking') {
    const words = live.thoughtWords

    await remember($, {
      kind: 'thought',
      label: words > 0 ? `생각 (${words}단어)` : '생각',
      durationMs,
      outcome: 'ok',
    })
  }

  if (live.phase === 'writing' && live.replyWords > 0) {
    await remember($, {
      kind: 'reply',
      label: `답변 작성 (${live.replyWords}단어)`,
      durationMs,
      outcome: 'ok',
    })
  }

  if (next === 'thinking') {
    live.thought = ''
    live.thoughtWords = 0
    live.isThoughtInWord = false
  }

  if (next === 'writing') {
    live.replyWords = 0
    live.isReplyInWord = false
  }

  live.phase = next
  live.phaseStartMs = nowMs
}

/** Follows one chunk of the main loop's response. */
async function follow($: EngineInterface, live: Live, chunk: TurnStepChunk): Promise<void> {
  if (chunk.kind === 'text' || chunk.kind === 'tool' || chunk.kind === 'input') {
    live.quietSinceMs = await $.clock.now()
  }

  switch (chunk.kind) {
    case 'thinking': {
      const isNew = live.phase !== 'thinking'

      await enter($, live, 'thinking')

      // A thought is timed from when the model went quiet (the step's start or
      // the last block's last chunk): without summaries its one chunk, empty,
      // comes only as it ends, and summaries come in bursts behind the thought.
      if (isNew && live.quietSinceMs > 0) {
        live.phaseStartMs = Math.min(live.phaseStartMs, live.quietSinceMs)
      }

      live.resultsToReview = 0
      const { added, isInWord } = wordsAdded(chunk.text, live.isThoughtInWord)

      live.thought = (live.thought + chunk.text).slice(-MAX_THOUGHT_BUFFER)
      live.thoughtWords += added
      live.isThoughtInWord = isInWord
      await publish($, live, isNew)

      return
    }
    case 'text': {
      const isNew = live.phase !== 'writing'

      await enter($, live, 'writing')
      live.resultsToReview = 0
      const { added, isInWord } = wordsAdded(chunk.text, live.isReplyInWord)

      live.replyWords += added
      live.isReplyInWord = isInWord
      await publish($, live, isNew)

      return
    }
    case 'tool':
      await enter($, live, 'composing')
      live.resultsToReview = 0
      live.composing = { tool: chunk.name, head: '', chars: 0 }
      await publish($, live)

      return
    case 'input': {
      const { composing } = live

      if (composing) {
        composing.chars += chunk.json.length

        if (composing.head.length < MAX_INPUT_HEAD) {
          composing.head += chunk.json
        }

        await publish($, live, false)
      }

      return
    }
  }
}

/**
 * What the box keeps saying while Claude thinks or writes a call it cannot
 * name yet: the turn's last action, or the prompt (or notice) before there
 * is one.
 */
function standingLabel(live: Live): string {
  return live.lastAction ?? live.turnTopic ?? '프롬프트 읽는 중'
}

/**
 * Names a background agent by the Agent call that launched it: the call's
 * short `description`, keyed by the id its result carries (the result's own
 * `description` can hold the whole prompt). Its own calls may already have
 * shown it.
 */
async function launched(
  $: EngineInterface,
  live: Live,
  result: unknown,
  asked: unknown,
): Promise<void> {
  if (typeof result !== 'object' || result === null) {
    return
  }

  const { agentId, description, status } = result as Record<string, unknown>

  if (typeof agentId !== 'string' || status !== 'async_launched') {
    return
  }

  const name = [asked, description].find(
    (text): text is string => typeof text === 'string' && oneLine(text) !== '',
  )

  if (name === undefined) {
    return
  }

  const agent = await agentOf($, live, agentId)

  live.agentNames.set(agentId, name)
  agent.name = name
  await publish($, live)
}

/** The agent an Agent call started, or the one that last did something. */
function agentUnder(live: Live, toolUseId: string): Agent | undefined {
  const working = [...live.agents.values()]

  return working.find(agent => agent.toolUseId === toolUseId)
    ?? working.reduce<Agent | undefined>((a, b) => (a === undefined || b.activeMs > a.activeMs ? b : a), undefined)
}

function outcomeOf(reason: TurnCompleteReason): ActivityOutcome {
  return reason === 'aborted' ? 'interrupted' : reason === 'error' ? 'error' : reason === 'refusal' ? 'denied' : 'ok'
}

function isShellTool(tool: string): boolean {
  return tool === 'Bash' || tool === 'PowerShell'
}

function isAgentTool(tool: string): boolean {
  return tool === 'Agent' || tool === 'Task'
}

function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** A main-loop call's label, a TaskStop named by the task it stops. */
function labelOf(live: Live, tool: string, args: Record<string, unknown>): string {
  if (tool === 'TaskStop') {
    const id = args['task_id'] ?? args['shell_id']
    const task = typeof id === 'string' ? live.tasks.get(id) : undefined

    if (task) {
      return `${TASK_NAMES[task.kind]} 중지: ${task.label}`
    }
  }

  return activityOf(tool, args)
}

/**
 * Takes up the background task a main-loop call started (a Bash call run in
 * the background, moved there by ctrl+b or by its timeout, or a Monitor), or
 * lets go of the one a TaskStop stopped, which sends no notice of its own.
 */
async function followTask(
  $: EngineInterface,
  live: Live,
  tool: string,
  args: Record<string, unknown>,
  record: unknown,
  startMs: number,
  outcome: ActivityOutcome,
): Promise<void> {
  if (typeof record !== 'object' || record === null) {
    return
  }

  const fields = record as Record<string, unknown>

  if (tool === 'TaskStop') {
    const id = [fields['task_id'], args['task_id'], args['shell_id']].find(
      (value): value is string => typeof value === 'string',
    )

    if (outcome === 'ok' && id !== undefined) {
      await finishTask($, live, id, 'interrupted', ' (중지)')
    }

    return
  }

  const isMonitor = tool === 'Monitor'
  const id = isMonitor ? fields['taskId'] : isShellTool(tool) ? fields['backgroundTaskId'] : undefined

  if (typeof id !== 'string' || id === '') {
    return
  }

  const timeoutMs = isMonitor && typeof fields['timeoutMs'] === 'number' ? fields['timeoutMs'] : 0

  live.tasks.set(id, {
    id,
    kind: isMonitor ? 'monitor' : 'shell',
    label: taskLabelOf(tool, args),
    sinceMs: startMs,
    events: 0,
    lastEventMs: null,
    deadlineMs: timeoutMs > 0 ? startMs + timeoutMs : null,
  })
  keepTicking($, live)
}

/** A background task's name: its call's description, else its command. */
function taskLabelOf(tool: string, args: Record<string, unknown>): string {
  const text = [args['description'], args['command']].find(
    (value): value is string => typeof value === 'string' && oneLine(value) !== '',
  )

  return text === undefined ? tool : clip(oneLine(text), MAX_LABEL_CHARS)
}

/**
 * Takes one notice: a monitor's event counts toward its task and folds into
 * the history's event row; an end lets the task go with its outcome. An
 * agent's end is the agent's own row (`finish`), and a notice of no task is
 * a row of its own.
 */
async function absorb($: EngineInterface, live: Live, notice: Notice): Promise<void> {
  const nowMs = await $.clock.now()
  const id = notice.taskId
  const task = id === null ? undefined : live.tasks.get(id)

  if (notice.event !== null) {
    if (task) {
      task.events += 1
      task.lastEventMs = nowMs
    }

    await rememberEvent($, id, notice.event)
  }

  if (notice.status === null) {
    if (notice.event === null && id === null && notice.body !== '') {
      await remember($, { kind: 'notice', label: `알림: ${clip(notice.body, MAX_EVENT_CHARS)}`, durationMs: null, outcome: 'ok' })
    }

    return
  }

  if (notice.kind === 'agent') {
    return
  }

  if (id !== null) {
    live.tasks.delete(id)
  }

  const kind = task ? TASK_NAMES[task.kind] : notice.kind === 'shell' || notice.kind === 'monitor' ? TASK_NAMES[notice.kind] : '작업'
  const name = task?.label ?? notice.name ?? id ?? ''
  const events = task?.kind === 'monitor' ? ` · 이벤트 ${task.events}개` : ''

  await remember($, {
    kind: 'tool',
    label: `${kind}: ${name}${endingOf(notice)}${events}`,
    durationMs: task ? nowMs - task.sinceMs : null,
    outcome: outcomeOfStatus(notice.status),
  })

  keepTicking($, live)
}

/** A monitor's event as a history row, folded into the row of the events just before it. */
async function rememberEvent($: EngineInterface, taskId: string | null, event: string): Promise<void> {
  const text = clip(event, MAX_EVENT_CHARS)

  await update($, history, entries => {
    const last = entries.at(-1)

    if (taskId !== null && last?.kind === 'notice' && last.taskId === taskId) {
      const count = (last.count ?? 1) + 1

      return [...entries.slice(0, -1), { ...last, label: `모니터 이벤트 ${count}개: ${text}`, count }]
    }

    const entry: ActivityEntry = taskId === null
      ? { kind: 'notice', label: `모니터 이벤트: ${text}`, durationMs: null, outcome: 'ok', count: 1 }
      : { kind: 'notice', label: `모니터 이벤트: ${text}`, durationMs: null, outcome: 'ok', taskId, count: 1 }

    return [...entries, entry].slice(-MAX_HISTORY)
  })
}

/** Lets a task go before its notice came: a TaskStop, or a monitor past its deadline. */
async function finishTask(
  $: EngineInterface,
  live: Live,
  id: string,
  outcome: ActivityOutcome,
  ending: string,
): Promise<void> {
  const task = live.tasks.get(id)

  if (!task) {
    return
  }

  live.tasks.delete(id)

  await remember($, {
    kind: 'tool',
    label: `${TASK_NAMES[task.kind]}: ${task.label}${ending}`,
    durationMs: (await $.clock.now()) - task.sinceMs,
    outcome,
  })

  keepTicking($, live)
  await publish($, live)
}

/** Lets go of monitors past their deadline whose notice never came. */
async function expire($: EngineInterface, live: Live): Promise<void> {
  const nowMs = await $.clock.now()

  for (const task of [...live.tasks.values()]) {
    if (task.deadlineMs !== null && nowMs > task.deadlineMs + DEADLINE_GRACE_MS) {
      await finishTask($, live, task.id, 'interrupted', ' (기한 만료)')
    }
  }
}

/** A monitor row's events: how many, and how long since the last. */
function eventsOf(task: ActivityTask, nowMs: number): string {
  if (task.kind !== 'monitor') {
    return ''
  }

  return task.lastEventMs === null
    ? ' · 이벤트 없음'
    : ` · 이벤트 ${task.events}개, 마지막 ${durationOf(nowMs - task.lastEventMs)} 전`
}

/**
 * The catch-up as the open box draws it: its three lines, the first carrying
 * when it was written or that an update runs, and a line for a failure; one
 * quiet line before the first update has written any.
 */
function catchUpLinesOf(
  current: ActivitySummary,
): { label: string | null; text: string; note: string; isError: boolean }[] {
  const error = current.error === null ? [] : [{ label: null, text: `요약 갱신 실패: ${current.error}`, note: '', isError: true }]

  if (current.lines === null) {
    const waiting = current.isUpdating ? '요약 만드는 중' : '아직 요약 없음'

    return current.error === null ? [{ label: null, text: waiting, note: '', isError: false }] : error
  }

  const { lines } = current
  const when = current.isUpdating ? '갱신 중' : current.updatedAtMs === null ? '' : `${clockOf(current.updatedAtMs)} 기준`

  return [
    ...SUMMARY_LABELS.map(({ key, label }, i) => ({
      label,
      text: printable(lines[key], MAX_LABEL_CHARS),
      note: i === 0 ? when : '',
      isError: false,
    })),
    ...error,
  ]
}

/** Opens or closes the box; opening folds in what the catch-up has not seen. */
async function toggle($: EngineInterface, catchUp: CatchUp): Promise<void> {
  const isOpen = await update($, isExpanded, open => !open)

  if (isOpen) {
    await summarize($, catchUp, false)
  }
}

/** At a turn's end, updates the catch-up when the box is open on some surface. */
async function foldWhileOpen($: EngineInterface, catchUp: CatchUp): Promise<void> {
  if (!(await read($, isExpanded))) {
    return
  }

  // A plain -p run draws nowhere, so nothing would read the catch-up.
  const surfaces = await $.session.surfaces().catch(() => [])

  if (surfaces.length > 0) {
    await summarize($, catchUp, true)
  }
}

/**
 * Reads the stored catch-up of the session the process runs now, when it is
 * not the one read last: at the start, and after a `/clear`, a resume or a
 * `/branch` moved the process to another id.
 */
async function followSession($: EngineInterface, catchUp: CatchUp): Promise<void> {
  const id = await $.session.id()

  if (id === catchUp.sessionId) {
    return
  }

  catchUp.sessionId = id
  catchUp.saved = savedOf(await $.store.get(SUMMARY_KEY + id))

  const { lines, updatedAtMs } = catchUp.saved

  await update($, summary, () => ({ lines, updatedAtMs, isUpdating: false, error: null }))
}

/**
 * Folds the messages added since the last update into the catch-up in one
 * Haiku call, however many there are ([readOf]). With `onlyIfWork`, skips
 * when none of them used a tool (a turn of talk alone is folded in at the
 * next update). One update runs at a time; one asked for meanwhile runs
 * after it.
 */
async function summarize($: EngineInterface, catchUp: CatchUp, onlyIfWork: boolean): Promise<void> {
  if (catchUp.isRunning) {
    catchUp.queued = (catchUp.queued ?? true) && onlyIfWork

    return
  }

  catchUp.isRunning = true

  try {
    await followSession($, catchUp)

    const id = catchUp.sessionId
    const messages = await $.session.messages()
    const start = startOf(messages, catchUp.saved)
    const fresh = messages.slice(start)
    const last = messages.at(-1)

    if (last === undefined || fresh.length === 0 || (onlyIfWork && !fresh.some(message => message.toolUses.length > 0))) {
      return
    }

    await update($, summary, current => ({ ...current, isUpdating: true, error: null }))

    const git = await gitStateOf($)
    const isFirstRead = start === 0 && catchUp.saved.lines === null
    const reply = await $.model.complete({
      model: SUMMARY_MODEL,
      system: SUMMARY_SYSTEM,
      prompt: promptOf(catchUp.saved, readOf(messages, start, isFirstRead), git),
      maxTokens: 1200,
      timeoutMs: SUMMARY_TIMEOUT_MS,
    })

    if (!reply.isAnswered) {
      throw new Error(
        reply.reason === 'api-error'
          ? `API 오류 (${reply.status ?? '응답 없음'}, ${reply.error})`
          : reply.reason === 'aborted'
            ? '시간 초과'
            : '빈 응답',
      )
    }

    // A /clear or a resume moved the process on while the model ran.
    if (catchUp.sessionId !== id || id === null) {
      return
    }

    const parsed = parseSummary(reply.text)

    if (parsed === null) {
      throw new Error('형식이 맞지 않는 응답')
    }

    const updatedAtMs = await $.clock.now()

    catchUp.saved = { ...parsed, covered: messages.length, marker: fingerprint(last), updatedAtMs }
    await saveCatchUp($, id, catchUp.saved)
    await update($, summary, current => ({ ...current, lines: parsed.lines, updatedAtMs }))
  } catch (error) {
    const reason = printable(error instanceof Error ? error.message : String(error), MAX_SUMMARY_ERROR_CHARS)

    await update($, summary, current => ({ ...current, error: reason })).catch(() => {})
  } finally {
    await update($, summary, current => (current.isUpdating ? { ...current, isUpdating: false } : current)).catch(() => {})

    catchUp.isRunning = false

    if (catchUp.queued !== null) {
      const again = catchUp.queued

      catchUp.queued = null
      void summarize($, catchUp, again)
    }
  }
}

/** Stores a session's catch-up, dropping those of the oldest sessions past `KEEP_SESSIONS`. */
async function saveCatchUp($: EngineInterface, id: string, saved: SavedSummary): Promise<void> {
  await $.store.set(SUMMARY_KEY + id, saved)

  const stored = await $.store.get(SUMMARY_ORDER_KEY)
  const earlier = Array.isArray(stored) ? stored.filter((other): other is string => typeof other === 'string' && other !== id) : []
  const order = [id, ...earlier]

  for (const old of order.slice(KEEP_SESSIONS)) {
    await $.store.delete(SUMMARY_KEY + old)
  }

  await $.store.set(SUMMARY_ORDER_KEY, order.slice(0, KEEP_SESSIONS))
}

/** `git status` and the latest commits of the session's directory, so the catch-up checks claims of work against the disk. */
async function gitStateOf($: EngineInterface): Promise<string> {
  const results: { argv: readonly string[]; output: string }[] = []

  for (const argv of [['git', 'status', '--short'], ['git', 'log', '--oneline', '-n', '3']]) {
    const output = await $.process
      .run(argv)
      .then(run => (run.exitCode === 0 ? run.stdout : run.stderr))
      .catch(() => '(실행 실패)')

    results.push({ argv, output })
  }

  return gitTextOf(results)
}
