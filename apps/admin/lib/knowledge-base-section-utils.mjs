export function buildKbMetaLines(kb) {
  const embeddingMode = String(kb?.embeddingMode || "").trim().toLowerCase();
  const kbId = String(kb?.id || "").trim();

  const modeLine = embeddingMode === "api" ? "云端嵌入" : "本地嵌入";

  return [modeLine, `ID: ${kbId || "-"}`];
}

export function resolveUploadMode(files) {
  const count = Array.isArray(files) ? files.length : 0;
  if (count <= 0) return "none";
  if (count === 1) return "single";
  return "batch";
}
