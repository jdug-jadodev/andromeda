"""Explicit-query-only search snippets. Fixed HTTPS origin; no AI service or page fetches."""
from html.parser import HTMLParser
import http.client
import ipaddress
import re
import socket
import threading
import time
import html
import xml.etree.ElementTree as ET
from urllib.parse import parse_qs, urlencode, urlsplit

from agenda_requests import clean
from doja_intents import normalized


def bounded(text, units):
    return text.encode('utf-16-le')[:units * 2].decode('utf-16-le', errors='ignore')


def rss_results(raw):
    if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper(): raise ValueError('XML declarations forbidden')
    try: root = ET.fromstring(raw)
    except ET.ParseError: raise ValueError('Invalid search RSS') from None
    if root.tag != 'rss': raise ValueError('Not a search RSS response')
    rows = []
    for item in root.findall('./channel/item'):
        try: url = source_url(item.findtext('link', ''))
        except ValueError: url = None
        title = bounded(' '.join(html.unescape(item.findtext('title', '')).split()), 120)
        excerpt = bounded(' '.join(html.unescape(re.sub('<[^>]*>', '', item.findtext('description', ''))).split()), 350)
        if url and title and excerpt: rows.append({'url': url, 'title': title, 'excerpt': excerpt})
        if len(rows) == 3: break
    return rows


def authorized_query(human, proposed):
    raw = clean(human)
    match = re.fullmatch(r'(?:busca|investiga|consulta) (?:en (?:internet|la web)|por internet) (.+)', raw, re.I)
    if (not match or not isinstance(proposed, str) or not 0 < len(match[1]) <= 160
            or normalized(match[1]) != normalized(proposed)
            or re.search(r'\b(?:y|ademas|tambien) (?:pon|crea|programa|dime|cuenta|cancela|no)\b', normalized(match[1]))):
        raise ValueError('Explicit standalone current web query required')
    return match[1]  # Only the necessary query, never history, audio or model additions.


def source_url(url):
    parts = urlsplit(url)
    if parts.hostname in {'duckduckgo.com', 'html.duckduckgo.com'}:
        values = parse_qs(parts.query).get('uddg', [])
        if len(values) == 1: parts = urlsplit(values[0]); url = values[0]
    host = parts.hostname
    if (parts.scheme != 'https' or not host or parts.username or parts.password or parts.port not in {None, 443}
            or len(url) > 1000 or re.search(r'[\s\x00-\x1f]', url) or '.' not in host
            or host.endswith(('.local', '.localhost', '.internal'))): return None
    try: ipaddress.ip_address(host); return None
    except ValueError: pass
    if not re.fullmatch(r'[a-zA-Z0-9.-]+', host): return None
    return url


class SearchResults(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.rows, self.row, self.capture, self.depth, self.buffer = [], None, '', 0, []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs); classes = attrs.get('class', '').split()
        if self.capture:
            if tag not in {'br', 'img', 'hr', 'input', 'meta', 'link', 'wbr'}: self.depth += 1
            return
        if 'result__a' in classes and tag == 'a':
            if len(self.rows) >= 3: return
            try: url = source_url(attrs.get('href', ''))
            except ValueError: url = None
            self.row = {'url': url, 'title': '', 'excerpt': ''} if url else None
            if self.row: self.rows.append(self.row); self.capture, self.depth, self.buffer = 'title', 1, []
        elif 'result__snippet' in classes and self.row:
            self.capture, self.depth, self.buffer = 'excerpt', 1, []

    def handle_endtag(self, tag):
        if self.capture:
            self.depth -= 1
            if self.depth <= 0:
                self.row[self.capture] = ' '.join(''.join(self.buffer).split())[:350 if self.capture == 'excerpt' else 120]
                self.capture, self.buffer = '', []

    def handle_data(self, text):
        if self.capture and sum(map(len, self.buffer)) < 2000: self.buffer.append(text[:1000])


def search(query, deadline, cancelled=None):
    if not isinstance(query, str) or not 0 < len(query) <= 160: raise ValueError('Invalid search query')
    end = min(time.monotonic() + 4, deadline)
    if end - time.monotonic() < .25: raise TimeoutError('Search budget exhausted')
    connection = http.client.HTTPSConnection('www.bing.com', timeout=max(.1, end - time.monotonic()))
    def abort():
        try:
            if connection.sock: connection.sock.shutdown(socket.SHUT_RDWR)
        except OSError: pass
        connection.close()
    watchdog = threading.Timer(max(.01, end - time.monotonic()), abort); watchdog.daemon = True; watchdog.start()
    try:
        connection.request('GET', '/search?' + urlencode({'q': query, 'format': 'rss', 'setlang': 'es'}), headers={
            'User-Agent': 'AndromedaLocalPilot/0.11 (read-only personal assistant)', 'Accept': 'application/rss+xml, application/xml, text/xml', 'Accept-Encoding': 'identity'})
        response = connection.getresponse()
        if response.status != 200 or not any(value in response.getheader('Content-Type', '') for value in ('xml', 'rss')):
            raise ValueError('Search unavailable')  # No redirects, CAPTCHA solving or provider substitution.
        raw = bytearray()
        while True:
            if time.monotonic() >= end or cancelled is not None and cancelled.is_set(): raise TimeoutError('Search cancelled')
            chunk = response.read1(min(8192, 131073 - len(raw)))
            if not chunk: break
            raw.extend(chunk)
            if len(raw) > 131072: raise ValueError('Search response too large')
        return rss_results(bytes(raw))
    except http.client.HTTPException:
        raise OSError('Search protocol failed') from None
    finally:
        watchdog.cancel(); abort()


def speech(rows):
    if not rows: return 'No obtuve resultados web utilizables. No tengo una respuesta verificada con esa búsqueda.'
    first = rows[0]; host = urlsplit(first['url']).hostname
    excerpt = bounded(first['excerpt'], 300)
    return f'Un extracto de {host} dice: {excerpt} Es un resultado de búsqueda, no una verificación completa de la página.'
