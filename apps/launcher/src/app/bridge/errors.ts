export type RecoveryAction =
  | 'ACCEPT_EULA'
  | 'EDIT_COMMAND'
  | 'LOCATE_WORKSPACE'
  | 'OPEN_ACTIVITY'
  | 'OPEN_LOGS'
  | 'RECONNECT_CLIENT_PROFILE'
  | 'REPAIR_SERVER'
  | 'RESTART_LAUNCHER'
  | 'RETRY_OPERATION'
  | 'REVIEW_BACKUPS'
  | 'REVIEW_PLUGINS'
  | 'REVIEW_SERVER_HEALTH'
  | 'START_SERVER'
  | 'STOP_SERVER'
  | 'USE_SERVER_CONTROLS'
  | 'WAIT_FOR_SERVER_START';

export type RuntimeCommandError = {
  code: string;
  message: string;
  details?: string;
  recoverable?: boolean;
  action?: RecoveryAction | null;
  correlationId?: string;
};

const RECOVERY_ACTIONS: ReadonlySet<string> = new Set<RecoveryAction>([
  'ACCEPT_EULA',
  'EDIT_COMMAND',
  'LOCATE_WORKSPACE',
  'OPEN_ACTIVITY',
  'OPEN_LOGS',
  'RECONNECT_CLIENT_PROFILE',
  'REPAIR_SERVER',
  'RESTART_LAUNCHER',
  'RETRY_OPERATION',
  'REVIEW_BACKUPS',
  'REVIEW_PLUGINS',
  'REVIEW_SERVER_HEALTH',
  'START_SERVER',
  'STOP_SERVER',
  'USE_SERVER_CONTROLS',
  'WAIT_FOR_SERVER_START'
]);

const RECOVERY_ACTION_LABELS: Record<RecoveryAction, string> = {
  ACCEPT_EULA: 'Accept EULA',
  EDIT_COMMAND: 'Edit command',
  LOCATE_WORKSPACE: 'Locate server folder',
  OPEN_ACTIVITY: 'Open Activity',
  OPEN_LOGS: 'Open logs',
  RECONNECT_CLIENT_PROFILE: 'Reconnect client profile',
  REPAIR_SERVER: 'Repair server',
  RESTART_LAUNCHER: 'Restart LazyBuilder',
  RETRY_OPERATION: 'Try again',
  REVIEW_BACKUPS: 'Review restore points',
  REVIEW_PLUGINS: 'Review Plugins',
  REVIEW_SERVER_HEALTH: 'Review server readiness',
  START_SERVER: 'Start server',
  STOP_SERVER: 'Stop server',
  USE_SERVER_CONTROLS: 'Use server controls',
  WAIT_FOR_SERVER_START: 'Wait for server start'
};

function recoveryAction(value: unknown): RecoveryAction | null {
  return typeof value === 'string' && RECOVERY_ACTIONS.has(value) ? (value as RecoveryAction) : null;
}

export function recoveryActionLabel(action: RecoveryAction): string {
  return RECOVERY_ACTION_LABELS[action];
}

export class RuntimeError extends Error {
  readonly code: string;
  readonly details: string;
  readonly recoverable: boolean;
  readonly action: RecoveryAction | null;
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
        action: recoveryAction(candidate.action),
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
