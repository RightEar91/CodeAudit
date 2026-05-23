import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import go from 'highlight.js/lib/languages/go'
import c from 'highlight.js/lib/languages/c'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import properties from 'highlight.js/lib/languages/properties'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('go', go)
hljs.registerLanguage('c', c)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('properties', properties)
hljs.registerLanguage('markdown', markdown)

const extMap: Record<string, string> = {
  java: 'java',
  py: 'python',
  js: 'javascript',
  jsx: 'javascript',
  mjs: 'javascript',
  cjs: 'javascript',
  ts: 'typescript',
  tsx: 'typescript',
  go: 'go',
  c: 'c',
  cpp: 'c',
  cc: 'c',
  cxx: 'c',
  h: 'c',
  hpp: 'c',
  xml: 'xml',
  html: 'xml',
  htm: 'xml',
  css: 'css',
  scss: 'css',
  less: 'css',
  json: 'json',
  yaml: 'yaml',
  yml: 'yaml',
  sql: 'sql',
  sh: 'bash',
  bash: 'bash',
  zsh: 'bash',
  properties: 'properties',
  prop: 'properties',
  md: 'markdown',
  txt: 'plaintext',
}

export function detectLanguage(filePath: string): string {
  const ext = filePath.split('.').pop()?.toLowerCase() || ''
  return extMap[ext] || 'plaintext'
}

export function highlightCode(code: string, language: string): string {
  const lang = language || 'plaintext'
  try {
    const result = hljs.highlight(code, { language: lang, ignoreIllegals: true })
    return result.value
  } catch {
    return escapeHtml(code)
  }
}

export function highlightDiffLine(line: string, language: string): string {
  const prefix = line.length > 0 ? line.charAt(0) : ''
  const code = line.length > 1 ? line.substring(1) : ''
  return prefix + highlightCode(code, language)
}

export function highlightDiffContent(diffContent: string, filePath: string): Array<{ type: string; html: string }> {
  const language = detectLanguage(filePath)
  return diffContent.split('\n').map(line => {
    if (line.startsWith('@@')) {
      return { type: 'header', html: escapeHtml(line) }
    }
    if (line.startsWith('+') && !line.startsWith('+++')) {
      return { type: 'add', html: highlightDiffLine(line, language) }
    }
    if (line.startsWith('-') && !line.startsWith('---')) {
      return { type: 'remove', html: highlightDiffLine(line, language) }
    }
    return { type: 'context', html: highlightCode(line, language) }
  })
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
