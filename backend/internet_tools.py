"""Read-only fixed-origin Internet tool. Sends query only, not conversation.

First provider is an encyclopedia, not weather/news/general web coverage.
No arbitrary URL fetch, proxy, credentials, scripts or page instructions.
"""
import html
import json
import re
import urllib.parse
import urllib.request


class SourceRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        target = urllib.parse.urlparse(newurl)
        if target.scheme != 'https' or target.hostname != 'es.wikipedia.org' or target.port not in {None, 443}:
            raise ValueError('Unexpected source redirect')
        return super().redirect_request(request, fp, code, message, headers, newurl)


def search_encyclopedia(query):
    if not isinstance(query, str) or not 0 < len(query.strip()) <= 160:
        raise ValueError('Invalid query')
    parameters = urllib.parse.urlencode({'action': 'query', 'list': 'search', 'srsearch': query,
                                         'srlimit': 3, 'format': 'json', 'utf8': 1})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), SourceRedirects())
    request = urllib.request.Request('https://es.wikipedia.org/w/api.php?' + parameters,
                                     headers={'User-Agent': 'AndromedaLocalPilot/0.1 (read-only personal assistant)'})
    with opener.open(request, timeout=8) as response:
        if urllib.parse.urlparse(response.geturl()).hostname != 'es.wikipedia.org':
            raise ValueError('Unexpected source redirect')
        raw = response.read(65537)
    if len(raw) > 65536:
        raise ValueError('Source too large')
    data = json.loads(raw)
    results = []
    for item in data.get('query', {}).get('search', [])[:3]:
        pageid = item.get('pageid')
        if type(pageid) is not int or pageid < 1:
            continue
        results.append({'title': str(item.get('title', ''))[:120],
                        'excerpt': html.unescape(re.sub('<[^>]*>', '', str(item.get('snippet', ''))))[:350],
                        'url': 'https://es.wikipedia.org/?curid=' + str(pageid)})
    return {'source': 'Wikipedia en español', 'query': query, 'results': results,
            'limitation': 'Extractos enciclopédicos, no información meteorológica ni noticias en vivo.'}
