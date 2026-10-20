import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {sensitiveFile, readHealth, checkBackendHealth} from './runtime-checks.mjs';

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
