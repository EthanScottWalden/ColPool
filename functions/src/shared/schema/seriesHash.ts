import { createHash } from "node:crypto";

// Canonical JSON and SHA-256 for the draft "reviewed" marker (ADR 0006, RC-12):
// `inputHash` is the hash of the exact SeriesInput the host reviewed, so posting
// can prove the series it saves is the series that was previewed.

/** JSON with object keys sorted at every level. `undefined` properties are dropped. */
export function canonicalJson(value: unknown): string {
  if (value === null || typeof value === "string" || typeof value === "boolean") return JSON.stringify(value);
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw new Error("canonicalJson: non-finite number");
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map((v) => (v === undefined ? "null" : canonicalJson(v))).join(",")}]`;
  if (typeof value === "object") {
    const proto = Object.getPrototypeOf(value);
    if (proto !== Object.prototype && proto !== null) {
      throw new Error("canonicalJson: only plain objects are supported");
    }
    const o = value as Record<string, unknown>;
    const parts = Object.keys(o)
      .filter((k) => o[k] !== undefined)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${canonicalJson(o[k])}`);
    return `{${parts.join(",")}}`;
  }
  throw new Error(`canonicalJson: unsupported value of type ${typeof value}`);
}

/** Lowercase hex SHA-256 of the canonical JSON. */
export function hashSeriesInput(input: unknown): string {
  return createHash("sha256").update(canonicalJson(input), "utf8").digest("hex");
}
