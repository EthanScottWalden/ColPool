// Throwaway: proves CI turns red on a failing test (#54 acceptance). Never merged.
import { it } from "node:test";
import assert from "node:assert/strict";
it("deliberately fails to demonstrate a red CI run", () => {
  assert.equal(1, 2);
});
