import { useEffect, useRef } from 'react'
import * as Prism from 'prismjs'
import 'prismjs/themes/prism-tomorrow.css'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-sql'
import 'prismjs/components/prism-bash'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-yaml'
import 'prismjs/components/prism-json'
import 'prismjs/components/prism-xml-doc'
import 'prismjs/components/prism-properties'

interface NoteContentProps {
  html: string
}

export default function NoteContent({ html }: NoteContentProps) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const root = ref.current
    if (!root) return

    root.querySelectorAll('pre').forEach((pre) => {
      if (pre.querySelector('.code-header')) return
      const header = document.createElement('div')
      header.className = 'code-header'
      header.innerHTML =
        '<span class="dot dot-red"></span>' +
        '<span class="dot dot-yellow"></span>' +
        '<span class="dot dot-green"></span>'
      pre.insertBefore(header, pre.firstChild)
    })

    root.querySelectorAll('pre code[class*="language-"]').forEach((code) => {
      const pre = code.parentElement
      if (!pre || pre.querySelector('.code-language-label')) return
      const language = code.className.match(/language-(\w+)/)
      if (language) {
        const label = document.createElement('span')
        label.className = 'code-language-label'
        label.textContent = language[1].charAt(0).toUpperCase() + language[1].slice(1)
        pre.appendChild(label)
      }
    })

    Prism.highlightAllUnder(root)
  }, [html])

  return <div ref={ref} className="note-content" dangerouslySetInnerHTML={{ __html: html }} />
}
