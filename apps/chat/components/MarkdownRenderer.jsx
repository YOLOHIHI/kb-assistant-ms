"use client"

import ReactMarkdown from "react-markdown"
import remarkMath from "remark-math"
import rehypeKatex from "rehype-katex"
import { visit } from "unist-util-visit"

// Remark plugin: transform [N] citation markers in text nodes into cite-ref AST nodes
function remarkCitationRefs() {
  return (tree) => {
    visit(tree, "text", (node, index, parent) => {
      if (!parent || index === null || typeof node.value !== "string") return

      const regex = /\[(\d+)\]/g
      const parts = []
      let lastIndex = 0
      let match

      while ((match = regex.exec(node.value)) !== null) {
        if (match.index > lastIndex) {
          parts.push({ type: "text", value: node.value.slice(lastIndex, match.index) })
        }
        parts.push({
          type: "citationRef",
          data: { hName: "cite-ref" },
          children: [{ type: "text", value: match[1] }],
        })
        lastIndex = regex.lastIndex
      }

      if (parts.length === 0) return

      if (lastIndex < node.value.length) {
        parts.push({ type: "text", value: node.value.slice(lastIndex) })
      }

      parent.children.splice(index, 1, ...parts)
      return index + parts.length
    })
  }
}

// LLMs often output display math as [ \formula ] instead of \[\formula\]
// Normalize to $$ form that remark-math recognizes.
// We require a \ inside to distinguish LaTeX from plain [text] or [1] citations.
function preprocessMath(content) {
  return content.replace(/\[ ([^\]]*\\[^\]]*) \]/g, (_, inner) => `$$${inner.trim()}$$`)
}

export default function MarkdownRenderer({ content = "", citations = [], onCitationClick }) {
  return (
    <div className="text-sm leading-relaxed">
      <ReactMarkdown
        remarkPlugins={[remarkMath, remarkCitationRefs]}
        rehypePlugins={[rehypeKatex]}
        components={{
          "cite-ref": ({ children }) => {
            const text = Array.isArray(children) ? children.join("") : String(children ?? "")
            const num = parseInt(text, 10)
            const idx = num - 1
            const hasCitation = Array.isArray(citations) && citations[idx] != null
            return (
              <sup>
                {hasCitation ? (
                  <button
                    onClick={() => onCitationClick?.(idx)}
                    className="text-blue-500 hover:text-blue-700 font-medium cursor-pointer"
                    title={citations[idx]?.filename || `引用 ${num}`}
                  >
                    [{num}]
                  </button>
                ) : (
                  <span className="text-zinc-400">[{num}]</span>
                )}
              </sup>
            )
          },
          p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
          h1: ({ children }) => <h1 className="text-base font-bold mb-2 mt-3">{children}</h1>,
          h2: ({ children }) => <h2 className="text-sm font-bold mb-2 mt-3">{children}</h2>,
          h3: ({ children }) => <h3 className="text-sm font-semibold mb-1 mt-2">{children}</h3>,
          ul: ({ children }) => <ul className="list-disc pl-5 mb-2 space-y-0.5">{children}</ul>,
          ol: ({ children }) => <ol className="list-decimal pl-5 mb-2 space-y-0.5">{children}</ol>,
          li: ({ children }) => <li>{children}</li>,
          strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
          em: ({ children }) => <em className="italic">{children}</em>,
          blockquote: ({ children }) => (
            <blockquote className="border-l-2 border-zinc-300 dark:border-zinc-600 pl-3 text-zinc-500 dark:text-zinc-400 mb-2">
              {children}
            </blockquote>
          ),
          hr: () => <hr className="border-zinc-200 dark:border-zinc-700 my-3" />,
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noopener noreferrer" className="text-blue-500 hover:underline">
              {children}
            </a>
          ),
          pre: ({ children }) => (
            <pre className="rounded-lg bg-zinc-100 dark:bg-zinc-800 p-3 overflow-x-auto my-2 text-xs font-mono">
              {children}
            </pre>
          ),
          code: ({ className, children }) =>
            className ? (
              <code className={className}>{children}</code>
            ) : (
              <code className="rounded bg-zinc-100 dark:bg-zinc-800 px-1 py-0.5 text-xs font-mono">
                {children}
              </code>
            ),
          table: ({ children }) => (
            <div className="overflow-x-auto mb-2">
              <table className="text-xs border-collapse w-full">{children}</table>
            </div>
          ),
          th: ({ children }) => (
            <th className="border border-zinc-200 dark:border-zinc-700 px-2 py-1 bg-zinc-50 dark:bg-zinc-800 font-medium text-left">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="border border-zinc-200 dark:border-zinc-700 px-2 py-1">{children}</td>
          ),
        }}
      >
        {preprocessMath(content)}
      </ReactMarkdown>
    </div>
  )
}
