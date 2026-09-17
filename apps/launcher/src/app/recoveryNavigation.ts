import type { RecoveryAction } from './bridge/errors';

export type RecoveryNavigationTarget =
  | 'activity'
  | 'overview'
  | 'plugins'
  | 'settings';

/**
 * Maps only safe, presentation-level recovery actions to existing product surfaces.
 * Destructive or state-changing actions intentionally return null so an error notice
 * never starts/stops/restarts/retries work without explicit flow-specific confirmation.
 */
export function recoveryNavigationTarget(action: RecoveryAction | null): RecoveryNavigationTarget | null {
  switch (action) {
    case 'OPEN_ACTIVITY':
      return 'activity';
    case 'START_SERVER':
    case 'REPAIR_SERVER':
    case 'REVIEW_SERVER_HEALTH':
    case 'REVIEW_BACKUPS':
    case 'OPEN_LOGS':
    case 'USE_SERVER_CONTROLS':
      return 'overview';
    case 'REVIEW_PLUGINS':
      return 'plugins';
    default:
      return null;
  }
}
