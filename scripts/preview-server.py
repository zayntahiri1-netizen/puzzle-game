#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
خادم المعاينة المحلي — يحاكي سلوك Firebase Hosting حرفياً.

خادم بايثون العادي يخدم الملفات بامتدادها فقط، فلا يعمل /terms محلياً
بينما يعمل على الموقع المنشور. هذا الخادم يطابق الاثنين:

  /terms        →  يخدم terms.html            (cleanUrls)
  /terms.html   →  تحويل 301 إلى /terms       (بلا امتداد أبداً)
  /             →  index.html
  مسار مجهول    →  404.html برمز 404

الاستعمال:  python3 scripts/preview-server.py <المنفذ> <مجلّد www>
"""
import os, sys, posixpath
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, urlunsplit, unquote


class CleanURLHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self._route():
            return
        super().do_GET()

    def do_HEAD(self):
        if self._route():
            return
        super().do_HEAD()

    def _route(self):
        parts = urlsplit(self.path)
        path = unquote(parts.path)

        # ① /x.html → تحويل دائم إلى /x  (كي لا يظهر الامتداد في شريط العنوان)
        if path.endswith('.html') and path != '/index.html':
            clean = path[:-5]
            self.send_response(301)
            self.send_header('Location', urlunsplit(('', '', clean, parts.query, parts.fragment)))
            self.end_headers()
            return True
        if path == '/index.html':
            self.send_response(301)
            self.send_header('Location', urlunsplit(('', '', '/', parts.query, parts.fragment)))
            self.end_headers()
            return True

        # ② /x → اخدم x.html إن وُجد
        if path != '/' and not path.endswith('/') and not os.path.splitext(path)[1]:
            candidate = self.translate_path(path + '.html')
            if os.path.isfile(candidate):
                self.path = urlunsplit(('', '', path + '.html', parts.query, parts.fragment))
            return False

        return False

    def send_error(self, code, message=None, explain=None):
        # ③ 404 مخصّصة — نفس ما يعرضه Firebase
        if code == 404:
            page = os.path.join(self.directory, '404.html')
            if os.path.isfile(page):
                body = open(page, 'rb').read()
                self.send_response(404)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                if self.command != 'HEAD':
                    self.wfile.write(body)
                return
        super().send_error(code, message, explain)

    def end_headers(self):
        # لا تخزين مؤقّت أثناء المعاينة — كل تحديث يظهر فوراً
        self.send_header('Cache-Control', 'no-store, must-revalidate')
        super().end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write("  %s\n" % (fmt % args))


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    root = sys.argv[2] if len(sys.argv) > 2 else 'www'
    handler = lambda *a, **kw: CleanURLHandler(*a, directory=root, **kw)
    srv = ThreadingHTTPServer(('127.0.0.1', port), handler)
    srv.serve_forever()


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        pass
