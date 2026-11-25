"""网关协议探针：按花枝的线协议（明文 <hz>命令|参数</hz>）逐项打接口。

    python probe_gateway.py [host] [port]

跑之前先用一个临时账号，别拿真实账号测（会改 last_play_time、写登录日志）。
"""
import socket
import sys

HOST = sys.argv[1] if len(sys.argv) > 1 else '127.0.0.1'
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8188

ACCOUNT = 'gwprobe01'
PASSWORD = 'probe123'


def call(cmd, **fields):
    body = '<c>%s</c>' % cmd
    for key, value in fields.items():
        body += '<%s>%s</%s>' % (key, value, key)
    packet = ('<hz>%s</hz>' % body).encode('utf-8')
    with socket.create_connection((HOST, PORT), timeout=5) as sock:
        sock.sendall(packet)
        data = b''
        while b'</hz>' not in data:
            chunk = sock.recv(4096)
            if not chunk:
                break
            data += chunk
    text = data.decode('utf-8', 'replace')
    return text


def check(name, text, expect):
    ok = expect in text
    print(('PASS ' if ok else 'FAIL ') + name + '  ' + text[:200])
    return ok


def main():
    results = []
    results.append(check('ping', call('ping'), '"code":0'))
    results.append(check('version', call('version'), '"port":7001'))
    results.append(check('news', call('news'), '"list"'))
    results.append(check('login 错密码', call('login', u=ACCOUNT, p='wrong'), '"code":1'))

    registered = call('reg', u=ACCOUNT, p=PASSWORD)
    if '"code":7' in registered:
        print('SKIP reg（账号已存在，用已有账号继续）')
        results.append(True)
    else:
        results.append(check('reg 注册', registered, '"code":0'))

    login = call('login', u=ACCOUNT, p=PASSWORD)
    results.append(check('login 正确', login, '"token"'))
    token = ''
    if '"token":"' in login:
        token = login.split('"token":"')[1].split('"')[0]
        print('  令牌 =', token, '长度', len(token))

    results.append(check('改成同样的账号应报已存在', call('reg', u=ACCOUNT, p=PASSWORD), '"code":7'))
    results.append(check('改密 原密码错', call('reSetPassword', u=ACCOUNT, p='bad', n='x1'), '"code":1'))
    results.append(check('改密', call('reSetPassword', u=ACCOUNT, p=PASSWORD, n='probe456'),
                         '"code":0'))
    results.append(check('新密码能登录', call('login', u=ACCOUNT, p='probe456'), '"token"'))
    results.append(check('旧密码不能登录', call('login', u=ACCOUNT, p=PASSWORD), '"code":1'))
    # 改回去，方便重复跑
    call('reSetPassword', u=ACCOUNT, p='probe456', n=PASSWORD)
    results.append(check('非法账号', call('login', u='a b', p='x'), '"code":3'))
    results.append(check('未知命令', call('nope'), '"code":9'))

    print('\n%d/%d PASS' % (sum(1 for r in results if r), len(results)))
    return 0 if all(results) else 1


if __name__ == '__main__':
    sys.exit(main())
