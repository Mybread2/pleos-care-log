import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export async function prepareTeamFixture() {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const directory = path.join(root, 'tests/fixtures/team-b04');
  const manifest = JSON.parse(await readFile(path.join(directory, 'source.json'), 'utf8'));
  const file = 'F01_ice_basic_refuel.json';
  const bytes = await readFile(path.join(directory, file));
  if (createHash('sha256').update(bytes).digest('hex') !== manifest.files[file]) throw new Error('Team F01 source digest mismatch');
  const fixture = JSON.parse(bytes.toString('utf8'));
  // Expected values never enter the application: only actual source events do.
  const { schemaVersion, environment, vehicleId, powertrain, events } = fixture;
  const runtime = { schemaVersion, environment, vehicleId, powertrain, events };
  const source = { fixtureId: fixture.fixtureId, repository: manifest.repository, commit: manifest.commit, file: `${manifest.directory}/${file}` };
  await writeFile(path.join(root, 'src/mock/teamFixture.js'),
    `// Generated from the pinned team B04 F01 source by npm run build.\nexport const TEAM_ICE_FIXTURE_V02 = ${JSON.stringify(runtime, null, 2)};\n\nexport const TEAM_FIXTURE_SOURCE = ${JSON.stringify(source, null, 2)};\n`);
}
