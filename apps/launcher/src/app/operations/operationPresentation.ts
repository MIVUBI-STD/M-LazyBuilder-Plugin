const OPERATION_TITLES: Readonly<Record<string, string>> = {
  'create-server': 'Create server',
  'adopt-server': 'Add existing server',
  'duplicate-server': 'Duplicate server',
  'delete-server': 'Delete server',
  'provision-server': 'Prepare server',
  'update-paper': 'Update Paper',
  'backup-server': 'Create server restore point',
  'restore-server': 'Restore server',
  'delete-backup': 'Delete restore point',
  'save-server-resources': 'Save server memory settings',
  'repair-server': 'Repair server',
  'install-plugin': 'Install plugin',
  'update-plugin': 'Update plugin',
  'change-plugin-state': 'Change plugin state',
  'remove-plugin': 'Remove plugin',
  'remove-problem-plugin': 'Remove broken plugin',
  'resolve-plugin-duplicates': 'Resolve plugin duplicates',
  'select-client-profile': 'Select client profile',
  'sync-client-components': 'Sync client components',
  'export-support-bundle': 'Create support package',
  'launcher-update': 'Update LazyBuilder',
  'download-runtime': 'Prepare server software'
};

export function operationTitle(kind: string): string {
  return OPERATION_TITLES[kind] ?? 'Launcher task';
}
