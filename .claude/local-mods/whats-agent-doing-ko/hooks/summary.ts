import type { SessionMessage } from 'claude-code'

import type { SummaryLines } from '../types'
import { clip } from './text'

/**
 * The model that writes the catch-up: one call per update, while the box is
 * open.
 */
export const SUMMARY_MODEL = 'haiku'

/** How many sessions' catch-ups the store keeps, the most recent first. */
export const KEEP_SESSIONS = 50

/** The notes carried between updates: the model's only memory of earlier messages. */
const MAX_NOTES_CHARS = 2000

/**
 * Messages are cut short except the newest few, which hold where things
 * stand. Tool calls go as their tool and the head of their input, a failed
 * one marked: their results are left out, since the git state shows what
 * landed on disk, and in a long session's transcript measured on 2026-11-05
 * (431 messages) the results were half of what a first read would send.
 */
const MAX_MESSAGE_CHARS = 1500
const MAX_RECENT_MESSAGE_CHARS = 4000
const RECENT_MESSAGES = 6
const MAX_TOOL_INPUT_CHARS = 120
const MAX_GIT_CHARS = 1500

/**
 * One update reads at most the latest `TAIL_CHARS` of what it has not seen,
 * the rest left out: the notes carry what came before, and a first catch-up
 * adds the session's opening asks (its goal). A turn's own messages are far
 * under it; a box opened after a long stretch closed reaches it.
 */
const FIRST_ASKS = 3
const TAIL_CHARS = 30_000

/**
 * A session's catch-up as the store keeps it: the three lines drawn, the
 * notes the next update reads, and where in the transcript it ends
 * (`covered` messages, the last of them fingerprinted as `marker`).
 */
export type SavedSummary = {
  lines: SummaryLines | null
  notes: string
  covered: number
  marker: string
  updatedAtMs: number | null
}

export const NO_SAVED_SUMMARY: SavedSummary = {
  lines: null,
  notes: '',
  covered: 0,
  marker: '',
  updatedAtMs: null,
}

export const SUMMARY_SYSTEM = [
  'You keep a three-line catch-up of a Claude Code session for someone who steps away and comes back. It is drawn in a narrow box above the prompt.',
  'You get your previous catch-up (its three lines and your running notes), the transcript messages added since it was written (long texts are clipped, tool calls are shown by their input alone and marked when they failed), and the git state of the working tree. Write the whole catch-up again: keep what is still true, rewrite what changed, drop what no longer matters. The notes are your only memory of earlier messages, so carry their facts forward unless the new messages change them.',
  'When there are too many new messages, only the latest are sent and the rest are left out, marked so: after your previous catch-up, or on a first catch-up between the session\'s opening requests and its latest messages. Do not guess what happened there.',
  [
    'What each line says:',
    '- 목표: what the person asked for in this session and the problem behind it; if the goal shifted, the goal as it stands now.',
    '- 상황: where the work stands now: finished, half done, broken, uncommitted, still running. Trust the git state over what the messages claim.',
    '- 다음: the obvious next step, or the question the person has to answer. If nothing is pending, say so.',
  ].join('\n'),
  [
    'How to write:',
    '- Korean. Each line at most 45 characters and ending in a noun form (e.g. "설계 확정, 구현 전", "커밋 대기"), never a sentence ending in -다 or -요.',
    '- Name things by what they are, never by shorthand the session invented (a bare "R5", "2안", "Gate 3"). Code identifiers, file names, commands and established tool names stay as written; other English terms are written in Korean.',
    '- No markdown and no quotes in the three lines.',
  ].join('\n'),
  [
    'Output exactly this, nothing before or after:',
    '목표: <line>',
    '상황: <line>',
    '다음: <line>',
    '메모:',
    '- <notes for your next update: facts, decisions, paths, open questions; at most 12 bullets of one line each>',
  ].join('\n'),
].join('\n\n')

/** A message's identity, to find again where the last update ended. */
export function fingerprint(message: SessionMessage): string {
  const ids = message.toolUses.map(use => use.tool_use_id).join(',')

  return `${message.role}\n${message.text.slice(0, 200)}\n${ids}`
}

/**
 * Where the messages not yet folded in begin: right after `covered` when the
 * message there is still the one summarized last, else after wherever that
 * message moved to (the list slides past the newest 4096 and compaction
 * rewrites it), else from the start.
 */
export function startOf(messages: readonly SessionMessage[], saved: SavedSummary): number {
  const { covered, marker } = saved

  if (marker === '') {
    return 0
  }

  const last = messages[covered - 1]

  if (covered <= messages.length && last !== undefined && fingerprint(last) === marker) {
    return covered
  }

  return messages.findLastIndex(message => fingerprint(message) === marker) + 1
}

/** Claude Code adds system reminders to user messages; they are not the person's words. */
function unremind(text: string): string {
  return text.replace(/<system-reminder>[\s\S]*?<\/system-reminder>/g, '').trim()
}

/** A message as the model reads it, cut shorter unless it is among the newest; '' when it says nothing. */
function formatMessage(messages: readonly SessionMessage[], index: number): string {
  const message = messages[index]

  if (message === undefined) {
    return ''
  }

  const lines: string[] = []
  const text = unremind(message.text)
  const max = index >= messages.length - RECENT_MESSAGES ? MAX_RECENT_MESSAGE_CHARS : MAX_MESSAGE_CHARS

  if (text !== '') {
    lines.push(`${message.role === 'user' ? 'USER' : 'ASSISTANT'}: ${clip(text, max)}`)
  }

  for (const use of message.toolUses) {
    const failed = use.isError === true ? ' (오류)' : ''

    lines.push(`  [tool ${use.tool}] ${clip(JSON.stringify(use.input), MAX_TOOL_INPUT_CHARS)}${failed}`)
  }

  return lines.length === 0 ? '' : `${lines.join('\n')}\n\n`
}

/**
 * What one update reads of the messages from `start`: all of them when they
 * fit `TAIL_CHARS`, else the latest that do, the ones before named as left
 * out; a first read (`withAsks`) also keeps the session's opening asks ahead
 * of that gap.
 */
export function readOf(messages: readonly SessionMessage[], start: number, withAsks: boolean): string {
  let tail = ''
  let tailStart = messages.length

  while (tailStart > start) {
    const formatted = formatMessage(messages, tailStart - 1)

    if (tail.length + formatted.length > TAIL_CHARS && tail !== '') {
      break
    }

    tail = formatted + tail
    tailStart -= 1
  }

  if (tailStart === start) {
    return tail
  }

  const asks = withAsks
    ? messages
        .map((message, index) => ({ message, index }))
        .filter(({ message, index }) => index >= start && index < tailStart && message.role === 'user' && unremind(message.text) !== '')
        .slice(0, FIRST_ASKS)
    : []

  const head = asks.map(({ index }) => formatMessage(messages, index)).join('')
  const skipped = tailStart - start - asks.length
  const where = withAsks ? '세션 중간' : '지난 요약 뒤'
  const gap = skipped > 0 ? `[${where} 메시지 ${skipped}개 생략]\n\n` : ''

  return `${head}${gap}${tail}`
}

/** The user message of one update. */
export function promptOf(saved: SavedSummary, messages: string, git: string): string {
  const previous =
    saved.lines === null
      ? '(none yet)'
      : [`목표: ${saved.lines.goal}`, `상황: ${saved.lines.status}`, `다음: ${saved.lines.next}`, '메모:', saved.notes].join('\n')

  return [
    `<previous_catch_up>\n${previous}\n</previous_catch_up>`,
    `<new_messages>\n${messages}</new_messages>`,
    `<git_state>\n${git}\n</git_state>`,
  ].join('\n\n')
}

/** `git status` and the latest commits, each clipped; a failure is a line of its own. */
export function gitTextOf(results: readonly { argv: readonly string[]; output: string }[]): string {
  return results.map(({ argv, output }) => `$ ${argv.join(' ')}\n${clip(output.trim() || '(empty)', MAX_GIT_CHARS)}`).join('\n\n')
}

/**
 * The three lines and the notes out of the model's reply, or null when a
 * line is missing.
 */
export function parseSummary(reply: string): { lines: SummaryLines; notes: string } | null {
  const lineOf = (label: string) => reply.match(new RegExp(`^\\s*${label}\\s*[:：]\\s*(.+)$`, 'm'))?.[1]?.trim()

  const goal = lineOf('목표')
  const status = lineOf('상황')
  const next = lineOf('다음')

  if (!goal || !status || !next) {
    return null
  }

  const notesAt = reply.search(/^\s*메모\s*[:：]/m)
  const notes = notesAt < 0 ? '' : reply.slice(notesAt).replace(/^\s*메모\s*[:：]\s*/, '').trim()

  return { lines: { goal, status, next }, notes: clip(notes, MAX_NOTES_CHARS) }
}

/** A stored catch-up read back, anything else (an older shape, nothing) as none. */
export function savedOf(value: unknown): SavedSummary {
  if (typeof value !== 'object' || value === null) {
    return NO_SAVED_SUMMARY
  }

  const record = value as Partial<SavedSummary>
  const lines = record.lines

  const isLines =
    typeof lines === 'object' &&
    lines !== null &&
    typeof lines.goal === 'string' &&
    typeof lines.status === 'string' &&
    typeof lines.next === 'string'

  return {
    lines: isLines ? lines : null,
    notes: typeof record.notes === 'string' ? record.notes : '',
    covered: typeof record.covered === 'number' ? record.covered : 0,
    marker: typeof record.marker === 'string' ? record.marker : '',
    updatedAtMs: typeof record.updatedAtMs === 'number' ? record.updatedAtMs : null,
  }
}

/** `14:05`, the time of day an update ended. */
export function clockOf(ms: number): string {
  const date = new Date(ms)

  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
