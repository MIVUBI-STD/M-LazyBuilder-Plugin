import { invoke } from '@tauri-apps/api/core';

export type RecoveryActionCode =
  | 'ACCEPT_EULA'
  | 'LOCATE_WORKSPACE'
  | 'OPEN_ACTIVITY'
  | 'OPEN_LOGS'
  | 'RECONNECT_CLIENT_PROFILE'
  | 'REPAIR_SERVER'
  | 'RESTART_LAUNCHER'
  | 'RETRY_OPERATION'
  | 'REVIEW_SERVER_HEALTH'
  | 'STOP_SERVER'
  | 'WAIT_FOR_SERVER_START';

export type RuntimeCommandError = {
  code: string;
  message: string;
  details?: string;
  recoverable?: boolean;
  action?: string | null;
  correlationId?: string;
};

export class RuntimeError extends Error {
  readonly code: string;
  readonly details: string;
  readonly recoverable: boolean;
  readonly action?: string | null;
  readonly correlationId: string;

  constructor(error: RuntimeCommandError) {
    super(error.message);
    this.name = 'RuntimeError';
    this.code = error.code;
    this.details = error.details ?? '';
    this.recoverable = error.recoverable ?? false;
    this.action = error.action ?? null;
    this.correlationId = error.correlationId ?? '';
  }
}

export function runtimeError(value: unknown): RuntimeCommandError {
  if (value && typeof value === 'object') {
    const candidate = value as Record<string, unknown>;
    if (typeof candidate.code === 'string' && typeof candidate.message === 'string') {
      return {
        code: candidate.code,
        message: candidate.message,
        details: typeof candidate.details === 'string' ? candidate.details : '',
        recoverable: candidate.recoverable === true,
        action: typeof candidate.action === 'string' ? candidate.action : null,
        correlationId: typeof candidate.correlationId === 'string' ? candidate.correlationId : ''
      };
    }
  }
  const message = String(value ?? '').replace(/^Error:\s*/i, '').trim();
  return {
    code: 'RUNTIME_ERROR',
    message: message || 'Something went wrong. Try again.',
    details: '',
    recoverable: false,
    action: null,
    correlationId: ''
  };
}

export async function invokeRuntime<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  try {
    return await invoke<T>(command, args);
  } catch (value) {
    throw new RuntimeError(runtimeError(value));
  }
}
