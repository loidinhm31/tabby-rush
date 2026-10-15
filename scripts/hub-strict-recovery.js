/**
 * scripts/hub-strict-recovery.js
 *
 * Maintenance-only recovery script for Doggo strict-v1 Hub persistence.
 * Designed for execution via:
 *   CONFIRM_MAINTENANCE_QUIESCENCE=true MONGO_URI="..." EXPECTED_OLD_EPOCH="..." SOURCE_BACKUP_COLLECTION="..." mongosh --nodb --file scripts/hub-strict-recovery.js
 * (Also executable via Node.js for CLI preflight and dry-run syntax checks).
 *
 * Strict Recovery Invariants:
 * 1. Maintenance Quiescence Assertion:
 *    - All traffic must be drained and writers quiesced. Requires CONFIRM_MAINTENANCE_QUIESCENCE=true.
 * 2. Fresh Epoch Allocation:
 *    - Generates a new external UUID v4 epoch (canonical lowercase). Never reuses or reopens a stale epoch.
 * 3. Target Collection Provisioning:
 *    - Uses canonical collection namespace matching backend convention:
 *      `app<AppId>` (e.g. `appDoggo`)
 *    - Creates exact scan index: { userId: 1, tableName: 1, rowId: 1 } with simple collation, not capped, no TTL.
 * 4. Sanitized Data Import:
 *    - Requires non-empty SOURCE_BACKUP_COLLECTION. Cannot publish an unseeded or empty epoch.
 *    - Source collection must not be the target namespace, active strict collection, or system collection.
 *    - Validates record structure: requires valid 24-hex userId, canonical UUIDv4 rowId, tableName: 'todos', and schema fields.
 *    - Whitelists todos fields: { id, title, completed, createdAt } and coerces createdAt & revision to BSON NumberLong.
 *    - Fail-closed: throws if NumberLong or Long constructor is unavailable.
 *    - Tombstones are imported as permanent empty tombstones (data: {}, deleted: true).
 * 5. Pre-CAS Validation:
 *    - Invokes the shipped read-only `strong_sync_admission` Rust preflight helper using
 *      protected environment variables (never passing credentials on argv or printing in logs).
 *    - Missing or failing admission helper blocks registry CAS unconditionally.
 * 6. Journaled Registry CAS on `_schemas`:
 *    - Performs atomic Compare-And-Swap on `_schemas` collection:
 *      { appId: 'doggo', strictEpoch: <EXPECTED_OLD_EPOCH>, syncMode: 'strict-v1' } -> { strictEpoch: <NEW_EPOCH> }.
 *    - Uses writeConcern: { w: 'majority', j: true }.
 * 7. Fail-Closed Safety:
 *    - Pre-CAS failure aborts and preserves the old epoch and old namespace intact.
 *    - Ambiguous CAS results maintain writer block for operator reconciliation without blind retries.
 *    - Never drops or purges the previous collection namespace.
 */

var isNode = typeof process !== 'undefined' && process.versions && process.versions.node;
var env = isNode ? process.env : {};

var CONFIG = {
  mongoUri: env.MONGO_URI || '',
  dbName: env.MONGO_DB_NAME || 'glean_hub',
  appId: env.APP_ID || 'doggo',
  expectedOldEpoch: env.EXPECTED_OLD_EPOCH || '',
  confirmQuiescence: env.CONFIRM_MAINTENANCE_QUIESCENCE === 'true',
  dryRun: env.DRY_RUN === 'true',
  sourceCollection: env.SOURCE_BACKUP_COLLECTION || '',
  admissionBin: env.STRONG_SYNC_ADMISSION_BIN || 'strong_sync_admission',
};

var HEX24_REGEX = /^[a-f0-9]{24}$/i;
var UUID_V4_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function generateUuidV4() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID().toLowerCase();
  }
  if (isNode) {
    var nodeCrypto = require('node:crypto');
    return nodeCrypto.randomUUID().toLowerCase();
  }
  if (typeof UUID === 'function') {
    return UUID().toString().toLowerCase();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random() * 16 | 0;
    var v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}
function toCamelCaseCollection(appId) {
  var parts = appId.split('-');
  var first = parts[0] || '';
  var rest = '';
  for (var p = 1; p < parts.length; p++) {
    var s = parts[p];
    if (s.length > 0) {
      rest += s.charAt(0).toUpperCase() + s.slice(1);
    }
  }
  var capFirst = first.length > 0 ? (first.charAt(0).toUpperCase() + first.slice(1)) : '';
  return 'app' + capFirst + rest;
}


function toBsonLong(v) {
  if (typeof NumberLong === 'function') return NumberLong(v);
  if (typeof Long === 'function') return Long(v);
  throw new Error('Fail-closed: BSON Int64 constructor (NumberLong or Long) is required to write revision and createdAt.');
}

function printBanner() {
  console.log('================================================================');
  console.log('Doggo Hub Strict-v1 Maintenance Recovery Script');
  console.log('Database:       ' + CONFIG.dbName);
  console.log('App ID:         ' + CONFIG.appId);
  console.log('Source Backup:  ' + (CONFIG.sourceCollection || '(None - required for live recovery)'));
  console.log('Expected Epoch: ' + (CONFIG.expectedOldEpoch || '(Not provided)'));
  console.log('Mode:           ' + (CONFIG.dryRun ? 'DRY-RUN (No mutations)' : 'LIVE MAINTENANCE RECOVERY'));
  console.log('================================================================\n');
}

function validatePrerequisites() {
  var errors = [];

  if (!CONFIG.confirmQuiescence) {
    errors.push('CONFIRM_MAINTENANCE_QUIESCENCE=true is required. All writer traffic must be halted before recovery.');
  }

  if (!CONFIG.mongoUri && !CONFIG.dryRun) {
    errors.push('MONGO_URI environment variable is required.');
  }

  if (!CONFIG.expectedOldEpoch && !CONFIG.dryRun) {
    errors.push('EXPECTED_OLD_EPOCH environment variable is required to perform journaled CAS.');
  }

  if (!CONFIG.sourceCollection && !CONFIG.dryRun) {
    errors.push('SOURCE_BACKUP_COLLECTION environment variable is required. Publishing an unseeded epoch is prohibited.');
  }

  if (CONFIG.sourceCollection) {
    if (CONFIG.sourceCollection.startsWith('app') ||
        CONFIG.sourceCollection === '_schemas' ||
        CONFIG.sourceCollection.startsWith('system.')) {
      errors.push("SOURCE_BACKUP_COLLECTION cannot be an active app collection ('" + CONFIG.sourceCollection + "'), internal, or system collection. Must be an isolated staging collection (e.g. 'staging_doggo_restore' or 'backup_doggo').");
    }
  }

  if (errors.length > 0) {
    console.error('[FATAL] Prerequisite validation failed:');
    for (var i = 0; i < errors.length; i++) {
      console.error('  - ' + errors[i]);
    }
    return false;
  }

  return true;
}

function validateTodoRecord(record) {
  if (!record || typeof record !== 'object') return false;
  if (typeof record.userId !== 'string' || !HEX24_REGEX.test(record.userId)) return false;
  if (typeof record.rowId !== 'string' || !UUID_V4_REGEX.test(record.rowId)) return false;
  if (record.tableName !== 'todos') return false;
  if (typeof record.deleted !== 'boolean') return false;

  if (record.deleted) {
    // Tombstone MUST have empty data
    if (record.data && Object.keys(record.data).length > 0) return false;
  } else {
    // Active record must validate schema columns
    var data = record.data;
    if (!data || typeof data !== 'object') return false;
    if (typeof data.id !== 'string' || data.id !== record.rowId) return false;
    if (typeof data.title !== 'string' || data.title.length === 0 || data.title.length > 200) return false;
    if (typeof data.completed !== 'boolean') return false;
    if (typeof data.createdAt === 'number') {
      if (!Number.isInteger(data.createdAt) || data.createdAt < 0 || data.createdAt > Number.MAX_SAFE_INTEGER) return false;
    } else if (data.createdAt && typeof data.createdAt.toNumber === 'function') {
      var n = data.createdAt.toNumber();
      if (!Number.isInteger(n) || n < 0 || n > Number.MAX_SAFE_INTEGER) return false;
    } else {
      return false;
    }
  }

  return true;
}

function executeAdmissionPreflight(appId, epoch) {
  if (!isNode) {
    throw new Error('Fail-closed: Admission preflight helper requires Node.js runtime.');
  }

  var childProcess = require('node:child_process');
  console.log('[Admission] Executing preflight helper: ' + CONFIG.admissionBin + ' --app-id ' + appId + ' --epoch ' + epoch);
  try {
    // Pass MONGO_URI and MONGO_DB_NAME in child environment only, NEVER on CLI argv or logged to console
    var output = childProcess.execFileSync(CONFIG.admissionBin, ['--app-id', appId, '--epoch', epoch], {
      encoding: 'utf8',
      env: Object.assign({}, process.env, {
        MONGO_URI: CONFIG.mongoUri,
        MONGO_DB_NAME: CONFIG.dbName
      })
    });
    console.log('[PASS] Admission preflight accepted: ' + output.trim());
  } catch (err) {
    throw new Error('Rust strong_sync_admission preflight helper failed or not found: ' + err.message + '. CAS publication BLOCKED.');
  }
}

function runRecoveryInDatabase(db) {
  console.log('[Step 1/6] Verifying writer quiescence and reading current app schema from _schemas...');
  var schemaDoc = db.getCollection('_schemas').findOne(
    { appId: CONFIG.appId },
    {},
    { readConcern: { level: 'majority' } }
  );

  if (!schemaDoc) {
    throw new Error("Application '" + CONFIG.appId + "' not found in _schemas registry.");
  }

  if (schemaDoc.syncMode !== 'strict-v1') {
    throw new Error("Application '" + CONFIG.appId + "' is not configured for 'strict-v1' syncMode (found: " + schemaDoc.syncMode + ").");
  }

  if (schemaDoc.strictEpoch !== CONFIG.expectedOldEpoch) {
    throw new Error("Registry epoch mismatch! Current: '" + schemaDoc.strictEpoch + "', Expected: '" + CONFIG.expectedOldEpoch + "'.");
  }

  console.log('[PASS] Verified current registry epoch in _schemas: ' + schemaDoc.strictEpoch);

  // Step 2: Allocate fresh epoch and staging collection
  var newEpoch = generateUuidV4();
  var cleanEpoch = newEpoch.replace(/-/g, '');
  var liveCollectionName = toCamelCaseCollection(CONFIG.appId);
  var stagingCollectionName = '_recovery_staging_' + CONFIG.appId + '_' + cleanEpoch;
  var backupCollectionName = '_recovery_backup_' + CONFIG.appId + '_' + cleanEpoch;
  console.log('\n[Step 2/6] Allocating fresh canonical epoch and isolated staging collection...');
  console.log('  New Epoch:          ' + newEpoch);
  console.log('  Live Collection:    ' + liveCollectionName);
  console.log('  Staging Collection: ' + stagingCollectionName);

  if (CONFIG.dryRun) {
    console.log('[DRY-RUN] Dry run active; skipping collection creation, document imports, and CAS.');
    return;
  }

  // Step 3: Create staging collection and required indexes matching validate_strict_admission
  console.log('\n[Step 3/6] Provisioning isolated staging collection and scan index...');
  db.createCollection(stagingCollectionName);
  var stagingCol = db.getCollection(stagingCollectionName);

  // Exact scan index matching backend: { userId: 1, tableName: 1, rowId: 1 }
  stagingCol.createIndex(
    { userId: 1, tableName: 1, rowId: 1 }
  );
  console.log('[PASS] Staging scan index provisioned successfully.');
  // Step 4: Import and sanitize source backup records
  console.log("\n[Step 4/6] Importing and sanitizing records from '" + CONFIG.sourceCollection + "'...");
  var importedCount = 0;
  var tombstoneCount = 0;

  var sourceCol = db.getCollection(CONFIG.sourceCollection);
  var totalSourceCount = sourceCol.countDocuments({});
  if (totalSourceCount === 0) {
    throw new Error("Source backup collection '" + CONFIG.sourceCollection + "' is empty. Aborting recovery.");
  }

  var cursor = sourceCol.find({});
  var batch = [];
  try {
    while (cursor.hasNext()) {
      var doc = cursor.next();
      if (!validateTodoRecord(doc)) {
        throw new Error("Record validation failed for rowId '" + (doc ? doc.rowId : 'unknown') + "'. Aborting recovery.");
      }

      var sanitizedData = {};
      if (!doc.deleted && doc.data) {
        var rawCreatedAt = typeof doc.data.createdAt === 'number' ? doc.data.createdAt : (doc.data.createdAt.toNumber ? doc.data.createdAt.toNumber() : doc.data.createdAt);
        sanitizedData = {
          id: doc.data.id,
          title: doc.data.title,
          completed: Boolean(doc.data.completed),
          createdAt: toBsonLong(rawCreatedAt)
        };
      }

      var sanitized = {
        _id: doc.userId + ':' + doc.rowId,
        userId: doc.userId,
        tableName: 'todos',
        rowId: doc.rowId,
        data: sanitizedData,
        revision: toBsonLong(1), // Explicit Int64 revision 1 for Hub compatibility
        deleted: doc.deleted,
        lastOp: null
      };

      if (doc.deleted) tombstoneCount++;
      else importedCount++;

      batch.push(sanitized);

      if (batch.length >= 500) {
        stagingCol.insertMany(batch, { writeConcern: { w: 'majority', j: true } });
        batch = [];
      }
    }

    if (batch.length > 0) {
      stagingCol.insertMany(batch, { writeConcern: { w: 'majority', j: true } });
    }
  } catch (importErr) {
    console.error('[ERROR] Import failed; cleaning up staging collection: ' + stagingCollectionName);
    try {
      stagingCol.drop();
    } catch (_) {}
    throw importErr;
  }

  var actualStagingCount = stagingCol.countDocuments({});
  if (actualStagingCount !== totalSourceCount) {
    try { stagingCol.drop(); } catch (_) {}
    throw new Error('Import count mismatch! Source count ' + totalSourceCount + ' != staging count ' + actualStagingCount);
  }

  console.log('[PASS] Imported ' + importedCount + ' active records and ' + tombstoneCount + ' permanent tombstones (' + totalSourceCount + ' total).');

  // Step 5: Promote staging collection to live and execute candidate preflight
  console.log('\n[Step 5/6] Promoting staging collection to live and running candidate preflight...');
  var collNames = db.getCollectionNames();
  var liveExists = collNames.indexOf(liveCollectionName) !== -1;
  var livePreserved = false;
  var stagingPromoted = false;

  try {
    if (liveExists) {
      console.log("  Preserving existing live collection to '" + backupCollectionName + "'...");
      db.getCollection(liveCollectionName).renameCollection(backupCollectionName);
      livePreserved = true;
    }
    db.getCollection(stagingCollectionName).renameCollection(liveCollectionName);
    stagingPromoted = true;

    executeAdmissionPreflight(CONFIG.appId, newEpoch);
  } catch (err) {
    console.error('[ERROR] Promotion or admission preflight failed: ' + err.message);
    if (stagingPromoted) {
      try {
        db.getCollection(liveCollectionName).renameCollection(stagingCollectionName + '_failed');
      } catch (e) {
        console.error('[WARN] Failed to rename failed live collection away: ' + e.message);
      }
    }
    if (livePreserved) {
      try {
        console.log("  Restoring previous live collection from '" + backupCollectionName + "'...");
        db.getCollection(backupCollectionName).renameCollection(liveCollectionName);
      } catch (e) {
        console.error('[CRITICAL] Failed to restore previous live collection: ' + e.message + '. Manual restoration required: rename ' + backupCollectionName + ' to ' + liveCollectionName);
      }
    }
    throw err;
  }
  // Step 6: Atomic Journaled Registry CAS on `_schemas`
  console.log('\n[Step 6/6] Executing atomic journaled registry CAS on _schemas collection...');
  var casResult;
  try {
    casResult = db.getCollection('_schemas').updateOne(
      {
        appId: CONFIG.appId,
        strictEpoch: CONFIG.expectedOldEpoch,
        syncMode: 'strict-v1'
      },
      {
        $set: {
          strictEpoch: newEpoch,
          recoveryTimestamp: new Date(),
          lastRecoveryStatus: 'fresh_epoch_promoted'
        }
      },
      {
        writeConcern: { w: 'majority', j: true }
      }
    );
  } catch (casErr) {
    console.error('\n[CRITICAL ERROR] Registry CAS threw an error or timed out: ' + casErr.message);
    console.error('Operator action: Ingress traffic must stay BLOCKED. Inspect _schemas on primary majority before proceeding.');
    throw casErr;
  }

  var modified = casResult.modifiedCount !== undefined ? casResult.modifiedCount : casResult.nModified;
  if (modified !== 1) {
    console.error('[CRITICAL ERROR] Registry CAS failed! modifiedCount=' + modified + '. Another operator or process may have updated _schemas.');
    console.error('Operator action: Traffic remains BLOCKED. Live data is at ' + liveCollectionName + ', previous data preserved at ' + backupCollectionName + '. Reconcile registry and storage before resuming.');
    throw new Error(
      'Registry CAS failed! modifiedCount=' + modified + '. Stale old epoch or concurrent modification. Traffic remains BLOCKED.'
    );
  }

  console.log('\n================================================================');
  console.log('[SUCCESS] Strict-v1 Recovery Completed Successfully.');
  console.log('Active Epoch Promoted: ' + newEpoch);
  if (liveExists) {
    console.log('Previous live collection preserved as: ' + backupCollectionName);
  }
  console.log('Mandatory next steps before unblocking traffic:');
  console.log('  1. Re-run strong_sync_admission against active epoch.');
  console.log('  2. Verify Hub strict bootstrap returns HTTP 200 with new epoch.');
  console.log('  3. Verify old-epoch client request returns HTTP 409 (epochMismatch).');
  console.log('================================================================');
}

function main() {
  printBanner();

  if (!validatePrerequisites()) {
    if (isNode) process.exit(1);
    throw new Error('Prerequisite validation failed.');
  }

  if (CONFIG.dryRun) {
    console.log('[PASS] Prerequisites and configuration syntax verified cleanly.');
    if (isNode) process.exit(0);
    return;
  }

  // Connect to database in mongosh or fail closed in plain Node
  var database = null;
  try {
    if (typeof connect === 'function') {
      console.log("[mongosh] Connecting to database '" + CONFIG.dbName + "'...");
      database = connect(CONFIG.mongoUri).getSiblingDB(CONFIG.dbName);
    } else {
      console.error('[FATAL] Live recovery execution requires mongosh shell environment.');
      console.error('To execute live recovery against MongoDB cluster, run:');
      console.error('  CONFIRM_MAINTENANCE_QUIESCENCE=true MONGO_URI="..." EXPECTED_OLD_EPOCH="..." SOURCE_BACKUP_COLLECTION="..." mongosh --nodb --file scripts/hub-strict-recovery.js');
      if (isNode) process.exit(1);
      throw new Error('mongosh environment required.');
    }
  } catch (err) {
    console.error('[FATAL] Connection initialization failed: ' + err.message);
    if (isNode) process.exit(1);
    throw err;
  }

  runRecoveryInDatabase(database);
}

try {
  main();
} catch (err) {
  console.error('\n[FATAL ERROR] Recovery aborted: ' + err.message);
  if (isNode) process.exit(1);
  throw err;
}
