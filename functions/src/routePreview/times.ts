import type { RouteLeg } from "./routesClient.ts";
import type { RouteResult } from "../shared/schema/series.ts";

/**
 * Expected times along one route (data-model §2.11). Each hop is rounded to whole minutes
 * on its own: `legMin = Math.round(durationSec / 60)`. The first point departs at
 * `departMin`; every later point arrives at the previous departure plus the previous hop;
 * a stop departs `minutes` after it arrives. Values above 1439 are kept as they are.
 * `stopMinutes[i]` is the wait at point `i + 1`, so it has `legs.length - 1` entries.
 */
export function buildRouteResult(departMin: number, legs: readonly RouteLeg[], stopMinutes: readonly number[]): RouteResult {
  const times: RouteResult["times"] = [{ departMin }];
  let depart = departMin;
  legs.forEach((leg, i) => {
    const arrive = depart + Math.round(leg.durationSec / 60);
    if (i === legs.length - 1) {
      times.push({ arriveMin: arrive });
    } else {
      depart = arrive + (stopMinutes[i] ?? 0);
      times.push({ arriveMin: arrive, departMin: depart });
    }
  });
  return {
    legs: legs.map((l) => ({ meters: l.meters, durationSec: l.durationSec })),
    totalMeters: legs.reduce((s, l) => s + l.meters, 0),
    totalDurationSec: legs.reduce((s, l) => s + l.durationSec, 0),
    times,
  };
}
