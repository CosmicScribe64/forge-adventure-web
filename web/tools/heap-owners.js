// Owner breakdown of a V8 .heapsnapshot (from the webtest "snapshot" step): dominator tree with chosen classes cut,
// so shared data is attributed to its class. Usage: node --max-old-space-size=9000 heap-owners.js FILE [minMB] [depth] [cutClass,...]
// Example cut list: f_StaticData,fc_CardDb,fm_FModel,f_CardStorageReader. Takes about 2 minutes for a 760 MB snapshot.
const fs=require('fs');
const buf=fs.readFileSync(process.argv[2]); const MIN=+(process.argv[3]||3)*1048576, MAXD=+(process.argv[4]||8);
const head=buf.slice(0,3000).toString();
const NC=+/"node_count":(\d+)/.exec(head)[1], EC=+/"edge_count":(\d+)/.exec(head)[1];
function parseArr(marker,count){let p=buf.indexOf(marker)+marker.length;const a=new Uint32Array(count);let i=0,v=0,inn=false;for(;;p++){const c=buf[p];if(c>=48&&c<=57){v=v*10+c-48;inn=true;}else{if(inn){a[i++]=v;v=0;inn=false;}if(c===93)break;}}return a;}
const nodes=parseArr('"nodes":[',NC*6), edges=parseArr('"edges":[',EC*3);
const sp=buf.indexOf('"strings":[');
const strings=JSON.parse(buf.slice(sp+10, buf.lastIndexOf(']')+1).toString());
const TYPES=["hidden","array","string","object","code","closure","regexp","number","native","synthetic","concatenated string","sliced string","symbol","bigint","object shape"];
const ETYPES=["context","element","property","internal","hidden","shortcut","weak"];
const nt=i=>nodes[i*6], nn=i=>strings[nodes[i*6+1]], ns=i=>nodes[i*6+3], ne=i=>nodes[i*6+4];
const first=new Uint32Array(NC+1); for(let i=0;i<NC;i++) first[i+1]=first[i]+ne(i);
// find ctx: biggest node named system / Context among root's descendants
let ctx=-1,best=0; for(let i=0;i<NC;i++) if(nn(i)==='system / Context'&&ne(i)>best){best=ne(i);ctx=i;}
console.log('ctx',ctx,'edges',best);
// build adjacency; cut nodes' edges are re-sourced from the root
const CUTN=new Set((process.argv[5]||'').split(',').filter(Boolean));
const cut=new Uint8Array(NC); for(let i=0;i<NC;i++) if(i===ctx||CUTN.has(nn(i))) cut[i]=1;
const deg=new Uint32Array(NC+2);
for(let i=0;i<NC;i++) for(let e=first[i];e<first[i+1];e++) if(edges[e*3]!==6) deg[(cut[i]?0:i)+1]++;
for(let i=0;i<=NC;i++) deg[i+1]+=deg[i];
const adj=new Uint32Array(deg[NC]); const fp=deg.slice();
for(let i=0;i<NC;i++) for(let e=first[i];e<first[i+1];e++) if(edges[e*3]!==6) adj[fp[cut[i]?0:i]++]=edges[e*3+2]/6;
const post=new Int32Array(NC), order=new Int32Array(NC).fill(-1); let pc=0;
const stack=new Int32Array(NC), eptr=new Uint32Array(NC); const seen=new Uint8Array(NC);
let s2=0; stack[s2++]=0; seen[0]=1; eptr[0]=deg[0];
while(s2){const v=stack[s2-1]; if(eptr[v]<deg[v+1]){const w=adj[eptr[v]++]; if(!seen[w]){seen[w]=1;eptr[w]=deg[w];stack[s2++]=w;}} else {s2--; order[v]=pc; post[pc++]=v;}}
const pcnt=new Uint32Array(NC+1);
for(let i=0;i<NC;i++){ if(!seen[i])continue; for(let k=deg[i];k<deg[i+1];k++) pcnt[adj[k]+1]++; }
for(let i=0;i<NC;i++) pcnt[i+1]+=pcnt[i];
const pred=new Uint32Array(pcnt[NC]); const fill=pcnt.slice();
for(let i=0;i<NC;i++){ if(!seen[i])continue; for(let k=deg[i];k<deg[i+1];k++) pred[fill[adj[k]]++]=i; }
const idom=new Int32Array(NC).fill(-1); idom[0]=0;
function intersect(a,b){while(a!==b){while(order[a]<order[b])a=idom[a];while(order[b]<order[a])b=idom[b];}return a;}
let changed=true,it=0;
while(changed){changed=false;it++;
 for(let k=pc-2;k>=0;k--){const v=post[k]; let nd=-1; for(let q=pcnt[v];q<pcnt[v+1];q++){const p=pred[q]; if(idom[p]===-1)continue; nd=(nd===-1)?p:intersect(p,nd);} if(nd!==idom[v]){idom[v]=nd;changed=true;}}}
const ret=new Float64Array(NC); for(let i=0;i<NC;i++) if(seen[i]) ret[i]=ns(i);
for(let k=0;k<pc-1;k++){const v=post[k]; ret[idom[v]]+=ret[v];}
console.log('reachable',pc,'iters',it,'total MB',(ret[0]/1048576).toFixed(1));
const ctxNames=new Map(); for(let e=first[ctx];e<first[ctx+1];e++) ctxNames.set(edges[e*3+2]/6,strings[edges[e*3+1]]);
const kids=new Map(); for(let v=1;v<NC;v++){ if(!seen[v])continue; if(ret[v]>=MIN){ const p=idom[v]; (kids.get(p)||kids.set(p,[]).get(p)).push(v);} }
function edgeName(p,c){ if(p===0&&ctxNames.has(c)) return 'static '+ctxNames.get(c); for(let e=first[p];e<first[p+1];e++){if(edges[e*3+2]/6===c&&edges[e*3]!==6){const et=ETYPES[edges[e*3]];return (et==='element'||et==='hidden')?'['+edges[e*3+1]+']':strings[edges[e*3+1]];}}return '?';}
function label(i,p){return TYPES[nt(i)][0]+':'+nn(i).slice(0,40).replace(/\n/g,' ')+'  <-'+edgeName(p,i);}
function pr(v,d){ const ks=(kids.get(v)||[]).sort((a,b)=>ret[b]-ret[a]); for(const k of ks.slice(0,d<1?40:10)){ console.log(' '.repeat(d*2)+(ret[k]/1048576).toFixed(1)+' MB '+label(k,v)); if(d<MAXD) pr(k,d+1);} }
pr(0,0);
// shared remainder
let sum0=0; for(const k of (kids.get(0)||[])) sum0+=ret[k]; console.log('top-level >=MIN sum',(sum0/1048576).toFixed(1),'of',(ret[0]/1048576).toFixed(1));
// self size by type name for objects: group Java classes
const by={}; for(let i=0;i<NC;i++){ if(!seen[i])continue; const t=nt(i); const k=TYPES[t][0]+':'+nn(i).slice(0,45); const o=by[k]||(by[k]=[0,0]); o[0]+=ns(i);o[1]++;}
console.log('TOP SELF BY CLASS'); for(const [k,v] of Object.entries(by).sort((a,b)=>b[1][0]-a[1][0]).slice(0,25)) console.log((v[0]/1048576).toFixed(1),'MB',v[1],'x',k);

const top=new Int32Array(NC); for(let k=pc-2;k>=0;k--){const v=post[k]; top[v]=idom[v]===0?v:top[idom[v]];}
const own={}; for(let v=1;v<NC;v++){ if(!seen[v]||idom[v]!==0)continue; const c=nn(v).slice(0,45); const k=TYPES[nt(v)][0]+':'+c; const o=own[k]||(own[k]=[0,0]); o[0]+=ret[v];o[1]++;}
console.log('PARTITION BY CLASS OF TOP-LEVEL OWNER (retained sum, count)'); for(const [k,v] of Object.entries(own).sort((a,b)=>b[1][0]-a[1][0]).slice(0,40)) console.log((v[0]/1048576).toFixed(1),'MB',v[1],'x',k);
// drill into CardFace / CardRules / CardEdition / PaperCard: retained by field of directly dominated children
for(const cls of ['fc_CardFace','fc_CardRules','fc_CardEdition','fi_PaperCard']){
 const f={}; let cnt=0;
 for(let v=1;v<NC;v++){ if(!seen[v])continue; const p=idom[v]; if(p===0||nn(p)!==cls)continue; cnt++; const k=edgeName(p,v).replace(/\d+$/,'')+' ('+TYPES[nt(v)][0]+':'+nn(v).slice(0,25)+')'; const o=f[k]||(f[k]=[0,0]); o[0]+=ret[v];o[1]++;}
 console.log('DRILL',cls); for(const [k,v] of Object.entries(f).sort((a,b)=>b[1][0]-a[1][0]).slice(0,10)) console.log('  ',(v[0]/1048576).toFixed(1),'MB',v[1],'x',k);
}
// duplicate JS strings
const dup=new Map(); let strTotal=0,strCount=0;
for(let i=0;i<NC;i++){ const t=nt(i); if(t!==2&&t!==10) continue; strTotal+=ns(i); strCount++; const k=nn(i); const o=dup.get(k); if(o){o[0]++;} else dup.set(k,[1,ns(i)]); }
let waste=0,wc=0; const wl=[]; for(const [k,[c,sz]] of dup){ if(c>1){waste+=(c-1)*sz; wc+=c-1; wl.push([(c-1)*sz,c,k.slice(0,60)]);} }
console.log('strings',strCount,(strTotal/1048576).toFixed(1),'MB; duplicate waste',(waste/1048576).toFixed(1),'MB in',wc,'copies');
wl.sort((a,b)=>b[0]-a[0]); for(const w of wl.slice(0,15)) console.log('  ',(w[0]/1024).toFixed(0),'KB x',w[1],JSON.stringify(w[2]));
