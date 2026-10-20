#!/usr/bin/env node
/** Register an ordinary Hub app; never convert an existing strict registration. */
import { openSync, writeFileSync, fsyncSync, closeSync, unlinkSync, readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
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
  const input = JSON.parse(readFileSync(resolve(values.schema), 'utf8'));
  if (!input.appId || input.appId !== values['confirm-app'] || !input.schema || typeof input.schema !== 'object' || Array.isArray(input.schema)) {
    throw new Error('Schema must contain appId/schema and --confirm-app must match');
  }
  if ('syncMode' in input || 'strictEpoch' in input || 'schemaVersion' in input) {
    throw new Error('Use an ordinary schema envelope without strict metadata or a client-assigned schemaVersion');
  }
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
  const payload = { appId: input.appId, schema: input.schema, ...(input.sharing === undefined ? {} : { sharing: input.sharing }) };
  const registration = await request('POST', '/api/v1/apps', payload);
  if (registration.ok) {
    const issued = await registration.json();
    if (issued.appId !== input.appId || typeof issued.apiKey !== 'string' || !issued.apiKey) throw new Error('Registration returned invalid credentials');
    // Persist the issued key before any subsequent request, including failed read-back.
    writeFileSync(fd, JSON.stringify({ appId: issued.appId, apiKey: issued.apiKey, schemaVersion: issued.schemaVersion }, null, 2) + '\n');
    credentialsSaved = true;
    fsyncSync(fd);
  } else if (registration.status !== 409) {
    throw new Error(`Registration rejected (HTTP ${registration.status}); no update attempted`);
  }
  const response = await request('GET', `/api/v1/apps/${encodeURIComponent(input.appId)}`);
  if (!response.ok) throw new Error(`Schema read-back failed (HTTP ${response.status})`);
  const current = await response.json();
  if (current.appId !== input.appId || current.syncMode != null || current.strictEpoch != null ||
      !Number.isSafeInteger(current.schemaVersion) || current.schemaVersion < 1 ||
      !isDeepStrictEqual(current.schema, input.schema) ||
      (input.sharing !== undefined && !isDeepStrictEqual(current.sharing, input.sharing))) {
    throw new Error('Read-back differs from the requested ordinary schema; registration is not an existing-app migration/update');
  }
  console.log(`Verified ordinary app ${input.appId}; schemaVersion=${current.schemaVersion}.`);
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
