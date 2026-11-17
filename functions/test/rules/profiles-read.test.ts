import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteField, doc, getDoc, getDocs, setDoc, setLogLevel } from "firebase/firestore";
import { coReason, reqReason } from "../../src/shared/schema/index.ts";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent,
  coRiderStudent,
  hostStudent,
  institutionUser,
  requesterStudent,
  rulesContext,
  studentWithoutConsent,
  unauthenticated,
  unrelatedStudent,
  type Persona,
} from "../helpers/personas.ts";
import { seedAccess, seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const host = hostStudent();
const requester = requesterStudent();
const coRider = coRiderStudent();
const unrelated = unrelatedStudent();
const HOST = host.uid as string;
const REQ = requester.uid as string;
const CO = coRider.uid as string;

describe("profiles read (RC-01)", () => {
  let env: RulesTestEnvironment;
  before(async () => {
    env = await createRulesEnv();
  });
  after(async () => {
    await env.cleanup();
  });
  beforeEach(async () => {
    await env.clearFirestore();
    await seedBase(env, [institutionUser()]);
  });

  const read = (as: Persona, ownerUid: string) =>
    getDoc(doc(rulesContext(env, as).firestore(), "profiles", ownerUid));

  it("the owner can read their own profile (no profileAccess doc needed)", async () => {
    await assertSucceeds(read(authedStudent(), authedStudent().uid as string));
  });

  it("case 1: any student can read an active host's profile", async () => {
    await seedAccess(env, HOST, { activeHostedSeries: { s1: true }, viewers: {} });
    await assertSucceeds(read(unrelated, HOST));
    await assertSucceeds(read(authedStudent(), HOST));
  });

  it("case 2: a host can read a requester's profile", async () => {
    await seedAccess(env, REQ, { activeHostedSeries: {}, viewers: { [HOST]: { [reqReason("s1")]: true } } });
    await assertSucceeds(read(host, REQ));
    await assertFails(read(unrelated, REQ));
  });

  it("case 3: a requester can read the host's profile (stored explicitly)", async () => {
    await seedAccess(env, HOST, { activeHostedSeries: {}, viewers: { [REQ]: { [reqReason("s1")]: true } } });
    await assertSucceeds(read(requester, HOST));
    await assertFails(read(unrelated, HOST));
  });

  it("case 4: co-riders accepted on the same weekday can read each other", async () => {
    await seedAccess(env, REQ, { viewers: { [CO]: { [coReason("s1", "tue")]: true } } });
    await seedAccess(env, CO, { viewers: { [REQ]: { [coReason("s1", "tue")]: true } } });
    await assertSucceeds(read(coRider, REQ));
    await assertSucceeds(read(requester, CO));
    await assertFails(read(unrelated, REQ));
  });

  it("an unrelated student is denied, even when other viewers have access", async () => {
    await seedAccess(env, REQ, { activeHostedSeries: {}, viewers: { [HOST]: { [reqReason("s1")]: true } } });
    await assertFails(read(unrelated, REQ));
  });

  it("a missing profileAccess doc denies", async () => {
    await assertFails(read(unrelated, HOST));
  });

  it("a profileAccess doc with missing keys denies without erroring open", async () => {
    await seedAccess(env, HOST, {});
    await assertFails(read(unrelated, HOST));
  });

  it("access ends immediately when the last reason is removed", async () => {
    await seedAccess(env, REQ, {
      viewers: { [HOST]: { [reqReason("s1")]: true, [coReason("s1", "mon")]: true } },
    });
    await assertSucceeds(read(host, REQ));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), "profileAccess", REQ),
        { viewers: { [HOST]: { [reqReason("s1")]: deleteField() } } },
        { merge: true },
      );
    });
    await assertSucceeds(read(host, REQ)); // one reason left
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(
        doc(ctx.firestore(), "profileAccess", REQ),
        { viewers: { [HOST]: { [coReason("s1", "mon")]: deleteField() } } },
        { merge: true },
      );
    });
    await assertFails(read(host, REQ));
  });

  it("access ends immediately when activeHostedSeries empties", async () => {
    await seedAccess(env, HOST, { activeHostedSeries: { s1: true } });
    await assertSucceeds(read(unrelated, HOST));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "profileAccess", HOST), { activeHostedSeries: { s1: deleteField() } }, { merge: true });
    });
    await assertFails(read(unrelated, HOST));
  });

  it("a viewer with an empty reason map is denied", async () => {
    await seedAccess(env, REQ, { activeHostedSeries: {}, viewers: { [HOST]: {} } });
    await assertFails(read(host, REQ));
  });

  for (const other of [institutionUser(), studentWithoutConsent(), unauthenticated()]) {
    it(`${other.name} cannot read an active host's profile`, async () => {
      await seedAccess(env, HOST, { activeHostedSeries: { s1: true }, viewers: { [other.uid ?? "x"]: { r: true } } });
      await assertFails(read(other, HOST));
    });
  }

  it("denies listing profiles, including a query for an active host", async () => {
    await seedAccess(env, HOST, { activeHostedSeries: { s1: true } });
    const db = rulesContext(env, unrelated).firestore();
    await assertFails(getDocs(collection(db, "profiles")));
    await assertFails(getDocs(collection(db, "profiles")));
    const ownerDb = rulesContext(env, authedStudent()).firestore();
    await assertFails(getDocs(collection(ownerDb, "profiles")));
  });
});
