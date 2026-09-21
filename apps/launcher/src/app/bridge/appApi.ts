import { invokeRuntime } from './invokeRuntime';
import type {
  DiagnosticSummary,
  LauncherOperationSnapshot,
  LauncherSettings,
  ServerReadinessSnapshot,
  ServerRepairPlan,
  ServerRepairResult,
  StartupReport,
  SystemSnapshot
} from './runtimeTypes';

const readinessApi = {
  server: (id: string) => invokeRuntime<ServerReadinessSnapshot>('launcher_server_health', { id }),
  repairPlan: (id: string) => invokeRuntime<ServerRepairPlan>('launcher_server_repair_plan', { id }),
  repair: (id: string) => invokeRuntime<ServerRepairResult>('launcher_server_repair', { id })
};

export const appApi = {
  system: {
    snapshot: () => invokeRuntime<SystemSnapshot>('system_snapshot')
  },
  diagnostics: {
    summary: () => invokeRuntime<DiagnosticSummary>('diagnostics_summary'),
    exportSupportBundle: () => invokeRuntime<string | null>('diagnostics_export_support_bundle')
  },
  startup: {
    status: () => invokeRuntime<StartupReport>('launcher_startup_status')
  },
  settings: {
    get: () => invokeRuntime<LauncherSettings>('launcher_settings_get'),
    save: (settings: LauncherSettings) => invokeRuntime<LauncherSettings>('launcher_settings_save', { settings })
  },
  readiness: readinessApi,
  operations: {
    list: () => invokeRuntime<LauncherOperationSnapshot[]>('launcher_operation_list'),
    get: (id: string) => invokeRuntime<LauncherOperationSnapshot>('launcher_operation', { id }),
    cancel: (id: string) => invokeRuntime<LauncherOperationSnapshot>('launcher_operation_cancel', { id })
  }
};
