import { clip, oneLine } from './text'

const MAX_DETAIL_CHARS = 60

/**
 * Says in a few words what a tool call is doing, for What's Agent Doing.
 *
 * @param tool the tool's name; a string, as the declared names are only the
 *   tools of the build that wrote the types (Grep and Glob are on some only)
 * @param args the call's arguments, whole or as far as they have streamed
 * @returns a short label such as `register.tsx 읽기`
 */
export function activityOf(tool: string, args: Record<string, unknown>): string {
  const whole = (key: string): string => {
    const value = args[key]

    return typeof value === 'string' ? oneLine(value) : ''
  }

  const arg = (key: string): string => clip(whole(key), MAX_DETAIL_CHARS)

  // The name is taken from the whole path before the cut: a path cut first
  // leaves only the start of the folder or name the cut fell in (`su…`).
  const file = (key: string): string => clip(baseName(whole(key)), MAX_DETAIL_CHARS)

  switch (tool) {
    case 'Bash':
    case 'PowerShell':
      return arg('description') || `명령 실행: ${arg('command')}`
    case 'Read':
      return `${file('file_path')} 읽기`
    case 'Edit':
      return `${file('file_path')} 수정`
    case 'Write':
      return `${file('file_path')} 작성`
    case 'NotebookEdit':
      return `${file('notebook_path')} 수정`
    case 'Grep':
      return `"${arg('pattern')}" 검색`
    case 'Glob':
      return `파일 찾기: ${arg('pattern')}`
    case 'WebFetch':
      return `${hostOf(arg('url'))} 가져오기`
    case 'WebSearch':
      return `웹 검색: "${arg('query')}"`
    case 'Agent':
    case 'Task':
      return `에이전트 실행: ${arg('description')}`
    case 'TodoWrite':
      return '할 일 목록 갱신'
    case 'AskUserQuestion':
      return '답변 대기'
    case 'Skill':
      return `${arg('skill')} 스킬 사용`
    case 'Monitor':
      return `모니터 시작: ${arg('description')}`
    case 'TaskStop':
      return `작업 중지: ${arg('task_id') || arg('shell_id')}`
  }

  if (tool.startsWith('mcp__')) {
    const [, server = '', name = ''] = tool.split('__')

    return `${server} 도구 사용: ${name}`
  }

  return `${tool} 사용`
}

/**
 * The string arguments a tool call's input has streamed so far, read from
 * its partial JSON: enough to name the file or command before it is whole.
 */
export function partialArgsOf(json: string): Record<string, unknown> {
  const args: Record<string, unknown> = {}

  for (const match of json.matchAll(/"([a-z_]+)"\s*:\s*"((?:[^"\\]|\\.)*)/g)) {
    const [, key = '', raw = ''] = match

    args[key] ??= unescaped(raw)
  }

  return args
}

function unescaped(raw: string): string {
  try {
    return JSON.parse(`"${raw}"`) as string
  } catch {
    return raw
  }
}

function baseName(path: string): string {
  return path.split(/[\\/]/).pop() || path
}

function hostOf(url: string): string {
  try {
    return new URL(url).host
  } catch {
    return url
  }
}
