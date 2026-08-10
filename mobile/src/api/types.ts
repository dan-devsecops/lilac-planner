/** Mirrors backend/src/main/java/com/lilac/planner/dto. */

export type Recurrence = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface TaskDto {
  id: string;
  title: string;
  points: number;
  completed: boolean;
  position: number;
  /** "HH:mm:ss" or null */
  scheduledTime: string | null;
  recurrence: Recurrence;
  recurrenceGroupId: string | null;
}

export interface DayDto {
  id: string;
  userId: string;
  /** "yyyy-MM-dd" */
  date: string;
  totalPoints: number;
  totalAvailablePoints: number;
  tasks: TaskDto[];
  earnedStickers: string[];
}

export interface TaskRequest {
  title?: string;
  points?: number;
  completed?: boolean;
  position?: number;
  /** "HH:mm:ss" */
  scheduledTime?: string | null;
  clearScheduledTime?: boolean;
  recurrence?: Recurrence;
}

export interface StatPointDto {
  date: string;
  points: number;
  completedTasks: number;
  totalTasks: number;
}

export interface UserDto {
  id: string;
  username: string;
  displayName: string;
  email: string;
  roles: string[];
  timezone: string | null;
}

export interface Sticker {
  code: string;
  emoji: string;
  name: string;
}

export interface AppVersionInfo {
  minSupportedAppVersion: string;
  latestAppVersion: string;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  /** Present only for mobile-mode login/refresh (client: "mobile" / body refreshToken). */
  refreshToken?: string;
}

/** RFC 9457 application/problem+json shape (see ApiExceptionHandler). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export type PushPlatform = 'EXPO' | 'WEB';

export interface PushSubscriptionRequest {
  platform: PushPlatform;
  token: string;
  p256dh?: string | null;
  auth?: string | null;
}

export interface PushSubscriptionDto {
  id: string;
  platform: PushPlatform;
  createdAt: string;
  lastSeenAt: string;
}
