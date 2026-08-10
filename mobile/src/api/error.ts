import type { ProblemDetail } from './types';

/**
 * Thrown for any non-2xx HTTP response. `status`/`detail` are parsed from the RFC 9457
 * application/problem+json body when present (see ApiExceptionHandler); callers should read
 * `status` rather than string-matching `message` (the web app's older code does the latter -
 * do not copy that pattern here).
 */
export class ApiError extends Error {
  status: number;
  detail?: string;

  constructor(status: number, message: string, detail?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
  }

  get isRateLimited() {
    return this.status === 429;
  }
}

export async function toApiError(res: Response): Promise<ApiError> {
  let detail: string | undefined;
  try {
    const problem: ProblemDetail = await res.json();
    detail = problem?.detail;
  } catch {
    // body wasn't problem+json (or was empty) - fall back to status text below
  }
  return new ApiError(res.status, detail || `${res.status} ${res.statusText}`, detail);
}
