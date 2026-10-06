"""Reads the output of a webtest run with web/tools/bigint-hook.js (the "js: {...}" lines holding JSON.stringify(__stk), the
first for the title screen and the second for the overworld) and prints, per run, which Java source lines the sampled BigInt
operations came from: innermost frame, inclusive, by file, and the callers of the libGDX maps.
usage: python3 web/tools/bigint-decode.py <webtest output file> <app.js.map>
"""
import json, sys, re, bisect, collections
out, mapf = sys.argv[1], sys.argv[2]
sm = json.load(open(mapf))
B64 = {c: i for i, c in enumerate("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")}
def vlq(s):
    o=[];sh=0;v=0
    for ch in s:
        d=B64[ch]; v+=(d&31)<<sh
        if d&32: sh+=5
        else: o.append(-(v>>1) if v&1 else v>>1); sh=0; v=0
    return o
lines=[];src=nm=sl=sc=0
for line in sm["mappings"].split(";"):
    gc=0;segs=[]
    for seg in line.split(","):
        if not seg: continue
        v=vlq(seg); gc+=v[0]
        if len(v)>=4:
            src+=v[1]; sl+=v[2]; sc+=v[3]
            if len(v)>=5: nm+=v[4]
            segs.append((gc,src,sl,sc))
    lines.append(segs)
def look(l,c):
    l-=1
    if l<0 or l>=len(lines) or not lines[l]: return "?"
    segs=lines[l]; i=bisect.bisect_right([s[0] for s in segs],c-1)-1
    if i<0: return "?"
    s=segs[i]; return sm["sources"][s[1]].split("/")[-1]+":"+str(s[2]+1)
txt=[l for l in open(out,errors="replace") if l.startswith('js: {"blob')]
for which,l in zip(("title","world"),txt):
    st=json.loads(l[4:]); tot=sum(st.values())
    self_c=collections.Counter(); incl=collections.Counter(); pair=collections.Counter()
    for k,v in st.items():
        fr=[]
        for f in k.split("|"):
            m=re.search(r":(\d+):(\d+)$",f)
            fr.append(look(int(m.group(1)),int(m.group(2))) if m else f)
        fr=[x for x in fr if x!='?'] or ['?']
        # skip frames from BigInt helper lines of lib classes? keep first non-"?" frame
        self_c[fr[0]]+=v
        for x in set(fr): incl[x]+=v
        pair[" < ".join(fr[:3])]+=v
    print("=====",which,"samples",tot)
    print("-- self (innermost frame)")
    for k,v in self_c.most_common(14): print(f"{100*v/tot:5.1f}%  {k}")
    print("-- inclusive")
    for k,v in incl.most_common(30): print(f"{100*v/tot:5.1f}%  {k}")
    print("-- top 3-frame chains")
    for k,v in pair.most_common(8): print(f"{100*v/tot:5.1f}%  {k}")
    byfile=collections.Counter()
    for k,v in self_c.items(): byfile[k.split(':')[0]]+=v
    print("-- self by file")
    for k,v in byfile.most_common(14): print(f"{100*v/tot:5.1f}%  {k}")
    # caller of maps: first frame outside maps
    cm=collections.Counter()
    for k,v in st.items():
        fr=[]
        for f in k.split("|"):
            m=re.search(r":(\d+):(\d+)$",f)
            fr.append(look(int(m.group(1)),int(m.group(2))) if m else f)
        fr=[x for x in fr if x!='?']
        if fr and re.match(r"(Int|IntFloat|ObjectInt|Object)Map|IntSet",fr[0]):
            o=[x for x in fr if not re.match(r"(Int|IntFloat|ObjectInt|Object|IntInt)(Map|Set)\.",x)]
            cm[(fr[0].split(':')[0],o[0] if o else '?')]+=v
    print("-- map callers (first frame outside maps; only 7 frames deep)")
    for k,v in cm.most_common(12): print(f"{100*v/tot:5.1f}%  {k}")
