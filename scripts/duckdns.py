"""Keep one DuckDNS A record current; the token never appears in logs."""
import os
import pathlib
import time
import urllib.parse
import urllib.request


def update(subdomain, token):
    query = urllib.parse.urlencode({'domains': subdomain, 'token': token, 'ip': ''})
    with urllib.request.urlopen('https://www.duckdns.org/update?' + query, timeout=20) as reply:
        if reply.read(128).decode().strip() != 'OK':
            raise RuntimeError('DuckDNS refused the update')


if __name__ == '__main__':
    while True:
        try:
            update(os.environ['DUCKDNS_SUBDOMAIN'], pathlib.Path('/run/secrets/duckdns_token').read_text().strip())
            print('DuckDNS record updated.', flush=True)
        except Exception:
            print('DuckDNS update failed; retry in 5 minutes.', flush=True)
        time.sleep(300)
