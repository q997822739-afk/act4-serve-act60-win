# Run against a built portable GM folder: python smoke.py <path-to-A4GmTool.exe>
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlencode


PASSWORD = 'example ! &+=% 管理员'
CSRF_HEADER = 'X-A4-GM-CSRF'
JSON_HEADERS = {'Content-Type': 'application/json'}
FORM_HEADERS = {'Content-Type': 'application/x-www-form-urlencoded'}


class Host:
    def __init__(self, executable, password=None, flags=()):
        self.executable = executable
        self.flags = flags
        self.env = os.environ.copy()
        self.env.pop('A4_GM_PASSWORD', None)
        if password is not None:
            self.env['A4_GM_PASSWORD'] = password

    def __enter__(self):
        args = ['--port', '0', '--db', 'missing-smoke.db', '--pvf', 'missing-smoke.pvf']
        self.proc = subprocess.Popen(
            [self.executable, *args, *self.flags], env=self.env, stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        try:
            line = self.proc.stdout.readline().strip()
            match = re.fullmatch(rb'http://127\.0\.0\.1:(\d+)/', line)
            assert match, f'Expected a loopback listen URL, got {line!r}'
            self.port = int(match[1])
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def __exit__(self, *_):
        if self.proc.stdin and not self.proc.stdin.closed:
            self.proc.stdin.close()
        if self.proc.poll() is None:
            self.proc.kill()
        self.proc.wait(timeout=5)
        self.proc.stdout.close()
        self.proc.stderr.close()

    def request(self, path, expected, method='GET', headers=None, body=None):
        conn = http.client.HTTPConnection('127.0.0.1', self.port, timeout=5)
        try:
            conn.request(method, path, body=body, headers=headers or {})
            resp = conn.getresponse()
            content = resp.read().decode('utf-8', errors='replace')
            assert resp.status == expected, (method, path, expected, resp.status, content[:150])
            assert resp.getheader('Cache-Control') == 'no-store'
            assert resp.getheader('Access-Control-Allow-Origin') is None
            return resp, content
        finally:
            conn.close()


def get_form(host, *, secure, headers=None):
    resp, page = host.request('/login', 200, headers=headers)
    assert 'type="password"' in page and 'name="username"' not in page
    assert 'frame-ancestors' in resp.getheader('Content-Security-Policy')
    token = re.search(r'name="csrf" value="([a-zA-Z0-9._-]+)"', page)[1]
    cookie = resp.getheader('Set-Cookie')
    assert ('; Secure' in cookie) == secure
    assert 'HttpOnly' in cookie and 'SameSite=Strict' in cookie
    return token, cookie.split(';')[0]


def login(host, *, secure, token, cookie):
    headers = dict(FORM_HEADERS, Cookie=cookie)
    resp, _ = host.request('/login', 303, 'POST', headers, urlencode({'csrf': token, 'password': PASSWORD}))
    assert resp.getheader('Location') == '/'
    cookies = [v for k, v in resp.getheaders() if k.lower() == 'set-cookie']
    assert len(cookies) == 2
    session, clear_login = cookies
    assert session.startswith(('__Host-' if secure else '') + 'a4gm_session=')
    assert ('; Secure' in session) == secure
    assert 'HttpOnly' in session and 'SameSite=Strict' in session and 'Path=/' in session and 'Domain=' not in session
    assert 'Max-Age=0' in clear_login
    return {'Cookie': session.split(';')[0]}


def main(executable):
    for flag in ('--require-password', '--stop-on-stdin-eof', '--secure-cookie'):
        for password in (None, '  '):
            host = Host(executable, password, (flag,))
            proc = subprocess.run([executable, flag], env=host.env, capture_output=True, timeout=5)
            assert proc.returncode == 1 and b'A4_GM_PASSWORD' in proc.stderr and not proc.stdout

    with Host(executable) as host:
        host.request('/', 200)
        host.request('/app.js', 200)
        host.request('/api/status', 200)
        host.request('/login', 404)
        host.request('/', 403, headers={'Host': 'public.invalid'})
        host.request('/api/csrf', 403, headers={'Host': 'public.invalid'})
        _, raw = host.request('/api/csrf', 200)
        data = json.loads(raw)
        assert not data['authenticated']
        host.request('/api/shut-to-select?on=true', 405)
        host.request('/api/shut-to-select', 403, 'POST', JSON_HEADERS, '{"on":true}')
        host.request('/api/shut-to-select', 200, 'POST', dict(JSON_HEADERS, **{CSRF_HEADER: data['csrfToken']}), '{"on":true}')

    with Host(executable, PASSWORD, ('--require-password', '--stop-on-stdin-eof')) as host:
        for path in ('/', '/index.html', '/app.js', '/style.css', '/favicon.ico', '/icon.png', '/other'):
            resp, _ = host.request(path, 303)
            assert resp.getheader('Location') == '/login'
        for path in ('/api', '/api/status', '/api/csrf', '/api/icon/?id=1', '/api/unknown'):
            host.request(path, 401)
        host.request('/api/shut-to-select', 401, 'POST', JSON_HEADERS, '{"on":true}')
        host.request('/logout', 401, 'POST')
        token, cookie = get_form(host, secure=False, headers={'X-Forwarded-Proto': 'https', 'Forwarded': 'proto=https'})
        host.request('/login', 403, 'POST', FORM_HEADERS, urlencode({'csrf': token, 'password': PASSWORD}))
        host.request('/login', 403, 'POST', dict(FORM_HEADERS, Cookie=cookie), urlencode({'csrf': 'wrong', 'password': PASSWORD}))
        with Host(executable, PASSWORD) as failed:
            bad_token, bad_cookie = get_form(failed, secure=False)
            resp, _ = failed.request('/login', 401, 'POST', dict(FORM_HEADERS, Cookie=bad_cookie), urlencode({'csrf': bad_token, 'password': 'wrong'}))
            assert 'a4gm_session' not in resp.getheader('Set-Cookie')
            bad_token = re.search(r'name="csrf" value="([a-zA-Z0-9._-]+)"', _)[1]
            bad_cookie = resp.getheader('Set-Cookie').split(';')[0]
            resp, _ = failed.request('/login', 429, 'POST', dict(FORM_HEADERS, Cookie=bad_cookie), urlencode({'csrf': bad_token, 'password': 'wrong'}))
            assert int(resp.getheader('Retry-After')) >= 1
        session = login(host, secure=False, token=token, cookie=cookie)
        host.request('/', 200, headers=session)
        host.request('/app.js', 200, headers=session)
        _, raw = host.request('/api/csrf', 200, headers=session)
        data = json.loads(raw)
        assert data['authenticated']
        csrf = data['csrfToken']
        post_headers = {**session, **JSON_HEADERS, CSRF_HEADER: csrf}
        host.request('/api/shut-to-select?on=true', 405, headers=session)
        host.request('/api/shut-to-select', 405, 'PUT', post_headers, '{"on":true}')
        host.request('/api/shut-to-select', 403, 'POST', {**session, **JSON_HEADERS}, '{"on":true}')
        host.request('/api/shut-to-select', 403, 'POST', {**post_headers, CSRF_HEADER: 'wrong'}, '{"on":true}')
        host.request('/api/shut-to-select', 415, 'POST', {**post_headers, **FORM_HEADERS}, 'on=true')
        host.request('/api/shut-to-select', 400, 'POST', post_headers, '{bad')
        _, raw = host.request('/api/status', 200, headers=session)
        assert not json.loads(raw)['shutToSelect']
        _, raw = host.request('/api/shut-to-select/', 200, 'POST', post_headers, '{"on":true}')
        assert json.loads(raw)['shutToSelect']
        host.request('/logout', 405, headers=session)
        host.request('/logout', 403, 'POST', session)
        resp, _ = host.request('/logout', 303, 'POST', post_headers)
        assert resp.getheader('Location') == '/login' and 'Max-Age=0' in resp.getheader('Set-Cookie')
        host.request('/api/status', 401, headers=session)
        host.proc.stdin.close()
        assert host.proc.wait(timeout=5) == 0

    with Host(executable, PASSWORD, ('--secure-cookie',)) as host:
        # Simulate the HTTP backend connection made by an HTTPS reverse proxy. Reuse
        # cookies manually because HTTPConnection deliberately is not a browser.
        token, cookie = get_form(host, secure=True, headers={'Host': 'public.invalid', 'X-Forwarded-Proto': 'http'})
        session = login(host, secure=True, token=token, cookie=cookie)
        host.request('/api/status', 200, headers={**session, 'Host': 'public.invalid'})
        host.request('/api/status', 401, headers={'Cookie': session['Cookie'].removeprefix('__Host-')})

    print('GM HTTP authentication smoke tests passed')


if __name__ == '__main__':
    main(str(Path(sys.argv[1]).resolve(strict=True)))
