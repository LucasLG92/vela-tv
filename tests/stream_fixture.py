"""Owned-loopback playback fixture. Supply synthetic ffmpeg HLS segments; no real IPTV account is used."""
from argparse import ArgumentParser
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import base64, json, threading, time, urllib.parse

parser=ArgumentParser()
parser.add_argument('--media-dir',type=Path,required=True)
parser.add_argument('--port-file',type=Path,required=True)
args=parser.parse_args()
segments=sorted(args.media_dir.glob('segment*.ts'))
assert len(segments)>=8, 'Generate at least eight owned synthetic 2-second MPEG-TS segments'
started=time.monotonic();lock=threading.Lock();hits=Counter();denied=Counter()
png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jK1kAAAAASUVORK5CYII=')

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def body(self,data,kind='application/json',code=200):
        self.send_response(code);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(data)));self.end_headers()
        try:self.wfile.write(data)
        except (BrokenPipeError,ConnectionResetError):pass
    def do_GET(self):
        url=urllib.parse.urlsplit(self.path);path=url.path
        with lock:hits[path]+=1;number=hits[path]
        if path=='/metrics':
            with lock:payload={'hits':dict(hits),'denied':dict(denied)}
            self.body(json.dumps(payload).encode());return
        if path=='/logo.png':self.body(png,'image/png');return
        if path.startswith(('/protected/','/protected2/')):
            agent='Vela-Stream-Fixture-2' if path.startswith('/protected2/') else 'Vela-Stream-Fixture'
            if self.headers.get('User-Agent')!=agent or self.headers.get('Referer')!='https://vela-fixture.invalid/watch':
                with lock:denied[path]+=1
                self.body(b'',code=403);return
        if path=='/player_api.php':
            query=urllib.parse.parse_qs(url.query);action=query.get('action',[''])[0]
            if not action:value={'user_info':{'auth':1,'status':'Active','allowed_output_formats':['m3u8','ts']},'server_info':{'timezone':'UTC'}}
            elif action=='get_live_categories':value=[{'category_id':'1','category_name':'Fixture'}]
            elif action=='get_live_streams':value=[{'stream_id':101,'name':'Fixture FHD','category_id':'1','stream_type':'live','stream_icon':''}]
            else:value={}
            self.body(json.dumps(value).encode());return
        if path.endswith('/401.m3u8') or path.endswith('/401.ts') or path=='/denied.m3u8':self.body(b'',code=403);return
        if path.endswith('/101.m3u8') or path in ('/missing.m3u8','/broken.ts'):self.body(b'',code=404);return
        if path=='/transient.m3u8' and number==1:self.body(b'',code=503);return
        if path.endswith('/master.m3u8'):
            self.body(b'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=640x360\nlive.m3u8\n','application/vnd.apple.mpegurl');return
        if path=='/radio.aac':
            self.body((args.media_dir/'radio.aac').read_bytes(),'audio/aac');return
        if path=='/timeshift/demo.ts':
            self.body(b''.join(p.read_bytes() for p in segments[:5]),'video/mp2t');return
        if path=='/short.m3u8':
            self.body(b'#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nsegment000.ts\n#EXTINF:2,\nsegment001.ts\n#EXT-X-ENDLIST\n','application/vnd.apple.mpegurl');return
        if path.endswith('.m3u8'):
            end=8+int((time.monotonic()-started)/2);begin=end-6
            text='#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXT-X-MEDIA-SEQUENCE:'+str(begin)+'\n'
            prefix=path.rsplit('/',1)[0]
            for i in range(begin,end):
                if i%len(segments)==0:text+='#EXT-X-DISCONTINUITY\n'
                text+='#EXTINF:2,\n'+prefix+'/'+segments[i%len(segments)].name+'\n'
            self.body(text.encode(),'application/vnd.apple.mpegurl');return
        candidate=args.media_dir/path.rsplit('/',1)[-1]
        if candidate in segments:self.body(candidate.read_bytes(),'video/mp2t');return
        if path.endswith('.ts'):
            self.send_response(200);self.send_header('Content-Type','video/mp2t');self.end_headers();self.connection.settimeout(5)
            try:
                until=time.monotonic()+90;i=0
                while time.monotonic()<until:
                    data=segments[i%len(segments)].read_bytes();i+=1
                    for offset in range(0,len(data),16384):self.wfile.write(data[offset:offset+16384]);self.wfile.flush();time.sleep(.02)
            except (BrokenPipeError,ConnectionResetError,TimeoutError):pass
            return
        self.body(b'',code=404)

server=ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True
args.port_file.write_text(str(server.server_port),encoding='utf-8')
server.serve_forever()
