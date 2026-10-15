import { useEffect, useRef, useState } from 'react'

interface MarkdownToolbarProps {
  textareaRef: React.RefObject<HTMLTextAreaElement | null>
  onApply: (value: string, start: number, end: number) => void
}

const CALLOUT_TYPES = [
  { type: 'note', icon: '📝', label: 'Note' },
  { type: 'tip', icon: '💡', label: 'Tip' },
  { type: 'warning', icon: '⚠️', label: 'Warning' },
  { type: 'danger', icon: '🔴', label: 'Danger' },
  { type: 'info', icon: 'ℹ️', label: 'Info' },
] as const

export default function MarkdownToolbar({ textareaRef, onApply }: MarkdownToolbarProps) {
  const [calloutOpen, setCalloutOpen] = useState(false)
  const savedSelection = useRef({ start: 0, end: 0 })
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!calloutOpen) return
    function handleOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setCalloutOpen(false)
      }
    }
    document.addEventListener('mousedown', handleOutside)
    return () => document.removeEventListener('mousedown', handleOutside)
  }, [calloutOpen])

  function textarea(): HTMLTextAreaElement {
    const ta = textareaRef.current
    if (!ta) throw new Error('Textarea недоступна')
    return ta
  }

  function wrapSelection(before: string, after: string) {
    const ta = textarea()
    const { selectionStart: start, selectionEnd: end, value: text } = ta
    const selected = text.substring(start, end)
    if (selected) {
      onApply(
        text.substring(0, start) + before + selected + after + text.substring(end),
        start + before.length,
        end + before.length,
      )
    } else {
      onApply(
        text.substring(0, start) + before + 'текст' + after + text.substring(end),
        start + before.length,
        start + before.length + 5,
      )
    }
  }

  function insertBlock(prefix: string) {
    const ta = textarea()
    const { selectionStart: start, selectionEnd: end, value: text } = ta
    const lineStart = text.lastIndexOf('\n', start - 1) + 1
    let lineEnd = text.indexOf('\n', end)
    if (lineEnd === -1) lineEnd = text.length
    const selectedBlock = text.substring(lineStart, lineEnd)
    const modified = selectedBlock
      .split('\n')
      .map((line) => prefix + line)
      .join('\n')
    onApply(
      text.substring(0, lineStart) + modified + text.substring(lineEnd),
      lineStart,
      lineStart + modified.length,
    )
  }

  function insertTemplate(template: string) {
    const ta = textarea()
    const { selectionStart: start, value: text } = ta
    const before = text.substring(0, start)
    const needNewline = before.length > 0 && !before.endsWith('\n')
    const prefix = needNewline ? '\n' : ''
    const caret = start + prefix.length + template.length
    onApply(before + prefix + template + text.substring(start), caret, caret)
  }

  function toggleCalloutMenu() {
    const ta = textarea()
    savedSelection.current = { start: ta.selectionStart, end: ta.selectionEnd }
    setCalloutOpen((prev) => !prev)
  }

  function insertCallout(type: string, fold: '' | '-') {
    const ta = textarea()
    const { start, end } = savedSelection.current
    const text = ta.value
    const selected = text.substring(start, end)
    const content = selected || 'Текст'
    const lines = content
      .split('\n')
      .map((line) => '> ' + line)
      .join('\n')
    const before = text.substring(0, start)
    const after = text.substring(end)
    const needNewline = before.length > 0 && !before.endsWith('\n')
    const prefix = needNewline ? '\n' : ''
    const template = '> [!' + type + ']' + fold + ' Заголовок\n' + lines + '\n'
    const caret = before.length + prefix.length + template.length
    setCalloutOpen(false)
    onApply(before + prefix + template + after, caret, caret)
  }

  return (
    <div className="md-toolbar">
      <button type="button" onClick={() => wrapSelection('**', '**')} title="Жирный (**текст**)">
        <b>B</b>
      </button>
      <button type="button" onClick={() => wrapSelection('*', '*')} title="Курсив (*текст*)">
        <i>I</i>
      </button>
      <button type="button" onClick={() => wrapSelection('~~', '~~')} title="Зачёркнутый (~~текст~~)">
        <s>S</s>
      </button>
      <button
        type="button"
        onClick={() => wrapSelection('`', '`')}
        title="Инлайн-код (`код`)"
        className="md-toolbar-mono"
      >
        {'</>'}
      </button>
      <button
        type="button"
        onClick={() => insertTemplate('```java\n\n```\n')}
        title="Блок кода"
        className="md-toolbar-mono"
      >
        {'{}'}
      </button>

      <span className="md-toolbar-divider" />

      <button type="button" onClick={() => insertBlock('## ')} title="Заголовок H2">
        H2
      </button>
      <button type="button" onClick={() => insertBlock('### ')} title="Заголовок H3">
        H3
      </button>

      <span className="md-toolbar-divider" />

      <button type="button" onClick={() => insertBlock('- ')} title="Маркированный список">
        •
      </button>
      <button type="button" onClick={() => insertBlock('1. ')} title="Нумерованный список">
        1.
      </button>
      <button type="button" onClick={() => insertBlock('- [ ] ')} title="Чекбокс (задача)">
        ☐
      </button>
      <button type="button" onClick={() => insertBlock('> ')} title="Цитата">
        ❝
      </button>
      <button type="button" onClick={() => insertTemplate('---\n')} title="Разделитель">
        —
      </button>

      <span className="md-toolbar-divider" />

      <div ref={menuRef} className="relative inline-block">
        <button type="button" onClick={toggleCalloutMenu} title="Вставить коллаут">
          ☰ ▾
        </button>
        {calloutOpen && (
          <div className="callout-dropdown">
            <table className="callout-grid">
              <thead>
                <tr>
                  <th />
                  <th>Обычный</th>
                  <th>Сворач.</th>
                </tr>
              </thead>
              <tbody>
                {CALLOUT_TYPES.map(({ type, icon, label }) => (
                  <tr key={type}>
                    <td className="callout-grid-label">
                      {icon} {label}
                    </td>
                    <td className="callout-grid-cell" onClick={() => insertCallout(type, '')}>
                      ▪
                    </td>
                    <td className="callout-grid-cell" onClick={() => insertCallout(type, '-')}>
                      −
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="callout-dropdown-hint">
              <div className="callout-dropdown-hint-label">Результат:</div>
              <div className="callout-dropdown-preview">
                <div className="callout-dropdown-preview-title">💡 Подсказка</div>
                <div className="callout-dropdown-preview-text">Используйте Stream API</div>
              </div>
              <div className="callout-dropdown-hint-label">Синтаксис:</div>
              <div className="callout-dropdown-syntax">
                &gt; [!tip] Подсказка
                <br />
                &gt; Используйте Stream API
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
