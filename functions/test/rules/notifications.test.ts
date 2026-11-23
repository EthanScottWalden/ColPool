import { after, before, beforeEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  setDoc,
  setLogLevel,
  Timestamp,
  updateDoc,
  where,
} from "firebase/firestore";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import {
  authedStudent,
  hostStudent,
  institutionUser,
  rulesContext,
  studentWithoutConsent,
  unauthenticated,
  type Persona,
} from "../helpers/personas.ts";
import { seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

const OWNER = authedStudent();
const OWNER_UID = OWNER.uid as string;

function seedDoc(read: boolean, minute: number) {
  return {
    type: "day_accepted",
    seriesId: "series-1",
    weekdays: ["tue"],
    title: "Day accepted",
    body: "Test Host accepted your request for Tuesday.",
    seriesLine: "Test's series from Test Lot",
    createdAt: Timestamp.fromDate(new Date(Date.UTC(2026, 10, 2, 18, minute))),
    read,
  };
}

describe("notifications: recipient reads and marks read, nothing else", () => {
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
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      // Every consented-or-not account gets an unread and a read notification of its own.
      for (const p of [OWNER, hostStudent(), studentWithoutConsent(), institutionUser()]) {
        const base = collection(db, "users", p.uid as string, "notifications");
        await setDoc(doc(base, "n-unread"), seedDoc(false, 1));
        await setDoc(doc(base, "n-read"), seedDoc(true, 2));
      }
    });
  });

  const col = (as: Persona, uid: string = OWNER_UID) =>
    collection(rulesContext(env, as).firestore(), "users", uid, "notifications");
  const ref = (as: Persona, id: string, uid: string = OWNER_UID) => doc(col(as, uid), id);

  describe("allowed", () => {
    it("recipient can get a notification", async () => {
      const snap = await assertSucceeds(getDoc(ref(OWNER, "n-unread")));
      assert.equal(snap.data()?.["read"], false);
    });

    it("recipient can list newest first (orderBy createdAt)", async () => {
      const snap = await assertSucceeds(getDocs(query(col(OWNER), orderBy("createdAt", "desc"))));
      assert.deepEqual(snap.docs.map((d) => d.id), ["n-read", "n-unread"]);
    });

    it("recipient can list unread (where read == false)", async () => {
      const snap = await assertSucceeds(getDocs(query(col(OWNER), where("read", "==", false))));
      assert.deepEqual(snap.docs.map((d) => d.id), ["n-unread"]);
    });

    it("recipient can flip read false -> true", async () => {
      await assertSucceeds(updateDoc(ref(OWNER, "n-unread"), { read: true }));
    });

    it("recipient can re-send read true -> true", async () => {
      await assertSucceeds(updateDoc(ref(OWNER, "n-read"), { read: true }));
    });
  });

  describe("denied", () => {
    it("another student cannot get, list or update", async () => {
      const other = hostStudent();
      await assertFails(getDoc(ref(other, "n-unread")));
      await assertFails(getDocs(col(other)));
      await assertFails(getDocs(query(col(other), where("read", "==", false))));
      await assertFails(updateDoc(ref(other, "n-unread"), { read: true }));
    });

    it("signed-out caller cannot get, list or update", async () => {
      const anon = unauthenticated();
      await assertFails(getDoc(ref(anon, "n-unread")));
      await assertFails(getDocs(col(anon)));
      await assertFails(updateDoc(ref(anon, "n-unread"), { read: true }));
    });

    it("institution account cannot read or update its own notifications", async () => {
      const inst = institutionUser();
      const uid = inst.uid as string;
      await assertFails(getDoc(ref(inst, "n-unread", uid)));
      await assertFails(getDocs(col(inst, uid)));
      await assertFails(updateDoc(ref(inst, "n-unread", uid), { read: true }));
    });

    it("owner without a consent record cannot read or update", async () => {
      const p = studentWithoutConsent();
      const uid = p.uid as string;
      await assertFails(getDoc(ref(p, "n-unread", uid)));
      await assertFails(getDocs(col(p, uid)));
      await assertFails(updateDoc(ref(p, "n-unread", uid), { read: true }));
    });

    it("client cannot create, even for themselves", async () => {
      await assertFails(setDoc(ref(OWNER, "n-new"), seedDoc(false, 3)));
      await assertFails(setDoc(ref(OWNER, "n-new"), { read: false }));
    });

    it("client cannot delete", async () => {
      await assertFails(deleteDoc(ref(OWNER, "n-unread")));
      await assertFails(deleteDoc(ref(OWNER, "n-read")));
    });

    it("read cannot go true -> false", async () => {
      await assertFails(updateDoc(ref(OWNER, "n-read"), { read: false }));
    });

    it("read cannot change together with another field", async () => {
      await assertFails(updateDoc(ref(OWNER, "n-unread"), { read: true, title: "Edited" }));
    });

    it("no other field can change on its own", async () => {
      for (const patch of [{ title: "Edited" }, { body: "Edited" }, { seriesLine: "Edited" }, { type: "series_ended" }]) {
        await assertFails(updateDoc(ref(OWNER, "n-unread"), patch));
      }
    });

    it("read must be a boolean", async () => {
      await assertFails(updateDoc(ref(OWNER, "n-unread"), { read: "true" }));
      await assertFails(updateDoc(ref(OWNER, "n-unread"), { read: 1 }));
      await assertFails(updateDoc(ref(OWNER, "n-unread"), { read: null }));
    });

    it("a collection-group query on notifications is denied", async () => {
      const db = rulesContext(env, OWNER).firestore();
      await assertFails(getDocs(collectionGroup(db, "notifications")));
      await assertFails(getDocs(query(collectionGroup(db, "notifications"), where("read", "==", false))));
    });
  });
});
