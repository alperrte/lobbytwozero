import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {sensitiveFile, readHealth, checkBackendHealth, backendTestEnvironment} from './runtime-checks.mjs';

function databaseModel() {
  return { services: {
    postgres: { ports: [{ target: 5432, published: '5542', host_ip: '127.0.0.1' }],
      environment: { POSTGRES_USER: 'fixture-user', POSTGRES_PASSWORD: 'fixture-db-pass' } },
    'game-service': { environment: { DB_GAME_NAME: 'game_db', STEAM_WEB_API_KEY: 'do-not-copy' } }
  } };
}

test('host Maven uses selected database credentials and published port without changing the Docker environment', () => {
  const ambient = { POSTGRES_HOST: 'postgres', POSTGRES_PORT: '5432', POSTGRES_PASSWORD: 'other-profile', KEEP: 'value' };
  const env = backendTestEnvironment(databaseModel(), ambient);
  assert.equal(env.POSTGRES_HOST, '127.0.0.1');
  assert.equal(env.POSTGRES_PORT, '5542');
  assert.equal(env.POSTGRES_PASSWORD, 'fixture-db-pass');
  assert.equal(env.DB_GAME_NAME, 'game_db');
  assert.equal(env.STEAM_WEB_API_KEY, undefined);
  assert.equal(env.KEEP, 'value');
  assert.equal(ambient.POSTGRES_PORT, '5432');
  assert.equal(ambient.POSTGRES_PASSWORD, 'other-profile');
});

test('host database resolution rejects external, missing or invalid published ports', () => {
  for (const values of [{ host_ip: '0.0.0.0' }, { host_ip: '192.0.2.1' }, { published: '0' }, { published: '65536' }]) {
    const model = databaseModel();
    Object.assign(model.services.postgres.ports[0], values);
    assert.throws(() => backendTestEnvironment(model), /127.0.0.1/);
  }
  assert.throws(() => backendTestEnvironment({ services: {} }), /127.0.0.1/);
});

test('missing credentials and inconsistent database names fail without exposing values', () => {
  const model = databaseModel();
  delete model.services.postgres.environment.POSTGRES_PASSWORD;
  assert.throws(() => backendTestEnvironment(model), /missing PostgreSQL credentials/);
  model.services.postgres.environment.POSTGRES_PASSWORD = 'fixture-db-pass';
  model.services.other = { environment: { DB_GAME_NAME: 'conflicting' } };
  assert.throws(() => backendTestEnvironment(model), /inconsistent database names/);
});

test('secret paths cover nested profiles, backups, keys and credentials but allow example templates', () => {
  for(const filename of ['.env','frontend/.env.local','nested/.env.prod','.env.bak','nested/key.pem','secret.key','user.p12','user.pfx','user.jks','user.keystore','.aws/credentials','foo/secrets/data','credentials.json','service-account.json']) assert.equal(sensitiveFile(filename),true,filename);
  for(const filename of ['.env.example','frontend/.env.local.example','.env.prod.example','file.ts','next-env.d.ts','some.env']) assert.equal(sensitiveFile(filename),false,filename);
});

async function serverTest(action) {
  const server=http.createServer(action.handler);
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const url=`http://127.0.0.1:${server.address().port}/actuator/health`;
  try {await action.check(url);} finally {server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
}
test('HTTP health requires both HTTP 200 and JSON UP', async () => {
  for(const [code,body,success] of [[200,'{"status":"UP"}',true],[200,'{"status":"DOWN"}',false],[503,'{"status":"UP"}',false],[200,'<html>oops</html>',false],[302,'',false]]) {
    await serverTest({handler:(_,res)=>{res.writeHead(code);res.end(body);},check:async url=>success?assert.equal(await readHealth(url),true):assert.rejects(readHealth(url))});
  }
});
test('HTTP requests time out and cannot send requests outside loopback', async () => {
  await serverTest({handler:()=>{},check:async url=>assert.rejects(readHealth(url,20),/timed out/)});
  await assert.rejects(readHealth('http://example.com'),/loopback/);
});

test('backend health reports all services and rejects stopped or different-project containers', async () => {
  const modules=['services/auth-service','services/api-gateway','services/user-service'];
  const config={name:'fixture',services:Object.fromEntries(modules.map(module=>[module.split('/')[1],{ports:[{target:8080,published:'5081'}],environment:{BACKEND_INTERNAL_PORT:'8080'}}]))};
  const containers=[{Service:'auth-service',State:'running',Project:'fixture'},{Service:'api-gateway',State:'running',Project:'other'},{Service:'user-service',State:'exited',Project:'fixture'}];
  let requests=0;const lines=[];
  const run=async(_,args)=>({code:0,output:JSON.stringify(args.includes('config')?config:containers)});
  const report=await checkBackendHealth({run,profile:'dev',modules,probe:async url=>{requests++;assert.match(url,/:5081\/actuator\/health$/);},log:line=>lines.push(line)});
  assert.equal(report.passed,false);assert.equal(requests,1);assert.equal(report.services.length,3);assert.equal(lines.length,3);assert.equal(report.services[0].status,'PASS');
});
test('HTTP failure does not prevent another service health request or report', async () => {
  const modules=['services/auth-service','services/api-gateway'];
  const model={name:'fixture',services:Object.fromEntries(modules.map((m,i)=>[m.split('/')[1],{ports:[{target:8080,published:String(5081+i)}],environment:{}}]))};
  const containers=modules.map(m=>({Service:m.split('/')[1],State:'running',Project:'fixture'}));
  const result=await checkBackendHealth({run:async(_,args)=>({code:0,output:JSON.stringify(args.includes('config')?model:containers)}),profile:'dev',modules,log:()=>{},timeoutMs:0,probe:async url=>{if(url.includes(':5081/'))throw Error('HTTP 503');}});
  assert.deepEqual(result.services.map(x=>x.status),['FAIL','PASS']);
});
