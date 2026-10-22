#!/usr/bin/env node
/** Register an ordinary Hub app; never convert an existing strict registration. */
import { openSync, writeFileSync, fsyncSync, closeSync, unlinkSync, readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual, parseArgs } from 'node:util';
import { spawnSync } from 'node:child_process';

function requirePrivateOutputDirectory(path) {
  if (process.platform !== 'win32') return;
  const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', `
    $ErrorActionPreference = 'Stop'
    $directory = Get-Item -LiteralPath $env:TABBYRUSH_CREDENTIAL_DIRECTORY
    if (!$directory.PSIsContainer -or ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe directory' }
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $allowed = @($identity, 'S-1-5-18', 'S-1-5-32-544')
    $acl = Get-Acl -LiteralPath $directory.FullName
    if (!$acl.AreAccessRulesProtected -or $acl.GetOwner([System.Security.Principal.SecurityIdentifier]).Value -notin $allowed) {
      throw 'Directory must have private protected ownership and permissions'
    }
    $rules = $acl.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier])
    if ($rules.Count -eq 0) { throw 'Directory has no explicit access rules' }
    foreach ($rule in $rules) {
      if ($rule.IsInherited -or $rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Value -notin $allowed) {
        throw 'Directory permits unexpected access'
      }
    }
  `], { env: { ...process.env, TABBYRUSH_CREDENTIAL_DIRECTORY: path }, encoding: 'utf8', windowsHide: true });
  if (result.error || result.status !== 0) throw new Error('Windows --out requires an existing private directory with protected ACLs limited to this user, SYSTEM and Administrators; registration was not attempted');
}

function secureCredentialFile(path) {
  if (process.platform !== 'win32') return;
  const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', `
    $ErrorActionPreference = 'Stop'
    $path = $env:TABBYRUSH_CREDENTIAL_OUTPUT
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $allowed = @($identity.Value, 'S-1-5-18', 'S-1-5-32-544')
    $acl = New-Object System.Security.AccessControl.FileSecurity
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sid in $allowed) {
      $principal = New-Object System.Security.Principal.SecurityIdentifier($sid)
      $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($principal, 'FullControl', 'Allow')
      $acl.AddAccessRule($rule)
    }
    [System.IO.File]::SetAccessControl($path, $acl)
    $actual = Get-Acl -LiteralPath $path
    if (!$actual.AreAccessRulesProtected) { throw 'Inherited permissions remain enabled' }
    $rules = $actual.GetAccessRules($true, $true, [System.Security.Principal.SecurityIdentifier])
    if ($rules.Count -ne 3) { throw 'Unexpected credential access rules' }
    foreach ($rule in $rules) {
      if ($rule.IsInherited -or $rule.AccessControlType -ne 'Allow' -or $rule.IdentityReference.Value -notin $allowed) {
        throw 'Credential file permits unexpected access'
      }
    }
  `], { env: { ...process.env, TABBYRUSH_CREDENTIAL_OUTPUT: path }, encoding: 'utf8', windowsHide: true });
  if (result.error || result.status !== 0) throw new Error('Cannot establish private Windows credential-file permissions; registration was not attempted');
}

/** Structural, identity, and system fields forbidden from editable allowlists */
export const FORBIDDEN_EDITABLE_FIELDS = {
  id: true,
  createdAt: true,
  userId: true,
  ownerUserId: true,
  taskId: true,
  syncVersion: true,
  syncedAt: true,
  deleted: true,
  deletedAt: true,
};

export function validateAssignmentPolicy(policy, parentTableSchema) {
  if (typeof policy !== 'object' || policy === null || Array.isArray(policy)) {
    throw new Error('assignmentPolicy must be a non-null object');
  }
  if (typeof policy.version !== 'number' || !Number.isSafeInteger(policy.version) || policy.version !== 1) {
    throw new Error(`Unsupported assignmentPolicy version: ${String(policy.version)}. Version 1 is required.`);
  }
  if (typeof policy.hierarchyParentField !== 'string' || !policy.hierarchyParentField.trim()) {
    throw new Error('assignmentPolicy.hierarchyParentField must be a non-empty string');
  }
  const hierarchyParentField = policy.hierarchyParentField.trim();

  if (!Array.isArray(policy.editableFields)) {
    throw new Error('assignmentPolicy.editableFields must be an array of string field names');
  }
  if (policy.editableFields.length === 0) {
    throw new Error('assignmentPolicy.editableFields cannot be empty');
  }
  const editableFields = [];
  const seen = new Set();
  for (const field of policy.editableFields) {
    if (typeof field !== 'string' || !field.trim()) {
      throw new Error('assignmentPolicy.editableFields entries must be non-empty strings');
    }
    const cleanField = field.trim();
    if (seen.has(cleanField)) {
      throw new Error(`Duplicate field '${cleanField}' in assignmentPolicy.editableFields`);
    }
    seen.add(cleanField);
    if (Boolean(FORBIDDEN_EDITABLE_FIELDS[cleanField]) || cleanField === hierarchyParentField) {
      throw new Error(
        `assignmentPolicy.editableFields cannot include structural, identity, or hierarchy field '${cleanField}'`,
      );
    }
    editableFields.push(cleanField);
  }

  if (parentTableSchema) {
    const colMap = new Map((parentTableSchema.columns || []).map((c) => [c.name, c]));
    const hierarchyCol = colMap.get(hierarchyParentField);
    if (!hierarchyCol) {
      throw new Error(
        `assignmentPolicy.hierarchyParentField '${hierarchyParentField}' does not exist in parent table`,
      );
    }
    if (!hierarchyCol.nullable) {
      throw new Error(
        `assignmentPolicy.hierarchyParentField '${hierarchyParentField}' must be nullable`,
      );
    }
    if (hierarchyCol.type !== 'string') {
      throw new Error(
        `assignmentPolicy.hierarchyParentField '${hierarchyParentField}' must have type 'string'`,
      );
    }
    for (const ef of editableFields) {
      const col = colMap.get(ef);
      if (!col) {
        throw new Error(`assignmentPolicy.editableField '${ef}' does not exist in parent table`);
      }
      if (ef === parentTableSchema.primaryKey) {
        throw new Error(
          `assignmentPolicy.editableFields cannot include parent table primary key '${ef}'`,
        );
      }
    }
  }

  return {
    version: 1,
    hierarchyParentField,
    editableFields,
  };
}

export function validateSharingConfig(sharing, schema) {
  if (typeof sharing !== 'object' || sharing === null || Array.isArray(sharing)) {
    throw new Error('sharing config must be a non-null object');
  }
  const {
    sharingTable,
    parentIdField,
    userIdField,
    parentTable,
    childTables,
    assignmentPolicy,
    ...unknownRest
  } = sharing;

  if (typeof sharingTable !== 'string' || !sharingTable.trim()) {
    throw new Error('sharing.sharingTable must be a non-empty string');
  }
  if (typeof parentIdField !== 'string' || !parentIdField.trim()) {
    throw new Error('sharing.parentIdField must be a non-empty string');
  }
  if (typeof userIdField !== 'string' || !userIdField.trim()) {
    throw new Error('sharing.userIdField must be a non-empty string');
  }
  if (typeof parentTable !== 'string' || !parentTable.trim()) {
    throw new Error('sharing.parentTable must be a non-empty string');
  }
  if (!Array.isArray(childTables) || !childTables.every((t) => typeof t === 'string')) {
    throw new Error('sharing.childTables must be an array of table name strings');
  }

  const cleanSharingTable = sharingTable.trim();
  const cleanParentIdField = parentIdField.trim();
  const cleanUserIdField = userIdField.trim();
  const cleanParentTable = parentTable.trim();
  const cleanChildTables = childTables.map((t) => t.trim());

  if (cleanSharingTable === cleanParentTable) {
    throw new Error(`sharingTable '${cleanSharingTable}' cannot be the same as parentTable`);
  }
  if (cleanChildTables.includes(cleanSharingTable)) {
    throw new Error(`sharingTable '${cleanSharingTable}' cannot be included in childTables`);
  }
  if (cleanChildTables.includes(cleanParentTable)) {
    throw new Error(`parentTable '${cleanParentTable}' cannot be included in childTables`);
  }

  let parentTableSchema;
  if (schema) {
    if (!schema[cleanParentTable]) {
      throw new Error(`sharing.parentTable '${cleanParentTable}' not found in schema`);
    }
    if (!schema[cleanSharingTable]) {
      throw new Error(`sharing.sharingTable '${cleanSharingTable}' not found in schema`);
    }
    for (const ct of cleanChildTables) {
      if (!schema[ct]) {
        throw new Error(`sharing.childTable '${ct}' not found in schema`);
      }
    }

    const sharingCols = new Map((schema[cleanSharingTable].columns || []).map((c) => [c.name, c]));
    if (!sharingCols.has('id')) {
      throw new Error(`sharingTable '${cleanSharingTable}' must have an 'id' column`);
    }
    if (!sharingCols.has(cleanParentIdField)) {
      throw new Error(
        `sharingTable '${cleanSharingTable}' must have column '${cleanParentIdField}' matching parentIdField`,
      );
    }
    if (!sharingCols.has(cleanUserIdField)) {
      throw new Error(
        `sharingTable '${cleanSharingTable}' must have column '${cleanUserIdField}' matching userIdField`,
      );
    }

    parentTableSchema = schema[cleanParentTable];
  }

  let validatedPolicy;
  if (assignmentPolicy !== undefined && assignmentPolicy !== null) {
    validatedPolicy = validateAssignmentPolicy(assignmentPolicy, parentTableSchema);
  }

  return {
    ...unknownRest,
    sharingTable: cleanSharingTable,
    parentIdField: cleanParentIdField,
    userIdField: cleanUserIdField,
    parentTable: cleanParentTable,
    childTables: cleanChildTables,
    ...(validatedPolicy ? { assignmentPolicy: validatedPolicy } : {}),
  };
}

export function validateProvisioningInput(input, confirmApp) {
  if (typeof input !== 'object' || input === null || Array.isArray(input)) {
    throw new Error('Schema input must be a non-null object');
  }
  if (!input.appId || (confirmApp !== undefined && input.appId !== confirmApp)) {
    throw new Error('Schema must contain appId/schema and --confirm-app must match');
  }
  if (!input.schema || typeof input.schema !== 'object' || Array.isArray(input.schema)) {
    throw new Error('Schema must contain appId/schema and --confirm-app must match');
  }
  if ('syncMode' in input || 'strictEpoch' in input || 'schemaVersion' in input) {
    throw new Error('Use an ordinary schema envelope without strict metadata or a client-assigned schemaVersion');
  }
  for (const [tableName, tableDef] of Object.entries(input.schema)) {
    if (typeof tableDef !== 'object' || tableDef === null || Array.isArray(tableDef)) {
      throw new Error(`Table '${tableName}' definition must be an object`);
    }
    if (typeof tableDef.primaryKey !== 'string' || !tableDef.primaryKey.trim()) {
      throw new Error(`Table '${tableName}' must have a string primaryKey`);
    }
    if (!Array.isArray(tableDef.columns) || tableDef.columns.length === 0) {
      throw new Error(`Table '${tableName}' columns must be a non-empty array`);
    }
  }

  let validatedSharing;
  if (input.sharing !== undefined && input.sharing !== null) {
    validatedSharing = validateSharingConfig(input.sharing, input.schema);
  }

  return {
    appId: input.appId,
    schema: input.schema,
    ...(validatedSharing !== undefined ? { sharing: validatedSharing } : {}),
  };
}

export function isMainModule() {
  if (!process.argv[1]) return false;
  try {
    const scriptPath = resolve(process.argv[1]).toLowerCase();
    const currentPath = fileURLToPath(import.meta.url).toLowerCase();
    return scriptPath === currentPath;
  } catch {
    return false;
  }
}

if (isMainModule()) {
  const { values } = parseArgs({ options: {
    origin: { type: 'string' }, schema: { type: 'string' }, out: { type: 'string' },
    'confirm-app': { type: 'string' }, help: { type: 'boolean', short: 'h' },
  } });
  if (values.help) {
    console.log('Usage: node scripts/hub-schema-provision.mjs --origin <origin> --schema <file> --out <new-credentials-file> --confirm-app <appId>');
    console.log('Set GLEAN_ADMIN_TOKEN in the environment. Registers a normal app; never updates/converts an existing app. Credentials are never printed.');
    console.log('Windows: --out must be inside an existing private, non-symlink directory with protected ACLs limited to the current user, SYSTEM and Administrators.');
    process.exit(0);
  }

  let fd;
  let credentialsSaved = false;
  let outputPath;
  try {
    const token = process.env.GLEAN_ADMIN_TOKEN;
    if (!token || !values.origin || !values.schema || !values.out) throw new Error('Explicit origin/schema/out and GLEAN_ADMIN_TOKEN are required');
    const origin = new URL(values.origin);
    const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname);
    if ((origin.protocol !== 'https:' && !(origin.protocol === 'http:' && loopback)) ||
        origin.username || origin.password || origin.search || origin.hash || origin.pathname !== '/') {
      throw new Error('Origin must be HTTPS (or loopback HTTP), without credentials, path, query or fragment');
    }
    const rawInput = JSON.parse(readFileSync(resolve(values.schema), 'utf8'));
    const validated = validateProvisioningInput(rawInput, values['confirm-app']);
    outputPath = resolve(values.out);
    // Secure parent custody first: tightening a newly created file cannot revoke an already opened read handle.
    requirePrivateOutputDirectory(dirname(outputPath));
    // Reserve a new private file before requesting a one-time key. Never overwrite custody.
    fd = openSync(outputPath, 'wx', 0o600);
    secureCredentialFile(outputPath);
    const request = async (method, path, body) => {
      const response = await fetch(new URL(path, origin), {
        method, redirect: 'error', signal: AbortSignal.timeout(15000),
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      });
      return response;
    };
    const payload = {
      appId: validated.appId,
      schema: validated.schema,
      ...(validated.sharing === undefined ? {} : { sharing: validated.sharing }),
    };
    const registration = await request('POST', '/api/v1/apps', payload);
    if (registration.ok) {
      const issued = await registration.json();
      if (issued.appId !== validated.appId || typeof issued.apiKey !== 'string' || !issued.apiKey) throw new Error('Registration returned invalid credentials');
      // Persist the issued key before any subsequent request, including failed read-back.
      writeFileSync(fd, JSON.stringify({ appId: issued.appId, apiKey: issued.apiKey, schemaVersion: issued.schemaVersion }, null, 2) + '\n');
      credentialsSaved = true;
      fsyncSync(fd);
    } else if (registration.status !== 409) {
      throw new Error(`Registration rejected (HTTP ${registration.status}); no update attempted`);
    }
    const response = await request('GET', `/api/v1/apps/${encodeURIComponent(validated.appId)}`);
    if (!response.ok) throw new Error(`Schema read-back failed (HTTP ${response.status})`);
    const current = await response.json();
    if (current.appId !== validated.appId || current.syncMode != null || current.strictEpoch != null ||
        !Number.isSafeInteger(current.schemaVersion) || current.schemaVersion < 1 ||
        !isDeepStrictEqual(current.schema, validated.schema) ||
        (validated.sharing !== undefined && !isDeepStrictEqual(current.sharing, validated.sharing))) {
      throw new Error('Read-back differs from the requested ordinary schema; registration is not an existing-app migration/update');
    }
    console.log(`Verified ordinary app ${validated.appId}; schemaVersion=${current.schemaVersion}.`);
    console.log(credentialsSaved ? `Credentials saved privately to ${outputPath}.` : 'Existing registration verified; no key reissued and no credentials file created.');
  } catch (error) {
    // Do not reflect server bodies, raw network errors or credentials in diagnostics.
    const message = error instanceof Error && !['TypeError', 'SyntaxError'].includes(error.name) ? error.message : 'Request or schema parsing failed';
    console.error(`[Provision] ${message}`);
    if (credentialsSaved) console.error('[Provision] Issued credentials were retained; inspect schema before retrying.');
    process.exitCode = 1;
  } finally {
    if (fd !== undefined) closeSync(fd);
    if (fd !== undefined && !credentialsSaved) unlinkSync(outputPath);
  }
}

