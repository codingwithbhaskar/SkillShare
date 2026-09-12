import { useEffect } from 'react'

/** Sets the browser tab title for the current page, restoring the
 *  previous one on unmount - without this every route showed the same
 *  static "SkillShare" title from index.html, which reads as unfinished
 *  next to a real product (a browser with five SkillShare tabs open
 *  should be able to tell them apart). */
export default function useDocumentTitle(title) {
  useEffect(() => {
    const previous = document.title
    document.title = title ? `${title} — SkillShare` : 'SkillShare'
    return () => {
      document.title = previous
    }
  }, [title])
}
