"""Fixed backend launcher after env -i; tokens never passed to llama.cpp or argv."""
import os
from pathlib import Path
import runpy
import sys

from web_search_env import configure


def main():
    expected = Path(__file__).resolve().with_name('doja_stream.py')
    if len(sys.argv) < 2 or Path(sys.argv[1]).resolve() != expected: raise SystemExit(2)
    if not configure(os.environ):
        print('Web search configuration unavailable; other tools unchanged.', file=sys.stderr)
    sys.argv = sys.argv[1:]
    runpy.run_path(str(expected), run_name='__main__')


if __name__ == '__main__': main()
