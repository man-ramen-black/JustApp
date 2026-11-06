import type { On, TurnStepChunk } from 'claude-code'
import { describe, expect, mock, test } from 'claude-code/testing'
import type { Engine, MockClock } from 'claude-code/testing'

const BAND = {
  plugin: 'whats-agent-doing-ko',
  surface: 'terminal',
  component: 'AbovePrompt',
  props: {
    hasSurvey: false,
    isWorking: false,
    maxRows: 20,
    bodyColumns: 100,
    scroll: { offset: 0, bodyRows: 20 },
    view: {},
  },
} as const

const DONE = {
  answer: 'All fixed.',
  durationMs: 1000,
  isAborted: false,
  reason: 'answer',
  category: null,
  explanation: null,
} as const

const SPAWN = {
  prompt: 'Do the work.',
  subagentType: 'general-purpose',
  provider: { plugin: 'engine', tier: 'core' },
  parentModel: 'claude-opus-5-5',
  background: true,
  fork: false,
} as const

const STEP = { turnId: 't1', model: 'claude-opus-5-5', messageCount: 1 } as const

type ToolArgs = Parameters<Engine['tool']['call']>[0]

/** A background shell's end as the engine delivers it, a prompt of its own. */
function shellNotice(id: string, status: string, summary: string): string {
  return [
    '<task-notification>',
    `<task-id>${id}</task-id>`,
    '<tool-use-id>toolu_1</tool-use-id>',
    `<output-file>/tmp/${id}.output</output-file>`,
    `<status>${status}</status>`,
    `<summary>${summary}</summary>`,
    '</task-notification>',
  ].join('\n')
}

/** One line a monitor's command printed, delivered as a notice with no status. */
function monitorEvent(id: string, description: string, event: string): string {
  return [
    '<task-notification>',
    `<task-id>${id}</task-id>`,
    `<summary>Monitor event: "${description}"</summary>`,
    `<event>${event}</event>`,
    '</task-notification>',
  ].join('\n')
}

/** Delivers a notice as the engine does: `prompt.submit` from a task notification. */
async function notify($: Engine, text: string) {
  await $.prompt.submit({ text, origin: { kind: 'task-notification' }, wait: false } as Parameters<Engine['prompt']['submit']>[0])
}

/**
 * Stands for the engine beneath the band, which draws nothing of its own
 * there (the surveys aside).
 */
function seatBand(on: On) {
  on('ui.render', { component: 'AbovePrompt' }, ($, e) => {
    const { Box } = $.ui.resolve(e)

    return <Box key="engine" />
  })
}

/**
 * Seats the engine beneath the plugin: a clock, turns, a model whose steps
 * yield `script[index]`, and tools that answer `toolAnswer`. `seen` keeps
 * what the box said at each `look()` a step or a tool takes.
 */
function seat(on: On, script: readonly (readonly TurnStepChunk[])[]) {
  const box = {
    read: async () => '',
    seen: [] as string[],
    toolAnswer: { deny: 'stubbed in the test' } as { deny: string },
    onTool: async () => {},
  }

  const clock = mock.clock(on, { now: 1_000_000 })
  seatBand(on)

  const look = async () => {
    box.seen.push(await box.read())
  }

  on('turn.start', ($, e) => ({ turnId: e.turnId }))
  on('turn.complete', ($, e) => ({ text: e.answer }))
  on('classic.PermissionRequest', () => ({}))
  on('prompt.submit', ($, e) => ({ text: e.text }))

  on('turn.step', async function* ($, e) {
    for (const chunk of script[e.index] ?? []) {
      yield chunk
      await clock.advance(300)
      await look()
    }

    return {
      turnId: e.turnId,
      index: e.index,
      answer: '',
      toolUses: [],
      stopReason: e.index + 1 < script.length ? 'tool_use' : 'end_turn',
      usage: null,
    } as const
  })

  on('tool.call', async () => {
    await box.onTool()
    await look()

    return box.toolAnswer
  })

  return { box, clock }
}

async function mount($: Engine, box: { read: () => Promise<string> }) {
  const ui = await $.ui.mount(BAND)

  box.read = async () => (await ui.find({ key: 'activity' }))?.text ?? ''

  return ui
}

async function step($: Engine, index: number) {
  for await (const _ of $.turn.step({ ...STEP, index })) {
    // drained: the beneath hook looks at the box between chunks
  }
}

describe('register', () => {
  test('the box says idle before any turn, its triangle closed', async ($, on) => {
    mock.clock(on)
    seatBand(on)

    const ui = await $.ui.mount(BAND)
    const text = (await ui.find({ key: 'activity' }))?.text ?? ''

    expect(text).toContain('대기')
    expect(text).toContain('▸')
  })

  test('another plugin drawing the band keeps its box, under this one', async ($, on) => {
    mock.clock(on)

    on('ui.render', { component: 'AbovePrompt' }, ($, e) => {
      const { Box, Text } = $.ui.resolve(e)

      return (
        <Box key="beneath">
          <Text>2 tasks for you</Text>
        </Box>
      )
    })

    const ui = await $.ui.mount(BAND)

    expect((await ui.find({ key: 'activity' }))?.text ?? '').toContain('대기')
    expect((await ui.find({ key: 'beneath' }))?.text ?? '').toContain('2 tasks for you')
  })

  test('a turn reads, thinks, prepares, runs, reviews, writes and ends', async ($, on) => {
    const { box } = seat(on, [
      [
        { kind: 'thinking', index: 0, text: 'The config looks off. I should check the loader first' },
        { kind: 'tool', index: 1, id: 'tu1', name: 'Read' },
        { kind: 'input', index: 1, json: '{"file_path": "src/app.ts"' },
      ],
      [{ kind: 'text', index: 0, text: 'All fixed now, the loader reads it.' }],
    ])

    await mount($, box)

    const { turnId } = await $.turn.start({ text: 'fix the config', turnId: 't1' })

    expect(await box.read()).toContain('프롬프트 읽는 중')

    await step($, 0)

    expect(box.seen[0]).toContain('생각 중: I should check the loader first')
    expect(box.seen[1]).toContain('프롬프트 읽는 중')
    expect(box.seen[2]).toContain('app.ts 읽기')
    expect(box.seen.join(' ')).not.toContain('Preparing')

    await $.tool.call({ tool: 'Read', file_path: 'src/app.ts' })

    expect(box.seen[3]).toContain('app.ts 읽기')
    expect(await box.read()).toContain('동작 1개 결과 검토 중')

    await step($, 1)

    expect(box.seen[4]).toContain('답변 작성 중 · 7단어')

    await $.turn.complete({ ...DONE, turnId })

    expect(await box.read()).toContain('대기 · 지난 턴')
  })

  test('the triangle opens the history of the turn', async ($, on) => {
    const { box } = seat(on, [
      [{ kind: 'thinking', index: 0, text: 'Look at the file.' }],
      [{ kind: 'text', index: 0, text: 'Done.' }],
    ])

    const ui = await mount($, box)

    const { turnId } = await $.turn.start({ text: 'fix the config', turnId: 't1' })

    await step($, 0)
    await $.tool.call({ tool: 'Read', file_path: 'src/app.ts' })
    await step($, 1)
    await $.turn.complete({ ...DONE, turnId })

    expect(await ui.find({ key: 'history' })).toBeUndefined()

    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(history).toContain('fix the config')
    expect(history).toContain('생각 (4단어)')
    expect(history).toContain('⊘ app.ts 읽기')
    expect(history).toContain('답변 작성 (1단어)')
    expect(history).toContain('만에 완료')
    expect((await ui.find({ key: 'activity' }))?.text).toContain('▾')
  })

  test('thinking with no thought text keeps the last action', async ($, on) => {
    const { box } = seat(on, [
      [{ kind: 'thinking', index: 0, text: '' }],
      [{ kind: 'thinking', index: 0, text: '' }],
    ])

    await mount($, box)
    await $.turn.start({ text: 'fix the config', turnId: 't1' })
    await step($, 0)
    await $.tool.call({ tool: 'Read', file_path: 'src/app.ts' })
    await step($, 1)

    expect(box.seen[0]).toContain('프롬프트 읽는 중')
    expect(box.seen[2]).toContain('app.ts 읽기')
    expect(box.seen.join(' ')).not.toContain('생각 중')
  })

  test('a thought whose one chunk comes as it ends is timed from the quiet before it', async ($, on) => {
    const clock = mock.clock(on, { now: 1_000_000 })
    seatBand(on)

    on('turn.start', ($, e) => ({ turnId: e.turnId }))
    on('turn.complete', ($, e) => ({ text: e.answer }))

    on('turn.step', async function* ($, e) {
      await clock.advance(5000)
      yield { kind: 'thinking', index: 0, text: '' } as const
      yield { kind: 'text', index: 1, text: 'Done.' } as const
      await clock.advance(300)

      return {
        turnId: e.turnId,
        index: e.index,
        answer: 'Done.',
        toolUses: [],
        stopReason: 'end_turn',
        usage: null,
      } as const
    })

    const ui = await $.ui.mount(BAND)
    const { turnId } = await $.turn.start({ text: 'fix the config', turnId: 't1' })

    await step($, 0)
    await $.turn.complete({ ...DONE, turnId })
    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(history).toMatch(/생각\s+5\.0초/)
    expect(history).not.toMatch(/생각\s+0\.0초/)
  })

  test('the review of a result is timed from the result, not the step before the call', async ($, on) => {
    const { box, clock } = seat(on, [
      [
        { kind: 'tool', index: 0, id: 'tu1', name: 'Read' },
        { kind: 'input', index: 0, json: '{"file_path": "src/app.ts"' },
      ],
      [{ kind: 'text', index: 0, text: 'Done.' }],
    ])

    box.onTool = async () => {
      await clock.advance(5000)
    }

    await mount($, box)
    await $.turn.start({ text: 'fix the config', turnId: 't1' })
    await step($, 0)
    await $.tool.call({ tool: 'Read', file_path: 'src/app.ts' })

    const review = await box.read()

    expect(review).toContain('동작 1개 결과 검토 중')
    expect(review).toContain('0.0초')
  })

  test('a command being written shows its description, never the command', async ($, on) => {
    const { box } = seat(on, [
      [
        { kind: 'tool', index: 0, id: 'tu1', name: 'Bash' },
        { kind: 'input', index: 0, json: '{"command": "npm test --silent"' },
        { kind: 'input', index: 0, json: ', "description": "Run the tests"' },
      ],
    ])

    await mount($, box)
    await $.turn.start({ text: 'test it', turnId: 't1' })
    await step($, 0)

    expect(box.seen[1]).toContain('프롬프트 읽는 중')
    expect(box.seen[2]).toContain('Run the tests')
    expect(box.seen.join(' ')).not.toContain('npm test')
  })

  test('control characters in a label never take the box down', async ($, on) => {
    const { box } = seat(on, [])
    const ui = await mount($, box)
    const esc = String.fromCharCode(27)
    const bel = String.fromCharCode(7)
    const rlo = String.fromCharCode(0x202e)

    await $.turn.start({ text: 'x', turnId: 't1' })
    await $.tool.call({ tool: 'Read', file_path: `src/${esc}]0;title${bel}${esc}[31m${rlo}red.ts` })
    await $.tool.call({ tool: 'Read', file_path: `src/${'x'.repeat(200000)}.ts` })
    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(box.seen[0]).toContain('읽기')
    expect(box.seen[0]).toContain('red.ts')
    expect(box.seen[0]).not.toContain(esc)
    expect(box.seen[0]).not.toContain(rlo)
    expect(box.seen[1]?.length ?? 0).toBeLessThan(400)
    expect(history).toContain('red.ts')
  })

  test('a long path is named by its whole file name, never the start of a folder', async ($, on) => {
    const { box } = seat(on, [])

    await mount($, box)
    await $.turn.start({ text: 'x', turnId: 't1' })
    await $.tool.call({ tool: 'Read', file_path: `/Users/someone/${'nested/'.repeat(10)}summary.ts` })

    expect(box.seen[0]).toContain('summary.ts 읽기')
  })

  test('a call waiting on a permission prompt says so', async ($, on) => {
    const { box } = seat(on, [])

    box.onTool = async () => {
      await $.classic.PermissionRequest({ tool_name: 'Bash', tool_input: { command: 'npm test' } })
    }

    await mount($, box)
    await $.turn.start({ text: 'test it', turnId: 't1' })
    await $.tool.call({ tool: 'Bash', command: 'npm test', description: 'Run the tests' })

    expect(box.seen[0]).toContain('승인 대기: Run the tests')
  })

  test('agents still at work after the turn keep the box busy', async ($, on) => {
    const { box, clock } = seat(on, [])

    on('agent.spawn', ($, e) => ({ model: 'claude-opus-5-5', agentId: e.tool_use_id === 'tu1' ? 'a1' : 'a2' }))

    const ui = await mount($, box)
    const { turnId } = await $.turn.start({ text: 'launch the panel', turnId: 't1' })

    await $.agent.spawn({ ...SPAWN, tool_use_id: 'tu1', description: 'Draft the plan' })
    await $.agent.spawn({ ...SPAWN, tool_use_id: 'tu2', description: 'Storyboard the video' })
    await $.turn.complete({ ...DONE, turnId })
    // A call made inside a subagent's loop, which carries its id.
    await $.tool.call({ tool: 'Read', file_path: 'README.md', agentId: 'a1' } as Parameters<Engine['tool']['call']>[0])
    await clock.advance(300)

    const team = (await ui.find({ key: 'agents' }))?.text ?? ''

    expect(await box.read()).toContain('에이전트 2개 작업 중')
    expect(team).toContain('Draft the plan › README.md 읽기')
    expect(team).toContain('Storyboard the video › 시작 준비 중')

    await $.turn.complete({ ...DONE, turnId: 't2', agentId: 'a2' })

    expect(await box.read()).toContain('Draft the plan › README.md 읽기')
    expect(await ui.find({ key: 'agents' })).toBeUndefined()

    await $.turn.complete({ ...DONE, turnId: 't3', agentId: 'a1', reason: 'error' })
    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(await box.read()).toContain('대기')
    expect(history).toContain('✓ 에이전트: Storyboard the video')
    expect(history).toContain('✗ 에이전트: Draft the plan')
  })

  test('a named agent is shown by its Agent call description, never its prompt', async ($, on) => {
    const { box, clock } = seat(on, [])

    on('agent.list', () => ({
      value: [{
        id: 'areviewer-1f2e',
        description: 'You are reviewing the diff. Read every file and',
        name: 'reviewer',
        type: 'general-purpose',
        status: 'running',
      }],
    }))

    const ui = await mount($, box)

    await $.tool.call({ tool: 'Read', file_path: 'src/app.ts', agentId: 'areviewer-1f2e' } as Parameters<Engine['tool']['call']>[0])
    await clock.advance(1000)

    // Before its Agent call is seen, the engine's handle for it stands.
    expect(await box.read()).toContain('reviewer › app.ts 읽기')

    box.toolAnswer = { result: { status: 'teammate_spawned' } } as never
    await $.tool.call({
      tool: 'Agent',
      name: 'reviewer',
      description: 'Review the diff',
      prompt: 'You are reviewing the diff. Read every file and…',
    } as Parameters<Engine['tool']['call']>[0])
    await clock.advance(1000)

    expect(await box.read()).toContain('Review the diff › app.ts 읽기')
    expect(await box.read()).not.toContain('You are reviewing')
    expect(await ui.find({ key: 'agents' })).toBeUndefined()
  })

  test('a background agent is named from the Agent call that launched it', async ($, on) => {
    const { box, clock } = seat(on, [])

    await mount($, box)

    box.toolAnswer = {
      result: { status: 'async_launched', agentId: 'b1', description: 'This is a long prompt. Read it all.' },
    } as never

    await $.tool.call({ tool: 'Agent', description: 'Tour the source', prompt: 'Read it.' } as Parameters<Engine['tool']['call']>[0])

    expect(await box.read()).toContain('Tour the source › 시작 준비 중')

    await $.tool.call({ tool: 'Read', file_path: 'src/app.ts', agentId: 'b1' } as Parameters<Engine['tool']['call']>[0])
    await clock.advance(300)

    expect(await box.read()).toContain('Tour the source › app.ts 읽기')
  })

  test('a background shell keeps the box waiting after the turn, until its notice', async ($, on) => {
    const { box } = seat(on, [])
    const ui = await mount($, box)
    const { turnId } = await $.turn.start({ text: 'run the tests', turnId: 't1' })

    box.toolAnswer = { result: { stdout: '', stderr: '', interrupted: false, backgroundTaskId: 'b1' } } as never
    await $.tool.call({ tool: 'Bash', command: 'sleep 100', description: 'Run the device tests', run_in_background: true } as ToolArgs)
    await $.turn.complete({ ...DONE, turnId })

    expect(await box.read()).toContain('백그라운드 1개 완료 대기 · 지난 턴')
    expect((await ui.find({ key: 'background' }))?.text ?? '').toContain('셸 › Run the device tests')

    const notice = shellNotice('b1', 'completed', 'Background command "Run the device tests" completed (exit code 0)')

    await notify($, notice)
    await $.turn.start({ text: notice, turnId: 't2' })

    expect(await box.read()).toContain('셸 완료 알림 읽는 중')
    expect(await ui.find({ key: 'background' })).toBeUndefined()

    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(history).toContain('✓ 셸: Run the device tests')
    expect(history.split('셸: Run the device tests').length).toBe(2)
    expect(history).not.toContain('task-notification')
  })

  test('a notice whose turn raised no prompt hook is taken at the turn, its exit code shown', async ($, on) => {
    const { box } = seat(on, [])
    const ui = await mount($, box)

    box.toolAnswer = { result: { stdout: '', stderr: '', interrupted: false, backgroundTaskId: 'b1' } } as never
    await $.tool.call({ tool: 'Bash', command: 'gradle test', description: 'Build and test', run_in_background: true } as ToolArgs)
    await $.turn.start({
      text: shellNotice('b1', 'failed', 'Background command "Build and test" failed with exit code 1'),
      turnId: 't2',
    })

    expect(await box.read()).toContain('셸 실패 알림 읽는 중')

    await ui.press({ key: 'toggle' })

    expect((await ui.find({ key: 'history' }))?.text ?? '').toContain('✗ 셸: Build and test (종료 코드 1)')
  })

  test('a monitor counts its events, and its deadline lets it go when no notice came', async ($, on) => {
    const { box, clock } = seat(on, [])
    const ui = await mount($, box)
    const { turnId } = await $.turn.start({ text: 'watch the run', turnId: 't1' })

    box.toolAnswer = { result: { taskId: 'm1', timeoutMs: 60_000 } } as never
    await $.tool.call({ tool: 'Monitor', command: 'tail -f run.log', description: 'Watch the run', timeout_ms: 60_000 } as ToolArgs)
    await $.turn.complete({ ...DONE, turnId })

    expect((await ui.find({ key: 'background' }))?.text ?? '').toContain('모니터 › Watch the run · 이벤트 없음')

    await notify($, monitorEvent('m1', 'Watch the run', 'RUN test_01'))
    await clock.advance(2000)
    await notify($, monitorEvent('m1', 'Watch the run', 'OK test_01'))

    expect((await ui.find({ key: 'background' }))?.text ?? '').toContain('이벤트 2개, 마지막')

    await ui.press({ key: 'toggle' })

    expect((await ui.find({ key: 'history' }))?.text ?? '').toContain('⇠ 모니터 이벤트 2개: OK test_01')

    await clock.advance(80_000)
    await clock.advance(1000)

    expect(await ui.find({ key: 'background' })).toBeUndefined()
    expect((await ui.find({ key: 'history' }))?.text ?? '').toContain('■ 모니터: Watch the run (기한 만료)')
    expect(await box.read()).not.toContain('백그라운드')
  })

  test('a TaskStop lets the shell go, which sends no notice', async ($, on) => {
    const { box } = seat(on, [])
    const ui = await mount($, box)

    box.toolAnswer = { result: { stdout: '', stderr: '', interrupted: false, backgroundTaskId: 'b1' } } as never
    await $.tool.call({ tool: 'Bash', command: 'until false; do sleep 5; done', description: 'Wait for the file', run_in_background: true } as ToolArgs)

    box.toolAnswer = { result: { message: 'Successfully stopped task: b1', task_id: 'b1', task_type: 'local_bash' } } as never
    await $.tool.call({ tool: 'TaskStop', task_id: 'b1' } as ToolArgs)

    expect(await ui.find({ key: 'background' })).toBeUndefined()

    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(history).toContain('셸 중지: Wait for the file')
    expect(history).toContain('■ 셸: Wait for the file (중지)')
  })

  test('a notice of no task is a row of its own, never markup', async ($, on) => {
    const { box } = seat(on, [])
    const ui = await mount($, box)
    const text = '<task-notification>\nsome-plugin: its hook was skipped.\n</task-notification>'

    await notify($, text)
    await $.turn.start({ text, turnId: 't1' })

    expect(await box.read()).toContain('알림 읽는 중')

    await ui.press({ key: 'toggle' })

    const history = (await ui.find({ key: 'history' }))?.text ?? ''

    expect(history).toContain('알림: some-plugin: its hook was skipped.')
    expect(history).not.toContain('task-notification')
  })
})

const USAGE = { input_tokens: 0, output_tokens: 0, cache_read_input_tokens: 0, cache_creation_input_tokens: 0 } as const

type Message = {
  role: 'user' | 'assistant'
  text: string
  toolUses: { tool_use_id: string; tool: string; input: Record<string, unknown>; text?: string; isError?: true }[]
}

const ASK: Message = { role: 'user', text: 'fix the config', toolUses: [] }

const WORK: Message = {
  role: 'assistant',
  text: 'Fixed the config.',
  toolUses: [{ tool_use_id: 'toolu_a', tool: 'Edit', input: { file_path: 'config.ts' }, text: 'ok' }],
}

/** An assistant message that used a tool, under its own tool_use id. */
function worked(text: string, id: string): Message {
  return { role: 'assistant', text, toolUses: [{ tool_use_id: id, tool: 'Edit', input: { file_path: 'config.ts' } }] }
}

function reply(goal: string, status: string, next: string): string {
  return [`목표: ${goal}`, `상황: ${status}`, `다음: ${next}`, '메모:', `- ${goal}`].join('\n')
}

/**
 * Seats what the catch-up reads beneath the plugin: the session's id, its
 * messages, a terminal drawing it, a git that answers, and a model whose
 * replies come from `replies` in turn (the last one again once they run out).
 * `prompts` keeps what each model call was asked.
 */
function seatCatchUp(on: On, store: Readonly<Record<string, unknown>> = {}) {
  const world = {
    sessionId: 's1',
    messages: [ASK, WORK] as Message[],
    replies: [{ isAnswered: true, text: reply('설정 파일 오류 수정', '수정 완료, 커밋 전', '커밋 여부 결정'), usage: USAGE }] as unknown[],
    prompts: [] as string[],
  }

  mock.store(on, store)

  on('session.id', () => ({ value: world.sessionId }))
  on('session.surfaces', () => ({ value: ['terminal'] as const }))
  on('session.messages', () => ({ value: world.messages }) as never)
  on('process.run', () => ({
    value: { exitCode: 0, stdout: ' M config.ts', stderr: '', isStdoutTruncated: false, isStderrTruncated: false },
  }))
  on('model.complete', ($, e) => {
    world.prompts.push(e.prompt)

    const answer = world.replies.length > 1 ? world.replies.shift() : world.replies[0]

    return { value: answer } as never
  })

  return world
}

/**
 * Runs a turn to its end, then lets the catch-up its end started run out: the
 * turn does not wait on it, so the test moves the clock until it settles.
 */
async function turn($: Engine, clock: MockClock, turnId: string) {
  await $.turn.start({ text: 'go on', turnId })
  await step($, 0)
  await $.turn.complete({ ...DONE, turnId })

  for (let i = 0; i < 20; i++) {
    await clock.advance(0)
  }
}

describe('catch-up', () => {
  test('a closed box asks no model, and opening it draws the three lines', async ($, on) => {
    const { box, clock } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on)
    const ui = await mount($, box)

    await turn($, clock, 't1')

    expect(world.prompts).toHaveLength(0)
    expect(await ui.find({ key: 'summary' })).toBeUndefined()

    await ui.press({ key: 'toggle' })

    const text = (await ui.find({ key: 'summary' }))?.text ?? ''

    expect(world.prompts).toHaveLength(1)
    expect(world.prompts[0]).toContain('fix the config')
    expect(world.prompts[0]).toContain('M config.ts')
    expect(text).toContain('목표 설정 파일 오류 수정')
    expect(text).toContain('상황 수정 완료, 커밋 전')
    expect(text).toContain('다음 커밋 여부 결정')
    expect(text).toContain('기준')

    await ui.press({ key: 'toggle' })
    world.messages.push(worked('More work.', 'toolu_b'))
    await turn($, clock, 't2')

    expect(world.prompts).toHaveLength(1)
  })

  test('while open, a turn that used a tool folds in only what is new; talk alone waits', async ($, on) => {
    const { box, clock } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on)
    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })

    world.messages.push({ role: 'user', text: 'why did it break', toolUses: [] }, { role: 'assistant', text: 'A typo.', toolUses: [] })
    await turn($, clock, 't1')

    expect(world.prompts).toHaveLength(1)

    world.replies = [{ isAnswered: true, text: reply('설정 파일 오류 수정', '반영 완료', '없음'), usage: USAGE }]
    world.messages.push({ role: 'user', text: 'apply it', toolUses: [] }, worked('Applied.', 'toolu_c'))
    await turn($, clock, 't2')

    expect(world.prompts).toHaveLength(2)
    expect(world.prompts[1]).toContain('상황: 수정 완료, 커밋 전')
    expect(world.prompts[1]).toContain('why did it break')
    expect(world.prompts[1]).toContain('apply it')
    expect(world.prompts[1]).not.toContain('fix the config')
    expect((await ui.find({ key: 'summary' }))?.text).toContain('상황 반영 완료')
  })

  test('a failed update keeps the last lines and says why', async ($, on) => {
    const { box, clock } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on)
    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })

    world.replies = [{ isAnswered: false, reason: 'api-error', status: 529, error: 'overloaded', usage: USAGE }]
    world.messages.push(worked('More work.', 'toolu_b'))
    await turn($, clock, 't1')

    const text = (await ui.find({ key: 'summary' }))?.text ?? ''

    expect(text).toContain('상황 수정 완료, 커밋 전')
    expect(text).toContain('요약 갱신 실패: API 오류 (529, overloaded)')
  })

  test('a process moved to another session reads that session’s catch-up, never the last one', async ($, on) => {
    const { box, clock } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on, {
      'summary:s2': {
        lines: { goal: '푸시 알림 문구 정리', status: '초안 작성', next: '검토 요청' },
        notes: '',
        covered: 0,
        marker: '',
        updatedAtMs: 0,
      },
    })
    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })

    world.sessionId = 's2'
    world.messages = [{ role: 'user', text: 'tidy the push copy', toolUses: [] }, WORK]
    world.replies = [{ isAnswered: true, text: reply('푸시 알림 문구 정리', '문구 확정', '반영 요청'), usage: USAGE }]
    await turn($, clock, 't1')

    expect(world.prompts).toHaveLength(2)
    expect(world.prompts[1]).toContain('목표: 푸시 알림 문구 정리')
    expect(world.prompts[1]).not.toContain('설정 파일 오류 수정')
    expect(world.prompts[1]).toContain('tidy the push copy')
    expect((await ui.find({ key: 'summary' }))?.text).toContain('상황 문구 확정')
  })

  test('a /clear takes the last session’s lines down until the new one has its own', async ($, on) => {
    const { box } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])

    seatCatchUp(on)
    on('session.end', ($, e) => ({ sessionId: e.sessionId }))

    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })

    expect((await ui.find({ key: 'summary' }))?.text).toContain('목표 설정 파일 오류 수정')

    await $.session.end({ reason: 'clear', sessionId: 's1', resume: { id: 's1' } })

    const text = (await ui.find({ key: 'summary' }))?.text ?? ''

    expect(text).toContain('아직 요약 없음')
    expect(text).not.toContain('설정 파일 오류 수정')
  })

  test('a long session’s first catch-up reads its opening asks and latest messages, in one call', async ($, on) => {
    const { box } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on)
    const filler = 'x'.repeat(1000)

    world.messages = [
      { role: 'user', text: 'fix the config', toolUses: [] },
      { role: 'assistant', text: 'Looking.', toolUses: [] },
      { role: 'user', text: 'and the tests too', toolUses: [] },
      ...Array.from({ length: 200 }, (_, i) => worked(`step-${i} ${filler}`, `toolu_${i}`)),
    ]

    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })

    expect(world.prompts).toHaveLength(1)
    expect(world.prompts[0]).toContain('fix the config')
    expect(world.prompts[0]).toContain('and the tests too')
    expect(world.prompts[0]).toContain('step-199')
    expect(world.prompts[0]).not.toContain('step-50 ')
    expect(world.prompts[0]).toMatch(/세션 중간 메시지 \d+개 생략/)
    expect(world.prompts[0]!.length).toBeLessThan(40_000)
  })

  test('a box opened after a long stretch closed reads only the latest of what it missed, in one call', async ($, on) => {
    const { box } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on)
    const filler = 'x'.repeat(1000)
    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })
    await ui.press({ key: 'toggle' })

    world.messages.push(...Array.from({ length: 200 }, (_, i) => worked(`step-${i} ${filler}`, `toolu_${i}`)))

    await ui.press({ key: 'toggle' })

    expect(world.prompts).toHaveLength(2)
    expect(world.prompts[1]).toContain('목표: 설정 파일 오류 수정')
    expect(world.prompts[1]).toContain('step-199')
    expect(world.prompts[1]).not.toContain('step-50 ')
    expect(world.prompts[1]).not.toContain('fix the config')
    expect(world.prompts[1]).toMatch(/지난 요약 뒤 메시지 \d+개 생략/)
    expect(world.prompts[1]!.length).toBeLessThan(40_000)
  })

  test('tool results never go to the model, and a failed call is marked', async ($, on) => {
    const { box } = seat(on, [[{ kind: 'text', index: 0, text: 'Done.' }]])
    const world = seatCatchUp(on)

    world.messages = [
      ASK,
      {
        role: 'assistant',
        text: 'Ran the tests.',
        toolUses: [
          { tool_use_id: 'toolu_r', tool: 'Read', input: { file_path: 'config.ts' }, text: 'whole-file-body' },
          { tool_use_id: 'toolu_t', tool: 'Bash', input: { command: './gradlew test' }, text: 'BUILD FAILED', isError: true },
        ],
      } as Message,
    ]

    const ui = await mount($, box)

    await ui.press({ key: 'toggle' })

    expect(world.prompts[0]).toContain('[tool Read] {"file_path":"config.ts"}')
    expect(world.prompts[0]).toContain('[tool Bash] {"command":"./gradlew test"} (오류)')
    expect(world.prompts[0]).not.toContain('whole-file-body')
    expect(world.prompts[0]).not.toContain('BUILD FAILED')
  })
})
