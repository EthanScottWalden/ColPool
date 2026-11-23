// The one way to write a notification. Works inside a Firestore Transaction or WriteBatch,
// so it commits with the change that caused it. Uses `create()`: a duplicate event fails.

import { FieldValue, type DocumentData, type DocumentReference, type Firestore } from "firebase-admin/firestore";
import { COLLECTIONS } from "../schema/collections.ts";
import { notificationId } from "./ids.ts";
import { buildNotificationText } from "./text.ts";
import { NOTIFICATIONS_SUBCOLLECTION, type NotificationEvent } from "./types.ts";
import { assertId, normalizeWeekdays } from "./validate.ts";

/** Anything with `create(ref, data)`: an Admin SDK `Transaction` or `WriteBatch`. */
export interface NotificationWriter {
  create(ref: DocumentReference, data: DocumentData): unknown;
}

/**
 * Adds one notification for `recipientUid` to the transaction or batch. Does not read.
 * Returns the notification id. Throws (before writing) on invalid input.
 */
export function writeNotification(
  writer: NotificationWriter,
  db: Firestore,
  recipientUid: string,
  event: NotificationEvent,
): string {
  assertId("recipient uid", recipientUid);
  const id = notificationId(event);
  const text = buildNotificationText(event);

  const data: DocumentData = {
    type: event.type,
    seriesId: event.seriesId,
    ...text,
    createdAt: FieldValue.serverTimestamp(),
    read: false,
  };
  if (event.type === "request_new") data["weekdays"] = normalizeWeekdays(event.weekdays);
  else if ("weekday" in event) data["weekdays"] = normalizeWeekdays([event.weekday]);

  const ref = db.collection(COLLECTIONS.users).doc(recipientUid).collection(NOTIFICATIONS_SUBCOLLECTION).doc(id);
  writer.create(ref, data);
  return id;
}
