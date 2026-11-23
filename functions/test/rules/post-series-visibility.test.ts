import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import type { RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, getDoc, setLogLevel } from "firebase/firestore";
import type { CallableRequest } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { adminApp } from "../../src/admin.ts";
import { createPostSeriesHandler } from "../../src/postSeries.ts";
import { createRoutePreviewHandler } from "../../src/routePreview.ts";
import { FakeRoutesClient } from "../../src/routePreview/routesClient.ts";
import { createRulesEnv } from "../helpers/rulesEnv.ts";
import { authedStudent, hostStudent, institutionUser, rulesContext, unrelatedStudent } from "../helpers/personas.ts";
import { seedBase } from "../helpers/rulesSeed.ts";

setLogLevel("silent");

// Posting grants every consented student read access to the host's profile (RC-01 case 1);
// firestore.rules is unchanged by #30. The handler writes through the Admin SDK into the same emulator.
describe("post series: host profile visibility (rules)", () => {
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

  it("after posting, another consented student reads the host profile; before and for others, not", async () => {
    const db = getFirestore(adminApp());
    const host = hostStudent();
    const other = unrelatedStudent();
    const read = (who: typeof host, uid: string) => getDoc(doc(rulesContext(env, who).firestore(), "profiles", uid));
    await assertFails(read(other, host.uid as string));

    await db.doc("campusPoints/cp1").set({ name: "Test Lot", lat: 33.9, lng: -117.4, order: 1, active: true });
    const now = new Date("2026-11-23T20:00:00Z");
    const silent = { info() {}, error() {} };
    const auth = { uid: host.uid as string, token: {} as never, rawToken: "x" };
    const req = (data: unknown): CallableRequest<unknown> => ({ data, auth, rawRequest: {} as never, acceptsStreaming: false });
    const leg = (t: string) => ({ departTime: t, campusPointId: "cp1", stops: [] });
    const s = {
      pickupSpot: { text: "1 Pickup Way", placeId: "p", lat: 33.9, lng: -117.4 },
      startDate: "2026-11-23", endDate: "2026-12-18", priceDollars: 5, vehicle: "Test Car", maxRiders: 3,
      weekdays: ["mon"], days: { mon: { toCampus: leg("07:30"), fromCampus: leg("12:00") } },
    };
    const { draftId } = await createRoutePreviewHandler({ db, routesClient: new FakeRoutesClient(), now: () => now, logger: silent })(req({ series: s }));
    await createPostSeriesHandler({ db, now: () => now, logger: silent })(req({ draftId, series: s }));

    await assertSucceeds(read(other, host.uid as string));
    await assertSucceeds(read(authedStudent(), host.uid as string));
    // An unrelated student's profile stays unreadable.
    await assertFails(read(other, authedStudent().uid as string));
  });
});
