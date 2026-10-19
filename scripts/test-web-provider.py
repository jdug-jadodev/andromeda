"""One public fixed-query connectivity check; no model, user text, page fetch or retries."""
import json
import time
from web_search import search

try:
    rows = search('nebulosa astronomía', time.monotonic() + 4)
    print(json.dumps({'event': 'public_web_provider_control', 'provider': 'bing_classic_rss', 'results': len(rows),
        'status': 'RESULTS' if rows else 'EMPTY_OR_CHALLENGE', 'public_query_only': True, 'audio_played': False,
        'microphone_opened': False, 'llm_started': False}), flush=True)
except Exception as failure:
    print(json.dumps({'event': 'public_web_provider_control', 'provider': 'bing_classic_rss', 'status': 'UNAVAILABLE',
        'error_class': type(failure).__name__, 'public_query_only': True, 'audio_played': False,
        'microphone_opened': False, 'llm_started': False}), flush=True)
