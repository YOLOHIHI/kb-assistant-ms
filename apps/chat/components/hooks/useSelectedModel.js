import { useMemo } from "react"

export function useSelectedModel(models, selectedModelId) {
  const chatModels = useMemo(
    () =>
      (models || []).filter(
        (m) => !m.capabilities?.embedding && !m.capabilities?.embed && !m.tags?.includes("embed"),
      ),
    [models],
  )
  const selectedModel = useMemo(
    () => chatModels.find((m) => m.id === selectedModelId) || chatModels[0] || null,
    [chatModels, selectedModelId],
  )
  return { chatModels, selectedModel }
}
