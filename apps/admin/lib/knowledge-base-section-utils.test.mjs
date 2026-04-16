import test from "node:test";
import assert from "node:assert/strict";

import {
  buildKbMetaLines,
  resolveUploadMode,
} from "./knowledge-base-section-utils.mjs";

test("buildKbMetaLines shows local embedding text and kb id", () => {
  const lines = buildKbMetaLines({
    id: "kb_local_001",
    embeddingMode: "local",
    embeddingModel: "",
  });

  assert.deepEqual(lines, ["本地嵌入", "ID: kb_local_001"]);
});

test("buildKbMetaLines hides cloud embedding model uuid and still shows kb id", () => {
  const lines = buildKbMetaLines({
    id: "kb_cloud_001",
    embeddingMode: "api",
    embeddingModel: "model_embed_001",
  });

  assert.deepEqual(lines, ["云端嵌入", "ID: kb_cloud_001"]);
});

test("resolveUploadMode distinguishes empty, single, and batch selections", () => {
  assert.equal(resolveUploadMode([]), "none");
  assert.equal(resolveUploadMode([{ name: "a.docx" }]), "single");
  assert.equal(
    resolveUploadMode([{ name: "a.docx" }, { name: "b.docx" }]),
    "batch"
  );
});
