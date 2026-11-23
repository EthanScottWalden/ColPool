import { SERIES_LIMITS } from "../schema/limits.ts";
import type { LegInput, PlaceAddress, RouteResult, SeriesInput, Stop } from "../schema/series.ts";
import type { PacificDate } from "../schema/types.ts";
import { WEEKDAYS, isWeekday, type Weekday } from "../schema/weekday.ts";
import { addDays, isLocalTime, lastDateOnWeekday, pacificDateOf } from "../time/pacific.ts";
import { conflictingWeekdays, type WeekdaySpan } from "./conflicts.ts";
import type { ValidationContext } from "./context.ts";
import type { SeriesValidationError } from "./errors.ts";

// Shared series validation (contract #23, ADR 0007). Pure: no Firestore, no Functions.
// Parses untrusted callable data and returns ALL errors at once.

export type SeriesValidationResult =
  | { ok: true; input: SeriesInput }
  | { ok: false; errors: SeriesValidationError[] };

type Obj = Record<string, unknown>;

/** Plain objects only: callable data is JSON, so a Date, Map or class instance is rejected. */
function isObj(v: unknown): v is Obj {
  if (typeof v !== "object" || v === null || Array.isArray(v)) return false;
  const proto = Object.getPrototypeOf(v);
  return proto === Object.prototype || proto === null;
}

// Technical caps on untrusted strings (not product rules): Places text and ids are far shorter.
const ADDRESS_TEXT_MAX = 500;
const PLACE_ID_MAX = 300;

function validDate(v: unknown): v is PacificDate {
  if (typeof v !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(v)) return false;
  try {
    return addDays(v, 0) === v;
  } catch {
    return false;
  }
}

class Collector {
  errors: SeriesValidationError[] = [];
  add(code: SeriesValidationError["code"], path: string, params?: SeriesValidationError["params"]): void {
    this.errors.push(params ? { code, path, params } : { code, path });
  }
}

function wholeNumber(c: Collector, v: unknown, path: string, min: number, max: number): number | undefined {
  if (v === undefined || v === null) return void c.add("required", path);
  if (typeof v !== "number" || !Number.isInteger(v)) return void c.add("notInteger", path);
  if (v < min || v > max) return void c.add("outOfRange", path, { min, max });
  return v;
}

function address(c: Collector, v: unknown, path: string): PlaceAddress | undefined {
  if (v === undefined || v === null) return void c.add("required", path);
  if (!isObj(v)) return void c.add("invalidAddress", path);
  const before = c.errors.length;
  const text = typeof v["text"] === "string" ? v["text"].trim() : "";
  const placeId = typeof v["placeId"] === "string" ? v["placeId"].trim() : "";
  if (text === "") c.add("invalidAddress", `${path}.text`);
  else if (text.length > ADDRESS_TEXT_MAX) c.add("tooLong", `${path}.text`, { limit: ADDRESS_TEXT_MAX });
  if (placeId === "") c.add("invalidAddress", `${path}.placeId`);
  else if (placeId.length > PLACE_ID_MAX) c.add("tooLong", `${path}.placeId`, { limit: PLACE_ID_MAX });
  const lat = v["lat"];
  const lng = v["lng"];
  if (typeof lat !== "number" || !Number.isFinite(lat) || lat < -90 || lat > 90) c.add("invalidAddress", `${path}.lat`);
  if (typeof lng !== "number" || !Number.isFinite(lng) || lng < -180 || lng > 180) c.add("invalidAddress", `${path}.lng`);
  if (c.errors.length > before) return undefined;
  return { text, placeId, lat: lat as number, lng: lng as number };
}

function leg(c: Collector, v: unknown, path: string, ctx: ValidationContext): LegInput | undefined {
  if (!isObj(v)) return void c.add("required", path);
  const before = c.errors.length;
  let departTime = "";
  if (v["departTime"] === undefined || v["departTime"] === null) c.add("required", `${path}.departTime`);
  else if (!isLocalTime(v["departTime"])) c.add("invalidTime", `${path}.departTime`);
  else departTime = v["departTime"];

  let campusPointId = "";
  const cp = v["campusPointId"];
  if (cp === undefined || cp === null || cp === "") c.add("required", `${path}.campusPointId`);
  else if (typeof cp !== "string" || !ctx.activeCampusPointIds.includes(cp)) c.add("unknownCampusPoint", `${path}.campusPointId`);
  else campusPointId = cp;

  const stops: Stop[] = [];
  const rawStops = v["stops"];
  if (rawStops === undefined || rawStops === null) c.add("required", `${path}.stops`);
  else if (!Array.isArray(rawStops)) c.add("required", `${path}.stops`);
  else {
    if (rawStops.length > SERIES_LIMITS.maxStopsPerDirection) {
      // Don't parse an oversized array: one error, not one per element.
      c.add("tooManyStops", `${path}.stops`, { limit: SERIES_LIMITS.maxStopsPerDirection });
    } else rawStops.forEach((s: unknown, i) => {
      const sp = `${path}.stops.${i}`;
      if (!isObj(s)) return void c.add("required", sp);
      const a = address(c, s["address"], `${sp}.address`);
      const m = wholeNumber(c, s["minutes"], `${sp}.minutes`, SERIES_LIMITS.stopMinutesMin, SERIES_LIMITS.stopMinutesMax);
      if (a && m !== undefined) stops.push({ address: a, minutes: m });
    });
  }
  if (c.errors.length > before) return undefined;
  return { departTime, campusPointId, stops };
}

/** Parses untrusted callable data. On success `input` is a clean, normalized SeriesInput. */
export function validateSeriesInput(raw: unknown, ctx: ValidationContext): SeriesValidationResult {
  const c = new Collector();
  if (!isObj(raw)) {
    c.add("required", "");
    return { ok: false, errors: c.errors };
  }
  const today = pacificDateOf(ctx.now);

  const pickupSpot = address(c, raw["pickupSpot"], "pickupSpot");
  const priceDollars = wholeNumber(c, raw["priceDollars"], "priceDollars", SERIES_LIMITS.priceDollarsMin, SERIES_LIMITS.priceDollarsMax);
  const maxRiders = wholeNumber(c, raw["maxRiders"], "maxRiders", SERIES_LIMITS.ridersMin, SERIES_LIMITS.ridersMax);

  let vehicle: string | undefined;
  if (typeof raw["vehicle"] !== "string" || raw["vehicle"].trim() === "") c.add("required", "vehicle");
  else if (raw["vehicle"].trim().length > SERIES_LIMITS.vehicleMax) c.add("tooLong", "vehicle", { limit: SERIES_LIMITS.vehicleMax });
  else vehicle = raw["vehicle"].trim();

  // Dates.
  let startDate: PacificDate | undefined;
  let endDate: PacificDate | undefined;
  for (const key of ["startDate", "endDate"] as const) {
    const v = raw[key];
    if (v === undefined || v === null || v === "") c.add("required", key);
    else if (!validDate(v)) c.add("invalidDate", key);
    else if (key === "startDate") startDate = v;
    else endDate = v;
  }
  if (startDate !== undefined && startDate < today) c.add("startInPast", "startDate");
  const rangeOk = startDate !== undefined && endDate !== undefined && endDate >= startDate;
  if (startDate !== undefined && endDate !== undefined && !rangeOk) c.add("endBeforeStart", "endDate");

  // Weekdays.
  const weekdays: Weekday[] = [];
  const rawWeekdays = raw["weekdays"];
  let weekdaysOk = false;
  if (!Array.isArray(rawWeekdays) || rawWeekdays.length === 0) c.add("required", "weekdays");
  else if (rawWeekdays.length > WEEKDAYS.length) c.add("outOfRange", "weekdays");
  else {
    weekdaysOk = true;
    const seen = new Set<string>();
    rawWeekdays.forEach((w: unknown, i) => {
      if (!isWeekday(w)) {
        weekdaysOk = false;
        return void c.add("outOfRange", `weekdays.${i}`);
      }
      if (seen.has(w)) {
        weekdaysOk = false;
        return void c.add("duplicateWeekday", "weekdays", { weekday: w });
      }
      seen.add(w);
      weekdays.push(w);
    });
    weekdays.sort((a, b) => WEEKDAYS.indexOf(a) - WEEKDAYS.indexOf(b));
  }

  // Per-weekday legs. `days` keys must match `weekdays` exactly.
  const days: SeriesInput["days"] = {};
  let daysOk = weekdaysOk;
  const rawDays = raw["days"];
  if (!isObj(rawDays)) {
    c.add("required", "days");
    daysOk = false;
  } else {
    let unknownKey = false;
    for (const key of Object.keys(rawDays)) {
      // Never echo an attacker-supplied key into an error path; only real weekday ids appear.
      if (!isWeekday(key)) unknownKey = true;
      else if (weekdaysOk && !weekdays.includes(key)) c.add("outOfRange", `days.${key}`);
    }
    if (unknownKey) c.add("outOfRange", "days");
    for (const wd of weekdays) {
      const d = rawDays[wd];
      if (!isObj(d)) {
        c.add("required", `days.${wd}`);
        daysOk = false;
        continue;
      }
      const to = leg(c, d["toCampus"], `days.${wd}.toCampus`, ctx);
      const from = leg(c, d["fromCampus"], `days.${wd}.fromCampus`, ctx);
      if (to && from) days[wd] = { toCampus: to, fromCampus: from };
      else daysOk = false;
    }
  }

  // At least one chosen weekday has a date in range (not each one).
  if (rangeOk && weekdaysOk && !weekdays.some((wd) => lastDateOnWeekday(startDate as string, endDate as string, wd) !== null)) {
    c.add("noDateOnWeekdays", "");
  }

  // RC-31, RC-32.
  const span: WeekdaySpan | undefined = rangeOk && weekdaysOk ? { startDate: startDate as string, endDate: endDate as string, weekdays } : undefined;
  c.errors.push(...validateHosting(span, ctx));

  if (c.errors.length > 0 || !pickupSpot || priceDollars === undefined || maxRiders === undefined || vehicle === undefined || !rangeOk || !daysOk) {
    return { ok: false, errors: c.errors };
  }
  return {
    ok: true,
    input: { pickupSpot, startDate: startDate as string, endDate: endDate as string, priceDollars, vehicle, maxRiders, weekdays, days },
  };
}

/**
 * RC-31 and RC-32 against the loaded context. `span` is the series being posted, or
 * undefined when its dates or weekdays are unusable (only the limit is checked then).
 */
export function validateHosting(span: WeekdaySpan | undefined, ctx: ValidationContext): SeriesValidationError[] {
  const errors: SeriesValidationError[] = [];
  const today = pacificDateOf(ctx.now);
  // Series past their end date still say "active" until the daily job runs (RC-31).
  const hosted = ctx.hostedSeries.filter((s) => s.endDate >= today);
  if (hosted.length >= SERIES_LIMITS.maxActiveHosted) {
    errors.push({ code: "hostingLimitReached", path: "", params: { limit: SERIES_LIMITS.maxActiveHosted } });
  }
  if (!span) return errors;

  const overlap = new Set<Weekday>();
  for (const s of hosted) for (const wd of conflictingWeekdays(span, s, today)) overlap.add(wd);
  const riding = new Set<Weekday>();
  for (const e of ctx.schedule) {
    if (e.role !== "rider" || e.state !== "accepted" || e.endDate < today) continue;
    for (const wd of conflictingWeekdays(span, { startDate: e.startDate, endDate: e.endDate, weekdays: [e.weekday] }, today)) riding.add(wd);
  }
  for (const wd of WEEKDAYS) {
    if (overlap.has(wd)) errors.push({ code: "hostingWeekdayOverlap", path: `days.${wd}`, params: { weekday: wd } });
  }
  for (const wd of WEEKDAYS) {
    if (riding.has(wd)) errors.push({ code: "riderScheduleConflict", path: `days.${wd}`, params: { weekday: wd } });
  }
  return errors;
}

/**
 * After routes exist (#28, #30): per weekday, the campus departure must not be before the
 * expected arrival (equal is allowed), and the arrival may not pass midnight.
 */
export function validateCampusTimes(
  input: Pick<SeriesInput, "weekdays" | "days">,
  route: Partial<Record<Weekday, { toCampus: RouteResult; fromCampus?: RouteResult }>>,
): SeriesValidationError[] {
  const errors: SeriesValidationError[] = [];
  for (const wd of input.weekdays) {
    const times = route[wd]?.toCampus.times;
    const day = input.days[wd];
    const arrive = times?.[times.length - 1]?.arriveMin;
    if (arrive === undefined || !day) continue;
    const path = `days.${wd}.fromCampus.departTime`;
    const [hh, mm] = day.fromCampus.departTime.split(":").map(Number) as [number, number];
    if (arrive > 1439 || hh * 60 + mm < arrive) {
      errors.push({ code: "campusDepartureBeforeArrival", path, params: { weekday: wd } });
    }
  }
  return errors;
}
