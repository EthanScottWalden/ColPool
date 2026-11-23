import type { DocumentReference, DocumentSnapshot, Firestore, Query, QuerySnapshot, Transaction } from "firebase-admin/firestore";
import { COLLECTIONS } from "../schema/collections.ts";
import type { ScheduleDoc, ScheduleEntry, PacificDate, SeriesDoc } from "../schema/index.ts";
import type { Weekday } from "../schema/weekday.ts";

function read(tx: Transaction | undefined, ref: Query): Promise<QuerySnapshot>;
function read(tx: Transaction | undefined, ref: DocumentReference): Promise<DocumentSnapshot>;
function read(tx: Transaction | undefined, ref: Query | DocumentReference): Promise<QuerySnapshot | DocumentSnapshot> {
  return tx !== undefined ? (tx.get(ref as never) as Promise<QuerySnapshot | DocumentSnapshot>) : ref.get();
}

// Loads what validation needs. The only validation file that touches Firestore.
// Pass a Transaction to read inside the posting transaction (#30).

export interface HostedSeriesSummary {
  id: string;
  startDate: PacificDate;
  endDate: PacificDate;
  weekdays: Weekday[];
}

export interface ValidationContext {
  /** Server clock. */
  now: Date;
  activeCampusPointIds: string[];
  /** The host's series with status "active" (not yet filtered by end date). */
  hostedSeries: HostedSeriesSummary[];
  /** The host's `schedules/{uid}` entries (all roles and states). Missing doc = empty. */
  schedule: ScheduleEntry[];
}

/**
 * `tx` (optional) makes every read part of that transaction. A Transaction has no public
 * way back to its Firestore, so the caller passes both (decision recorded in ADR 0007).
 */
export async function loadValidationContext(
  db: Firestore,
  hostId: string,
  now: Date,
  tx?: Transaction,
): Promise<ValidationContext> {
  const [points, series, schedule] = await Promise.all([
    read(tx, db.collection(COLLECTIONS.campusPoints).where("active", "==", true)),
    read(tx, db.collection(COLLECTIONS.series).where("hostId", "==", hostId).where("status", "==", "active")),
    read(tx, db.collection(COLLECTIONS.schedules).doc(hostId)),
  ]);

  const entries = (schedule.data() as ScheduleDoc | undefined)?.entries ?? {};
  return {
    now,
    activeCampusPointIds: points.docs.map((d) => d.id),
    hostedSeries: series.docs.map((d) => {
      const s = d.data() as SeriesDoc;
      return { id: d.id, startDate: s.startDate, endDate: s.endDate, weekdays: s.weekdays };
    }),
    schedule: Object.values(entries),
  };
}
