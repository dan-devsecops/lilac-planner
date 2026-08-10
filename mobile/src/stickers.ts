// Mirrors frontend/src/stickers.js verbatim - backend config (planner.stickers.*) is the
// source of truth; this only drives progress-bar rendering client-side.
export const BASE_THRESHOLD = 20;
export const THRESHOLD_STEP = 10;

export function thresholdFor(index: number): number {
  return BASE_THRESHOLD + index * THRESHOLD_STEP;
}

export function nextThreshold(points: number): number {
  if (points < BASE_THRESHOLD) return BASE_THRESHOLD;
  const reachedIdx = Math.floor((points - BASE_THRESHOLD) / THRESHOLD_STEP);
  return BASE_THRESHOLD + (reachedIdx + 1) * THRESHOLD_STEP;
}

export function previousThreshold(points: number): number {
  if (points < BASE_THRESHOLD) return 0;
  const reachedIdx = Math.floor((points - BASE_THRESHOLD) / THRESHOLD_STEP);
  return BASE_THRESHOLD + reachedIdx * THRESHOLD_STEP;
}
