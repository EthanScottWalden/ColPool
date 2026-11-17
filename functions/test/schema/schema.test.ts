import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import {
  COLLECTIONS,
  GENDER_OPTIONS,
  INTEREST_OPTIONS,
  MAJOR_OPTIONS,
  PROFILE_LIMITS,
  TRAIT_OPTIONS,
  WEEKDAYS,
  YEAR_OPTIONS,
  buildProfileOptionsDoc,
  coReason,
  reqReason,
  scheduleEntryKey,
} from "../../src/shared/schema/index.ts";

const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*$/;

describe("schema key builders", () => {
  it("are deterministic and match the documented shapes", () => {
    assert.equal(reqReason("s1"), "req:s1");
    assert.equal(reqReason("s1"), reqReason("s1"));
    assert.equal(coReason("s1", "tue"), "co:s1:tue");
    assert.equal(scheduleEntryKey("s1", "tue"), "s1_tue");
    assert.equal(scheduleEntryKey("s1", "tue"), scheduleEntryKey("s1", "tue"));
  });

  it("give distinct keys for distinct inputs", () => {
    const keys = new Set<string>();
    for (const d of WEEKDAYS) {
      keys.add(coReason("s1", d));
      keys.add(scheduleEntryKey("s1", d));
    }
    keys.add(reqReason("s1"));
    assert.equal(keys.size, WEEKDAYS.length * 2 + 1);
  });

  it("reject ids and weekdays that would break the key format", () => {
    assert.throws(() => reqReason(""));
    assert.throws(() => reqReason("a:b"));
    assert.throws(() => coReason("s1", "tuesday" as never));
    assert.throws(() => scheduleEntryKey("a/b", "mon"));
  });
});

describe("option lists", () => {
  const lists = {
    genders: GENDER_OPTIONS,
    years: YEAR_OPTIONS,
    majors: MAJOR_OPTIONS,
    interests: INTEREST_OPTIONS,
    traits: TRAIT_OPTIONS,
  };

  for (const [name, entries] of Object.entries(lists)) {
    it(`${name}: ids are unique, kebab-case ids (not labels), order is 1..n`, () => {
      const ids = entries.map((e) => e.id);
      assert.equal(new Set(ids).size, ids.length);
      for (const e of entries) {
        assert.match(e.id, KEBAB, `id "${e.id}" looks like a label`);
        assert.ok(e.label.length > 0);
      }
      assert.deepEqual(entries.map((e) => e.order), entries.map((_, i) => i + 1));
    });
  }

  it("years are ordered freshman to graduate (RC-21 closeness)", () => {
    assert.deepEqual(YEAR_OPTIONS.map((e) => e.id), ["freshman", "sophomore", "junior", "senior", "graduate"]);
  });

  it("majors hold the owner-supplied CBU list", () => {
    assert.equal(MAJOR_OPTIONS.length, 127);
  });

  it("the seed doc's parallel id arrays equal the entry ids", () => {
    const doc = buildProfileOptionsDoc();
    assert.deepEqual(doc.genderIds, doc.genders.map((e) => e.id));
    assert.deepEqual(doc.yearIds, doc.years.map((e) => e.id));
    assert.deepEqual(doc.majorIds, doc.majors.map((e) => e.id));
    assert.deepEqual(doc.interestIds, doc.interests.map((e) => e.id));
    assert.deepEqual(doc.traitIds, doc.traits.map((e) => e.id));
  });
});

describe("collections and docs", () => {
  it("collection names are unique", () => {
    const names = Object.values(COLLECTIONS);
    assert.equal(new Set(names).size, names.length);
  });

  it("RC-30 constants match docs/data-model.md", () => {
    const md = readFileSync(join(import.meta.dirname, "../../../docs/data-model.md"), "utf8");
    const documented = new Map<string, number>();
    for (const m of md.matchAll(/^\|\s*`(\w+Max|\w+Min)`\s*\|\s*(\d+)\s*\|/gm)) {
      documented.set(m[1] as string, Number(m[2]));
    }
    assert.deepEqual(Object.fromEntries(documented), { ...PROFILE_LIMITS });
  });
});
