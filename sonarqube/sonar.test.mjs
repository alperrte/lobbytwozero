import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { loadSonarEnv, validateSonarHost, runScan, runSonar, overallTargets, sonarRequest } from './sonar.mjs';
import { loadBackendTestEnv } from '../lib/profile-env.mjs';
import http from 'node:http';

const conditions = [
  { metric: 'new_coverage', op: 'LT', error: '80' },
  { metric: 'new_duplicated_lines_density', op: 'GT', error: '3' },
  { metric: 'new_violations', op: 'GT', error: '0' },
];

const goodMetrics = {
  coverage: 85,
  duplicated_lines_density: 2,
  security_rating: 1,
  reliability_rating: 1,
  sqale_rating: 1,
};

async function scratch(action) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-sonar-'));

  const write = (name, content) => {
    const filename = path.join(root, name);
    fs.mkdirSync(path.dirname(filename), { recursive: true });
    fs.writeFileSync(filename, content);
  };

  write(
    '.env.dev',
    'SONAR_TOKEN=local-token\nSONAR_DB_PASSWORD=local-password\nPOSTGRES_USER=fixture-user\nPOSTGRES_PASSWORD=fixture-password\nPOSTGRES_HOST=postgres\nPOSTGRES_PORT=5432\nPOSTGRES_HOST_PORT=5542\nDB_AUTH_NAME=fixture_auth\n',
  );
  write('backend/pom.xml', '<module>services/auth-service</module>');

  try {
    await action(root, write);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}

test('explicit profile works without active .env and ambient variables take precedence', async () => {
  await scratch(async (root, write) => {
    write('.env', 'SONAR_TOKEN=wrong-profile\n');

    assert.equal(loadSonarEnv(root, 'dev', {}).SONAR_TOKEN, 'local-token');
    assert.equal(loadSonarEnv(root, 'dev', { SONAR_TOKEN: 'shell' }).SONAR_TOKEN, 'shell');
    assert.throws(() => loadSonarEnv(root, '../prod', {}));
    assert.throws(() => loadSonarEnv(root, 'prod', {}), /Missing/);

    write(
      '.env.dev',
      'SONAR_TOKEN="quoted-value"\nSONAR_BACKEND_PROJECT_KEY=ltz-backend # comment\nPATH=untrusted-path\nNODE_OPTIONS=untrusted-option\n',
    );

    assert.equal(loadSonarEnv(root, 'dev', {}).SONAR_TOKEN, 'quoted-value');
    assert.equal(loadSonarEnv(root, 'dev', {}).SONAR_BACKEND_PROJECT_KEY, 'ltz-backend');
    assert.equal(loadSonarEnv(root, 'dev', {}).PATH, undefined);
    assert.equal(loadSonarEnv(root, 'dev', {}).NODE_OPTIONS, undefined);
  });
});

test('Sonar host validation rejects remote servers, credentials, paths and redirects before scanner execution', () => {
  for (const host of [
    'http://example.com',
    'http://token@localhost:5199',
    'http://localhost:5199/path',
    'file:///tmp/x',
    'http://localhost:5199?token=x',
  ]) {
    assert.throws(() => validateSonarHost(host));
  }

  for (const host of [
    'http://localhost:5199',
    'http://127.0.0.1:5199',
    'http://[::1]:5199',
  ]) {
    assert.ok(validateSonarHost(host));
  }
});

test('Maven test environment loads only selected profile DB keys and maps container addresses to host', async () => {
  await scratch(async (root, write) => {
    write('.env', 'POSTGRES_PASSWORD=wrong-active-profile\n');

    fs.appendFileSync(
      path.join(root, '.env.dev'),
      'PATH=untrusted\nNODE_OPTIONS=untrusted\nOTHER_SECRET=ignored\n',
    );

    const env = loadBackendTestEnv(root, 'dev', {});

    assert.equal(env.POSTGRES_USER, 'fixture-user');
    assert.equal(env.POSTGRES_PASSWORD, 'fixture-password');
    assert.equal(env.POSTGRES_HOST, '127.0.0.1');
    assert.equal(env.POSTGRES_PORT, '5542');
    assert.equal(env.DB_AUTH_NAME, 'fixture_auth');

    for (const key of ['PATH', 'NODE_OPTIONS', 'OTHER_SECRET', 'SONAR_TOKEN']) {
      assert.equal(env[key], undefined);
    }

    assert.equal(loadSonarEnv(root, 'dev', {}).POSTGRES_PASSWORD, undefined);

    const overridden = loadBackendTestEnv(root, 'dev', {
      POSTGRES_HOST: 'localhost',
      POSTGRES_PORT: '5555',
      POSTGRES_PASSWORD: 'terminal-password',
      DB_AUTH_NAME: 'terminal_auth',
      PATH: 'trusted-terminal-path',
    });

    assert.equal(overridden.POSTGRES_HOST, 'localhost');
    assert.equal(overridden.POSTGRES_PORT, '5555');
    assert.equal(overridden.POSTGRES_PASSWORD, 'terminal-password');
    assert.equal(overridden.DB_AUTH_NAME, 'terminal_auth');
    assert.equal(overridden.PATH, 'trusted-terminal-path');

    write(
      '.env.prod',
      'POSTGRES_USER=prod-fixture\nPOSTGRES_PASSWORD=prod-fixture-password\nPOSTGRES_HOST_PORT=5556\n',
    );

    assert.equal(loadBackendTestEnv(root, 'prod', {}).POSTGRES_PORT, '5556');
    assert.equal(loadBackendTestEnv(root, 'dev', {}).POSTGRES_PORT, '5542');
  });
});

test('Maven test environment rejects missing credentials, invalid ports and arbitrary profile names without leaking values', async () => {
  await scratch(async (root, write) => {
    assert.throws(
      () => loadBackendTestEnv(root, '../dev', {}),
      /Profile must/,
    );

    for (const port of ['0', '65536', 'invalid-sensitive-value']) {
      assert.throws(
        () => loadBackendTestEnv(root, 'dev', { POSTGRES_PORT: port }),
        error =>
          /between 1 and 65535/.test(error.message) &&
          !error.message.includes(port),
      );
    }

    write('.env.dev', 'POSTGRES_USER=fixture-user\n');

    assert.throws(
      () => loadBackendTestEnv(root, 'dev', {}),
      /require POSTGRES_USER and POSTGRES_PASSWORD/,
    );
  });
});

for (const option of [
  'success',
  'gateFail',
  'lowCoverage',
  'scannerFail',
  'missingReports',
  'serverDown',
  'remoteHost',
  'invalidToken',
  'policyForbidden',
]) {
  test(`backend scan ${option} checks current reports, analysis gate and overall targets`, async () => {
    await scratch(async (root, write) => {
      const lines = [];
      const calls = [];

      const runCommand = async (command, args, settings) => {
        calls.push({
          command,
          args,
          env: settings.env,
          secrets: settings.secrets,
        });

        if (command === 'docker') {
          assert.equal(settings.capture, true);
          assert.ok(args.includes('config'));
          assert.ok(args.includes('.env.dev'));

          return {
            code: 0,
            output: JSON.stringify({
              services: {
                postgres: {
                  ports: [
                    {
                      target: 5432,
                      published: '5542',
                      host_ip: '127.0.0.1',
                    },
                  ],
                  environment: {
                    POSTGRES_USER: 'fixture-user',
                    POSTGRES_PASSWORD: 'fixture-db-pass',
                  },
                },
                'auth-service': {
                  environment: {
                    DB_AUTH_NAME: 'auth_db',
                  },
                },
              },
            }),
          };
        }

        assert.match(command, /^mvn(\.cmd)?$/);
        assert.equal(settings.env.SONAR_HOST_URL, 'http://localhost:5199');
        assert.ok(!args.some(arg => arg.includes('local-token')));
        assert.ok(!args.some(arg => arg.includes('fixture-password')));
        assert.ok(!args.some(arg => arg.includes('fixture-db-pass')));

        if (args.includes('-version') || args.includes('verify')) {
          assert.equal(settings.env.POSTGRES_HOST, '127.0.0.1');
          assert.equal(settings.env.POSTGRES_PORT, '5542');
          assert.equal(settings.env.POSTGRES_PASSWORD, 'fixture-db-pass');
          assert.equal(settings.env.DB_AUTH_NAME, 'auth_db');
          assert.ok(settings.secrets.includes('fixture-db-pass'));

          settings.log('database password: fixture-db-pass');
        } else {
          assert.equal(settings.env.POSTGRES_PASSWORD, undefined);
        }

        if (args.includes('-version')) {
          return {
            code: 0,
            output: 'Java version: 25.0.4',
          };
        }

        if (args.includes('verify')) {
          if (option !== 'missingReports') {
            const base = 'backend/services/auth-service/target';

            write(
              `${base}/surefire-reports/TEST-app.xml`,
              '<testsuite tests="1" failures="0" errors="0" skipped="0"/>',
            );
            write(
              `${base}/site/jacoco/jacoco.xml`,
              '<report><counter type="LINE" missed="0" covered="10"/></report>',
            );
            write(`${base}/site/jacoco/index.html`, 'report');
            write(`${base}/classes/App.class`, 'bytecode');
          }

          return {
            code: 0,
            output: '',
          };
        }

        write('backend/target/sonar/report-task.txt', 'ceTaskId=current-task\n');
        settings.log('token: local-token');

        return {
          code: option === 'scannerFail' ? 1 : 0,
          output: '',
        };
      };

      const request = async (_, endpoint) => {
        if (endpoint.includes('/system/status')) {
          return {
            status: option === 'serverDown' ? 'DOWN' : 'UP',
          };
        }

        if (endpoint.includes('/authentication/validate')) {
          return {
            valid: option !== 'invalidToken',
          };
        }

        if (endpoint.includes('/qualitygates/show')) {
          return { conditions };
        }

        if (endpoint.includes('/metrics/search')) {
          return {
            metrics: Object.keys(goodMetrics).map(key => ({ key })),
          };
        }

        if (endpoint.includes('/ce/task')) {
          assert.ok(endpoint.includes('current-task'));

          return {
            task: {
              status: 'SUCCESS',
              analysisId: 'current-analysis',
            },
          };
        }

        if (endpoint.includes('/project_status')) {
          assert.ok(endpoint.includes('current-analysis'));

          return {
            projectStatus: {
              status: option === 'gateFail' ? 'ERROR' : 'OK',
            },
          };
        }

        if (endpoint.includes('/measures/component')) {
          const metrics = {
            ...goodMetrics,
            ...(option === 'lowCoverage' ? { coverage: 50 } : {}),
          };

          return {
            component: {
              measures: Object.entries(metrics).map(([metric, value]) => ({
                metric,
                value: String(value),
              })),
            },
          };
        }

        if (endpoint.includes('/get_by_project')) {
          if (option === 'policyForbidden') {
            throw Object.assign(new Error('Forbidden'), {
              status: 403,
            });
          }

          return {
            qualityGate: {
              name: 'Sonar way',
            },
          };
        }

        throw new Error('Unexpected API ' + endpoint);
      };

      const result = await runScan({
        root,
        ambient:
          option === 'remoteHost'
            ? { SONAR_HOST_URL: 'http://example.com' }
            : {},
        runCommand,
        request,
        log: line => lines.push(line),
      });

      assert.equal(
        result.exitCode,
        ['success', 'policyForbidden'].includes(option) ? 0 : 1,
      );

      const output = lines.join('\n');

      assert.ok(!output.includes('local-token'));
      assert.ok(!output.includes('fixture-password'));
      assert.ok(!output.includes('fixture-db-pass'));

      if (['serverDown', 'remoteHost', 'invalidToken'].includes(option)) {
        assert.equal(calls.length, 0);
      }

      if (option === 'missingReports') {
        assert.ok(!calls.some(call => call.args.includes('sonar:sonar')));
      }

      if (option === 'policyForbidden') {
        assert.equal(result.details.gatePolicy.status, 'NOT VERIFIED');
      }
    });
  });
}

test('missing metrics never satisfy overall targets', () => {
  assert.equal(overallTargets([]), false);

  assert.equal(
    overallTargets(
      Object.entries(goodMetrics).map(([metric, value]) => ({
        metric,
        value,
      })),
    ),
    true,
  );
});

test('unreachable local SonarQube gives an actionable error without exposing the token', async () => {
  const server = http.createServer();

  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));

  const port = server.address().port;

  await new Promise(resolve => server.close(resolve));

  await assert.rejects(
    sonarRequest(
      `http://127.0.0.1:${port}`,
      '/api/system/status',
      'synthetic-token',
    ),
    error => {
      assert.match(error.message, /SonarQube bağlantısı kurulamadı/);
      assert.match(error.message, /sonar.cmd up/);
      assert.ok(!error.message.includes('synthetic-token'));
      return true;
    },
  );
});

test('Sonar down only stops the isolated project and preserves volumes; status does not need a token or application DB credentials', async () => {
  await scratch(async root => {
    const calls = [];

    const runCommand = async (command, args, settings) => {
      calls.push({ command, args });
      assert.equal(settings.env.POSTGRES_PASSWORD, undefined);

      return {
        code: 0,
        output: '',
      };
    };

    assert.equal(
      (
        await runSonar({
          root,
          action: 'down',
          ambient: {},
          runCommand,
          log: () => {},
        })
      ).exitCode,
      0,
    );

    assert.ok(calls[0].args.includes('ltz-sonar'));
    assert.ok(calls[0].args.includes('stop'));
    assert.ok(!calls[0].args.includes('--volumes'));
    assert.ok(!calls[0].args.includes('.env'));

    assert.equal(
      (
        await runSonar({
          root,
          action: 'status',
          ambient: { SONAR_TOKEN: '' },
          runCommand,
          request: async () => ({ status: 'UP' }),
          log: () => {},
        })
      ).exitCode,
      0,
    );
  });
});