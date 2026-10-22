import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { evaluateModule, validatePolicy, gitContext, xmlMetadata, sourceInventory, prepareCoverage, enforceCoverage, ownerApprovedPolicy, javaTokens, mavenBuildStart } from './coverage.mjs';
import { selectScope } from './changes.mjs';
const hash = text => createHash('sha256').update(text).digest('hex');
const module = 'services/auth-service';
const name = 'backend/' + module + '/src/main/java/p/App.java';
function fixture(action) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-coverage-'));
  const write = (relative, text) => {
    const file = path.join(root, relative); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, text); return file;
  };
  const text = 'package p;\nclass App {\nint a() {\nreturn 1;\n}\nint b() {\nreturn 2;\n}\n}\n';
  write(name, text);
  write('backend/pom.xml', '<project><modules><module>' + module + '</module></modules></project>');
  write('backend/' + module + '/target/classes/p/App.class', 'synthetic bytecode inventory');
  const policy = {version:1,minimumPercent:80,modules:{[module]:{level:'bootstrap',baseline:{covered:1,missed:2,tests:1},sources:sourceInventory(root,module)}}};
  const context = {changes:{},previous:structuredClone(policy)};
  function metadata(covered = 1, missed = 2) {
    return {name:'auth-service',counter:{covered,missed},sources:[{path:'p/App.java',counter:{covered,missed},
      lines:Array.from({length:covered+missed},(_,i)=>({nr:i+1,ci:i<covered?1:0,mi:i<covered?0:1,cb:0,mb:0}))}],classes:[{name:'p/App',source:'App.java'}],tests:1,failures:0,errors:0,skipped:0};
  }
  const evaluate = data => evaluateModule({root,module,policy,context,metadata:data??metadata()});
  const changed = lines => {
    context.changes[name]={lines,semantic:true,deleted:false,sourceHash:hash(fs.readFileSync(path.join(root,name)))};
  };
  try { return action({root,write,text,policy,context,metadata,evaluate,changed}); }
  finally {
    if(path.dirname(root)!==os.tmpdir() || !path.basename(root).startsWith('ltz-coverage-')) throw Error('Unsafe fixture cleanup');
    fs.rmSync(root,{recursive:true,force:true});
  }
}
test('unchanged verified bootstrap uses real baseline and reports NOT_APPLICABLE, never fake100',()=>fixture(f=>{
  const result=f.evaluate(); assert.equal(result.baseline,'PASS'); assert.equal(result.changed.status,'NOT_APPLICABLE'); assert.equal(result.changed.percent,null);
}));
test('new Java code with no covered lines FAILS',()=>fixture(f=>{
  f.changed([2,3]); assert.throws(()=>f.evaluate(),/changed-line/);
}));
test('new executable lines at80percent PASS',()=>fixture(f=>{
  f.changed([1,2,3,4,6]); const result=f.evaluate(f.metadata(5,1)); assert.equal(result.changed.percent,80);
}));
test('new executable lines below80percent FAIL',()=>fixture(f=>{
  f.changed([1,2,6]); assert.throws(()=>f.evaluate(f.metadata(5,1)),/changed-line/);
}));
test('strict module below80 FAILS',()=>fixture(f=>{
  f.policy.modules[module].level='strict'; assert.throws(()=>f.evaluate(),/strict/);
}));
test('baseline regression FAILS',()=>fixture(f=>{
  assert.throws(()=>f.evaluate(f.metadata(0,3)),/baseline/);
}));
test('denominator reduction FAILS',()=>fixture(f=>{
  assert.throws(()=>f.evaluate(f.metadata(1,1)),/denominator/);
}));
test('test-only regressions cannot consume additional-code coverage budget',()=>fixture(f=>{
  assert.throws(()=>f.evaluate(f.metadata(3,3)),/cumulative/);
}));
test('zero and skipped tests FAIL',()=>fixture(f=>{
  for(const change of [{tests:0},{skipped:1},{errors:1},{failures:1}])assert.throws(()=>f.evaluate({...f.metadata(),...change}),/tests/);
}));
test('wrong service XML and missing compiled classes FAIL',()=>fixture(f=>{
  assert.throws(()=>f.evaluate({...f.metadata(),name:'other-service'}),/mapping/);
  assert.throws(()=>f.evaluate({...f.metadata(),classes:[]}),/compiled/);
}));
test('sourcefile collisions, missing Java source metadata and counter mismatch FAIL',()=>fixture(f=>{
  const m=f.metadata();m.sources[0].path='q/App.java';assert.throws(()=>f.evaluate(m),/mapping/);
  const other=f.metadata();other.sources[0].lines[0].ci=0;assert.throws(()=>f.evaluate(other),/inconsistent/);
}));
test('new source unmeasured by JaCoCo FAILS',()=>fixture(f=>{
  f.write('backend/'+module+'/src/main/java/p/NewLogic.java','package p;class NewLogic{}');
  assert.throws(()=>f.evaluate(),/absent/);
}));
test('semantic source changes with no executable line record FAIL',()=>fixture(f=>{
  f.changed([9]);assert.throws(()=>f.evaluate(),/measurable/);
}));
test('comment-only and deleted-file changes record why coverage is NOT_APPLICABLE',()=>fixture(f=>{
  f.changed([9]);f.context.changes[name].semantic=false;
  assert.match(f.evaluate().changed.reason,/comments/);
  delete f.context.changes[name];
  f.context.changes['backend/'+module+'/src/main/java/p/Deleted.java']={lines:[],semantic:true,deleted:true,sourceHash:hash('')};
  assert.match(f.evaluate().changed.reason,/deleted/);
}));
test('rename is remeasured as delete+addition and cannot inherit another service report',()=>fixture(f=>{
  f.context.changes['backend/services/other-service/src/main/java/p/App.java']={lines:[],semantic:true,deleted:true,sourceHash:hash('')};
  f.changed([1,2,3]);assert.throws(()=>f.evaluate(),/changed-line/);
}));
test('negative/fractional/duplicate counters and unsafe diff paths FAIL',()=>fixture(f=>{
  for(const count of [-1,0.5])assert.throws(()=>f.evaluate({...f.metadata(),counter:{covered:count,missed:3-count}}),/counter/);
  const m=f.metadata();m.sources[0].lines.push(m.sources[0].lines[0]);assert.throws(()=>f.evaluate(m),/duplicate/);
  f.context.changes['backend/'+module+'/src/main/java/../../.env']={};assert.throws(()=>f.evaluate(),/diff context/);
}));
test('even comment changes after context creation invalidate report/source freshness',()=>fixture(f=>{
  f.context.inventory={[module]:sourceInventory(f.root,module,true)};
  f.write(name,'// inserted after preparation\n'+f.text);
  assert.throws(()=>f.evaluate(),/sources changed/);
}));
test('Maven and all runners retain central enforcement and instrumentation without a hidden old gate',()=>{
  const read=relative=>fs.readFileSync(new URL('../../'+relative,import.meta.url),'utf8');
  const pom=read('backend/pom.xml');
  assert.ok(pom.includes('<goal>prepare-agent</goal>')&&pom.includes('<goal>report</goal>'));
  assert.ok(!pom.includes('<id>coverage-context</id>')&&!pom.includes('<argument>maven-init</argument>'));
  assert.ok(pom.includes('<id>coverage-policy</id><phase>verify</phase>'));
  assert.ok(pom.includes('<argument>${maven.build.timestamp}</argument>'));
  // Existing Dockerfiles COPY only backend/ and use package. Coverage's Node/Git
  // prerequisites must therefore never be attached to initialize/package.
  const dockerfile=read('backend/services/auth-service/Dockerfile');
  assert.ok(dockerfile.includes('COPY backend/ ./backend/')&&dockerfile.includes('-DskipTests package'));
  assert.ok(!pom.includes('<id>coverage-check</id>')&&!pom.includes('<jacoco.skip>true'));
  for(const file of ['pre-push/backend-check.mjs','pre-push/quality.mjs','pre-push/ci/backend.mjs']){
    const code=read(file);assert.ok(code.includes('prepareCoverage')&&code.includes('coverageCheck('));
    assert.ok(!/lineCoverage\s*(?:>=|<)\s*80/.test(code));
  }
});
test('direct Maven verify rejects skip flags and uses session start instead of post-test time',()=>{
  const stamp=new Date(Date.now()-60000).toISOString();
  assert.equal(mavenBuildStart(stamp,['false','false','false','false']),Date.parse(stamp));
  for(let index=0;index<4;index++){
    const flags=['false','false','false','false'];flags[index]='true';assert.throws(()=>mavenBuildStart(stamp,flags),/cannot skip/);
  }
  assert.throws(()=>mavenBuildStart('unresolved-property',[]),/invalid/);
});
test('Git errors/missing merge-base cannot produce success',()=>{
  assert.throws(()=>gitContext('.', {run:()=>({status:1,stdout:''})}),/Git/);
  assert.throws(()=>gitContext('.', {run:(_cmd,args)=>({status:0,stdout:args.includes('merge-base')?'':'a'.repeat(40)})}),/merge-base/);
});
test('Java lexer preserves string content but ignores comments and whitespace',()=>{
  assert.deepEqual(javaTokens('x = "//not comment"; // comment'),javaTokens('x="//not comment";'));
  assert.notDeepEqual(javaTokens('return "a";'),javaTokens('return "b";'));
  assert.throws(()=>javaTokens('// '+String.raw`\u000a`+' return 42;'),/Unicode escapes/);
});
test('manifest registration, immutable debt, strict downgrade and lowering threshold FAIL',()=>fixture(f=>{
  assert.throws(()=>validatePolicy(f.policy,[module,'services/new-service'],f.policy),/modules/);
  const old=structuredClone(f.policy);old.modules[module].level='strict';assert.throws(()=>validatePolicy(f.policy,[module],old),/downgrade/);
  const changed=structuredClone(f.policy);changed.modules[module].baseline.missed=100;assert.throws(()=>validatePolicy(changed,[module],f.policy),/immutable/);
  changed.minimumPercent=33;assert.throws(()=>validatePolicy(changed,[module],f.policy),/threshold/);
  const added=structuredClone(f.policy);added.modules['services/new-service']=structuredClone(added.modules[module]);
  assert.throws(()=>validatePolicy(added,[module,'services/new-service'],f.policy),/cannot acquire bootstrap/);
  added.modules['services/new-service'].level='strict';assert.equal(validatePolicy(added,[module,'services/new-service'],f.policy),true);
  const promoted=structuredClone(f.policy);promoted.modules[module].level='strict';assert.equal(validatePolicy(promoted,[module],f.policy),true);
}));
test('real merge-commit diff scope uses base tip and tested merge line numbers',()=>fixture(f=>{
  const base='a'.repeat(40),head='b'.repeat(40),pr='c'.repeat(40),calls=[];
  const run=(_command,args)=>{
    calls.push(args);
    const command=args[4],tail=args.slice(5);
    if(command==='rev-parse')return {status:0,stdout:tail[1].startsWith('origin/main')?base:tail[1].startsWith('HEAD')?head:tail[1].startsWith(pr)?pr:head};
    if(command==='merge-base')return {status:0,stdout:tail.includes('--is-ancestor')?'':base};
    if(command==='diff')return {status:0,stdout:tail.includes('--name-only')?name+'\0':'@@ -4 +4 @@\n-return 0;\n+return 1;\n'};
    if(command==='ls-tree')return {status:0,stdout:tail.at(-1)===name?name+'\n':''};
    if(command==='show')return {status:0,stdout:tail[0].startsWith(head)?f.text:f.text.replace('return 1;','return 0;')};
    throw Error('Unexpected Git fixture command');
  };
  const context=gitContext(f.root,{base:'origin/main',head,prHead:pr,run});
  assert.deepEqual(context.changes[name].lines,[4]);assert.equal(context.changes[name].semantic,true);
  assert.ok(calls.filter(a=>a.includes('diff')).every(a=>a.includes(base)&&a.includes(head)&&a.includes('--no-textconv')));
}));
test('new files, untracked files and deletions are explicit diff records without rename heuristics',()=>fixture(f=>{
  const deleted=name.replace('App.java','Deleted.java'),base='a'.repeat(40);
  const run=(_command,args)=>{
    const command=args[4],tail=args.slice(5);
    if(command==='rev-parse'||command==='merge-base')return {status:0,stdout:base};
    if(command==='diff')return {status:0,stdout:tail.includes('--name-only')?deleted+'\0':''};
    if(command==='ls-files')return {status:0,stdout:name+'\0'};
    if(command==='ls-tree')return {status:0,stdout:tail.at(-1)===deleted?deleted+'\n':''};
    if(command==='show')return {status:0,stdout:f.text};
    throw Error('Unexpected Git fixture command');
  };
  const context=gitContext(f.root,{run});
  assert.equal(context.changes[deleted].deleted,true);
  assert.equal(context.changes[name].deleted,false);
  assert.equal(context.changes[name].lines.length,f.text.split('\n').length);
}));
test('initial policy cannot pass by arbitrary manifest mutation without owner review',async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'ltz-policy-'));
  try{
    fs.mkdirSync(path.join(root,'backend/config'),{recursive:true});
    fs.writeFileSync(path.join(root,'backend/pom.xml'),'<module>services/auth-service</module>');
    const policy={version:1,minimumPercent:80,modules:{[module]:{level:'bootstrap',baseline:{covered:1,missed:2,tests:1},sources:{'p/App.java':'a'.repeat(64)}}}};
    fs.writeFileSync(path.join(root,'backend/config/coverage-policy.json'),JSON.stringify(policy));
    await assert.rejects(prepareCoverage(root,{context:{previous:null,changes:{}},env:{}}),/POLICY_REVIEW_REQUIRED/);
  }finally{assert.equal(path.dirname(root),os.tmpdir());fs.rmSync(root,{recursive:true,force:true});}
});
test('a hand-written approved context is not a substitute for verified Git facts',()=>fixture(f=>{
  f.write('backend/config/coverage-policy.json',JSON.stringify(f.policy));
  const fake=f.write('.cache/coverage-context.json',JSON.stringify({...f.context,approved:true,started:Date.now(),policyHash:hash(JSON.stringify(f.policy))}));
  assert.throws(()=>enforceCoverage(f.root,[module],fake),/Git/);
}));
test('owner approval must be independent, current-head, latest and not dismissed; API errors fail closed',async()=>{
  const head='a'.repeat(40),request={repository:'owner/repo',owner:'owner',author:'developer',pullNumber:1,head};
  const fetcher=rows=>async()=>({ok:true,json:async()=>rows});
  const row={user:{login:'owner'},commit_id:head,state:'APPROVED'};
  assert.equal(await ownerApprovedPolicy({...request,fetcher:fetcher([row])}),true);
  for(const rows of [[{...row,commit_id:'b'.repeat(40)}],[row,{...row,state:'DISMISSED'}],[row,{...row,state:'CHANGES_REQUESTED'}]])assert.equal(await ownerApprovedPolicy({...request,fetcher:fetcher(rows)}),false);
  assert.equal(await ownerApprovedPolicy({...request,author:'owner',fetcher:fetcher([row])}),false);
  assert.equal(await ownerApprovedPolicy({...request,fetcher:async()=>({ok:false})}),false);
});
test('missing, malformed, negative and entity-bearing XML FAIL through real JDK parser',()=>fixture(f=>{
  const base='backend/'+module+'/target/';
  assert.throws(()=>xmlMetadata(f.root,module,0));
  f.write(base+'surefire-reports/TEST-app.xml','<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="x"/></testsuite>');
  for(const xml of ['<report>', '<!DOCTYPE report [<!ENTITY x SYSTEM "file:///not-readable">]><report/>', '<report><counter type="LINE" covered="-1" missed="1"/></report>']){
    f.write(base+'site/jacoco/jacoco.xml',xml);assert.throws(()=>xmlMetadata(f.root,module,0),/parser/);
  }
}));
test('coverage policy and Auth application changes select backend; README-only does not',()=>{
  const available=[module,'services/api-gateway'];
  assert.equal(selectScope(['README.md'],available).backend,false);
  assert.deepEqual(selectScope(['backend/config/coverage-policy.json'],available).modules,available);
  assert.equal(selectScope([name],available).backend,true);
});
