"""Offline configuration check: no secret values, network, model or microphone."""
import json
import os

from internet_search import configuration, InternetSearchError
from web_search_env import configure


def main():
    status = 'ready'
    if not configure(os.environ): status = 'configuration_invalid'
    else:
        try: configuration(os.environ)
        except InternetSearchError as error: status = error.code
    print(json.dumps({'web_search_configuration': status, 'network_requests': 0}))
    return 0 if status == 'ready' else 1


if __name__ == '__main__': raise SystemExit(main())
