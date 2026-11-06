import type { ActivityOutcome } from '../types'
import { clip, oneLine } from './text'

const MAX_NAME_CHARS = 60

/**
 * One `<task-notification>` block the engine delivers as a prompt when a
 * background task reports: a shell's or a monitor's end, a monitor's event
 * (no `status`), an agent's stop, or an engine notice with no task.
 */
export type Notice = {
  taskId: string | null
  status: string | null
  kind: 'shell' | 'monitor' | 'agent' | 'other'
  name: string | null
  event: string | null
  exitCode: number | null
  /** The block's words without its tags, for a notice that names no task. */
  body: string
}

/**
 * The notification blocks a prompt holds, in order; none for a prompt the
 * person typed. Several may arrive in one prompt when they queued together.
 */
export function noticesIn(text: string): Notice[] {
  return (text.match(/<task-notification>[\s\S]*?<\/task-notification>/g) ?? []).map(noticeOf)
}

function noticeOf(block: string): Notice {
  const summary = tagOf(block, 'summary') ?? ''
  const event = tagOf(block, 'event')
  const quoted = summary.match(/"([\s\S]*)"/)?.[1] ?? null
  const exit = summary.match(/exit code (\d+)/)?.[1]

  // The engine's summary is the one place a notice says what kind of task it
  // reports on; a block with an event and no summary is still a monitor's.
  const kind: Notice['kind'] = summary.startsWith('Background command')
    ? 'shell'
    : summary.startsWith('Monitor') || (summary === '' && event !== null)
      ? 'monitor'
      : summary.startsWith('Agent')
        ? 'agent'
        : 'other'

  return {
    taskId: tagOf(block, 'task-id'),
    status: tagOf(block, 'status'),
    kind,
    name: quoted === null ? null : clip(oneLine(quoted), MAX_NAME_CHARS),
    event: event === null ? null : oneLine(event),
    exitCode: exit === undefined ? null : Number(exit),
    body: oneLine(decoded(block.replace(/<[^>]+>/g, ' '))),
  }
}

function tagOf(block: string, name: string): string | null {
  const open = `<${name}>`
  const start = block.indexOf(open)

  if (start < 0) {
    return null
  }

  const end = block.indexOf(`</${name}>`, start)

  return end < 0 ? null : decoded(block.slice(start + open.length, end).trim())
}

function decoded(text: string): string {
  return text
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&amp;/g, '&')
}

/** What the box says while Claude reads the notices that started a turn. */
export function topicOf(notices: readonly Notice[]): string {
  if (notices.length > 1) {
    return `알림 ${notices.length}개 읽는 중`
  }

  const [notice] = notices

  if (notice === undefined) {
    return '알림 읽는 중'
  }

  switch (notice.kind) {
    case 'shell':
      return notice.status === 'failed'
        ? '셸 실패 알림 읽는 중'
        : isStopped(notice.status)
          ? '셸 중지 알림 읽는 중'
          : '셸 완료 알림 읽는 중'
    case 'monitor':
      return notice.status === null ? '모니터 이벤트 읽는 중' : '모니터 종료 알림 읽는 중'
    case 'agent':
      return '에이전트 완료 알림 읽는 중'
    case 'other':
      return '알림 읽는 중'
  }
}

export function outcomeOfStatus(status: string | null): ActivityOutcome {
  return status === 'failed' ? 'error' : isStopped(status) ? 'interrupted' : 'ok'
}

/** What follows a finished task's name in its history row, if anything. */
export function endingOf(notice: Notice): string {
  if (notice.status === 'failed') {
    return notice.exitCode === null ? ' (실패)' : ` (종료 코드 ${notice.exitCode})`
  }

  return isStopped(notice.status) ? ' (중지)' : ''
}

function isStopped(status: string | null): boolean {
  return status === 'killed' || status === 'stopped'
}
