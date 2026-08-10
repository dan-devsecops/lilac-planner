import { describe, expect, it } from 'vitest';
import {
  BASE_THRESHOLD,
  THRESHOLD_STEP,
  nextThreshold,
  previousThreshold,
  thresholdFor,
} from '../stickers.js';

describe('stickers thresholds', () => {
  it('exposes 20 and 10 as the base + step', () => {
    expect(BASE_THRESHOLD).toBe(20);
    expect(THRESHOLD_STEP).toBe(10);
  });

  it('thresholdFor(i) yields 20, 30, 40, … in order', () => {
    expect(thresholdFor(0)).toBe(20);
    expect(thresholdFor(1)).toBe(30);
    expect(thresholdFor(2)).toBe(40);
    expect(thresholdFor(5)).toBe(70);
  });

  describe('nextThreshold', () => {
    it('returns the base threshold when the user is below it', () => {
      expect(nextThreshold(0)).toBe(20);
      expect(nextThreshold(15)).toBe(20);
      expect(nextThreshold(19)).toBe(20);
    });

    it('returns the immediately next 10-point tier above the current points', () => {
      expect(nextThreshold(20)).toBe(30);
      expect(nextThreshold(25)).toBe(30);
      expect(nextThreshold(30)).toBe(40);
      expect(nextThreshold(59)).toBe(60);
    });
  });

  describe('previousThreshold', () => {
    it('returns 0 below the base threshold', () => {
      expect(previousThreshold(0)).toBe(0);
      expect(previousThreshold(19)).toBe(0);
    });

    it('returns the latest tier the user has already passed', () => {
      expect(previousThreshold(20)).toBe(20);
      expect(previousThreshold(25)).toBe(20);
      expect(previousThreshold(30)).toBe(30);
      expect(previousThreshold(48)).toBe(40);
    });
  });
});
